#!/usr/bin/env python3
"""Read-only, network-free verification of the portable Pic-Pac-Poe handoff.

Run from any working directory with Python 3.9+:
  python3 docs/ios-handoff/verify-handoff.py
  python3 docs/ios-handoff/verify-handoff.py --strict-release

This program never rewrites manifests, hashes, source, screenshots, or Git state.
It checks the package on disk; it does not certify CI, signing, a remote tag,
human accessibility checks, or the truth of screenshot provenance metadata.
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit


PACKAGE = Path(__file__).resolve().parent
REPOSITORY = PACKAGE.parent.parent
REFERENCE = PACKAGE / "reference"
HEX256 = re.compile(r"[0-9a-fA-F]{64}\Z")
WINDOWS_ABSOLUTE = re.compile(r"(?<![A-Za-z0-9])[A-Za-z]:[\\/]")
LOCAL_ABSOLUTE = re.compile(r"(?<![\w:/])/(?:tmp|private/tmp|Users|home)/[^\s\"<>]+")
STAGES = {
    "HANDOFF", "TURN_START", "REVEALING", "PLAYING", "AI_THINKING",
    "AI_TARGETING", "AI_PLACING", "AI_SETTLING", "TERMINAL",
}
DELAYS = {
    "TURN_START": (300, 160), "REVEALING": (650, 500),
    "AI_TARGETING": (280, 160), "AI_PLACING": (340, 180),
    "AI_SETTLING": (480, 320),
}
WIN_LINES = [
    [0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6],
    [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6],
]
GOLDEN_IDS = {
    "scriptedGames": {
        "picpac-player-one-wins-with-o", "picpac-player-two-wins-with-x",
        "picpac-full-board-draw", "classic-player-one-top-row",
    },
    "ruleTransitions": {
        "draw-removes-piece-before-placement", "legal-placement-switches-actor",
        "stale-turn-rejected-without-mutation", "occupied-cell-rejected-without-mutation",
        "wrong-phase-rejected-without-mutation", "exhausted-symbol-rejected-without-mutation",
        "terminal-command-rejected-without-mutation",
    },
    "bagProbabilities": {"initial-five-five", "held-x-is-excluded", "held-o-is-excluded", "single-o-remains"},
    "deterministicDraws": {
        "initial-roll-zero-is-x", "initial-roll-four-is-x", "initial-roll-five-is-o",
        "initial-roll-nine-is-o", "two-one-boundary-x", "two-one-boundary-o",
    },
    "scriptedRandomTraces": {
        "bag-five-call-boundary-trace", "random-agent-three-call-index-trace",
    },
    "aiChoices": {
        "easy-immediate-win-with-x", "easy-immediate-win-with-o", "medium-opening-held-x",
        "hard-opening-held-x", "hard-opening-held-o", "random-scripted-legal-index",
    },
    "presentationScenarios": {
        "local-handoff-ready-reveal-place", "computer-winning-move-settles-before-result",
        "computer-draw-move-settles-before-result",
    },
    "restorationScenarios": {
        "held-piece-restores-without-redraw", "computer-target-restores-and-commits-once",
        "stale-presentation-callback-after-rematch-is-ignored",
        "cancelled-ai-result-after-mode-replacement-is-ignored",
    },
}
REQUIRED = (
    "PIC_PAC_POE_IOS_HANDOFF.md", "ANDROID_TO_SWIFTUI_MAP.md",
    "BEHAVIOR_AND_STATE.md", "DESIGN_AND_MOTION.md",
    "SCREENS_AND_ACCESSIBILITY.md", "IOS_PARITY_CHECKLIST.md",
    "ASSET_MANIFEST.md", "MAC_CODEX_BOOTSTRAP_PROMPT.md",
    "PRIVACY_AND_STORE.md", "REPOSITORY_AND_DELIVERY.md",
    "release-identity.json", "design-tokens.json", "motion-spec.json",
    "state-machine.json", "golden-fixtures.json", "assets-manifest.json",
    "reference/screenshot-manifest.json", "reference/checksums.sha256",
)


class Verifier:
    def __init__(self):
        self.errors = []
        self.warnings = []
        self.json_documents = {}
        self.artifacts = {}
        self.link_count = 0
        self.hash_count = 0

    def fail(self, message):
        self.errors.append(message)

    @staticmethod
    def label(path):
        try:
            return path.relative_to(REPOSITORY).as_posix()
        except ValueError:
            return str(path)

    def read_text(self, path):
        try:
            return path.read_text(encoding="utf-8-sig")
        except (OSError, UnicodeError) as error:
            self.fail(f"Cannot read UTF-8 {self.label(path)}: {error}")
            return None

    def local_target(self, value, base, boundary, context):
        if not isinstance(value, str) or not value.strip():
            self.fail(f"{context}: missing/non-string relative path")
            return None
        value = unquote(value)
        if "\\" in value or WINDOWS_ABSOLUTE.search(value) or value.startswith("/"):
            self.fail(f"{context}: non-portable/absolute path {value!r}")
            return None
        if urlsplit(value).scheme or value.startswith("//"):
            self.fail(f"{context}: expected a local relative path, got {value!r}")
            return None
        target = (base / value).resolve()
        try:
            target.relative_to(boundary.resolve())
        except ValueError:
            self.fail(f"{context}: path escapes allowed root: {value!r}")
            return None
        if not target.exists():
            self.fail(f"{context}: missing {self.label(target)}")
            return None
        return target

    def scan_documents(self):
        for name in REQUIRED:
            if not (PACKAGE / name).is_file():
                self.fail(f"Missing required file: docs/ios-handoff/{name}")
        for path in sorted(PACKAGE.rglob("*")):
            if not path.is_file() or path.suffix not in {".md", ".json", ".mmd"}:
                continue
            content = self.read_text(path)
            if content is None:
                continue
            if WINDOWS_ABSOLUTE.search(content) or LOCAL_ABSOLUTE.search(content):
                self.fail(f"Absolute machine-local path leaked into {self.label(path)}")
            if re.search(r"-----BEGIN (?:ENCRYPTED )?PRIVATE KEY-----", content):
                self.fail(f"Private-key material appears in {self.label(path)}")
            if path.suffix == ".json":
                try:
                    def reject_constant(value):
                        raise ValueError(f"non-JSON numeric constant {value}")
                    document = json.loads(content, parse_constant=reject_constant)
                    self.json_documents[path.relative_to(PACKAGE).as_posix()] = document
                except (ValueError, json.JSONDecodeError) as error:
                    self.fail(f"Invalid JSON in {self.label(path)}: {error}")
            elif path.suffix == ".md":
                self.markdown_links(path, content)

    def markdown_links(self, path, content):
        # Ignore code examples, including illustrative shell placeholders.
        lines = []
        fence = None
        for line in content.splitlines():
            marker = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
            if marker:
                token = marker.group(1)
                if fence is None:
                    fence = token[0]
                elif token[0] == fence:
                    fence = None
                continue
            if fence is None:
                lines.append(line)
        prose = "\n".join(lines)
        prose = re.sub(r"(`+).*?\1", "", prose, flags=re.DOTALL)
        definitions = {}
        for match in re.finditer(r"^\s{0,3}\[([^\]]+)\]:\s*(?:<([^>]+)>|(\S+))", prose, re.MULTILINE):
            key = " ".join(match.group(1).lower().split())
            definitions[key] = match.group(2) or match.group(3)
        destinations = list(definitions.values())

        # Match destination parentheses with nesting, so URL/path parentheses
        # are not truncated by the common simple Markdown-link regex.
        for match in re.finditer(r"!?\[(?:[^\]\\]|\\.)*\]\(", prose):
            start = match.end()
            index, depth, escaped = start, 1, False
            while index < len(prose) and depth:
                char = prose[index]
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
                index += 1
            if depth:
                self.fail(f"Unclosed Markdown link in {self.label(path)}")
                continue
            raw = prose[start:index - 1].strip()
            destination = re.match(r"<([^>]+)>|([^\s]+)", raw)
            if destination:
                destinations.append(destination.group(1) or destination.group(2))
        for match in re.finditer(r"!?\[([^\]]+)\]\[([^\]]*)\]", prose):
            key = " ".join((match.group(2) or match.group(1)).lower().split())
            if key not in definitions:
                self.fail(f"Undefined Markdown reference [{key}] in {self.label(path)}")
        # Definition destinations also cover shortcut references such as [name].
        for destination in destinations:
            if destination.startswith("#"):
                continue
            parts = urlsplit(destination)
            if parts.scheme in {"http", "https", "mailto"}:
                continue  # deliberately no network validation
            if parts.scheme:
                self.fail(f"Unsupported/non-portable link in {self.label(path)}: {destination}")
                continue
            self.link_count += 1
            self.local_target(parts.path, path.parent, REPOSITORY, self.label(path))

    def document(self, name):
        value = self.json_documents.get(name)
        if value is not None and not isinstance(value, dict):
            self.fail(f"{name}: top-level JSON value must be an object")
            return {}
        return value or {}

    def hash_file(self, path):
        digest = hashlib.sha256()
        try:
            with path.open("rb") as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(block)
            self.hash_count += 1
            return digest.hexdigest()
        except OSError as error:
            self.fail(f"Cannot hash {self.label(path)}: {error}")
            return None

    def records(self, name, sections, base, boundary):
        document = self.document(name)
        if document.get("schemaVersion") != 1:
            self.fail(f"{name}: schemaVersion must be 1")
        seen = set()
        for section in sections:
            rows = document.get(section)
            if not isinstance(rows, list) or not rows:
                self.fail(f"{name}: {section} must be a nonempty list")
                continue
            for index, row in enumerate(rows):
                context = f"{name}:{section}[{index}]"
                if not isinstance(row, dict):
                    self.fail(f"{context}: expected an object")
                    continue
                target = self.local_target(row.get("path"), base, boundary, context)
                if target is None:
                    continue
                if not target.is_file():
                    self.fail(f"{context}: artifact must be a regular file")
                    continue
                if target in seen:
                    self.fail(f"{context}: duplicate artifact {self.label(target)}")
                seen.add(target)
                expected = row.get("sha256")
                if not isinstance(expected, str) or not HEX256.fullmatch(expected):
                    self.fail(f"{context}: invalid SHA-256")
                elif self.hash_file(target) != expected.lower():
                    self.fail(f"{context}: SHA-256 mismatch for {self.label(target)}")
                size = row.get("sizeBytes")
                if type(size) is not int or size <= 0 or size != target.stat().st_size:
                    self.fail(f"{context}: sizeBytes mismatch/invalid for {self.label(target)}")
                if section == "screenshots":
                    if not row.get("sourceFile") or not row.get("kind"):
                        self.fail(f"{context}: screenshot needs sourceFile and kind provenance")
                for dimension in ("width", "height", "widthPx", "heightPx"):
                    if dimension in row and (type(row[dimension]) is not int or row[dimension] <= 0):
                        self.fail(f"{context}: invalid {dimension}")
                if base == REFERENCE:
                    self.artifacts[target] = expected.lower() if isinstance(expected, str) else ""

    def checksums(self):
        path = REFERENCE / "checksums.sha256"
        content = self.read_text(path)
        if content is None:
            return
        seen = set()
        for line_number, line in enumerate(content.splitlines(), 1):
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            match = re.fullmatch(r"([0-9a-fA-F]{64})[ \t]+\*?(.+)", line)
            if not match:
                self.fail(f"checksums.sha256:{line_number}: malformed checksum row")
                continue
            target = self.local_target(match.group(2), REFERENCE, REFERENCE, f"checksums.sha256:{line_number}")
            if target is None:
                continue
            if target in seen:
                self.fail(f"checksums.sha256:{line_number}: duplicate artifact")
            seen.add(target)
            if not target.is_file() or self.hash_file(target) != match.group(1).lower():
                self.fail(f"checksums.sha256:{line_number}: checksum mismatch")
            if target in self.artifacts and self.artifacts[target] != match.group(1).lower():
                self.fail(f"checksums.sha256:{line_number}: differs from screenshot manifest")
        for target in self.artifacts.keys() - seen:
            self.fail(f"Reference checksum missing: {self.label(target)}")

    def contracts(self):
        tokens = self.document("design-tokens.json")
        motion = self.document("motion-spec.json")
        machine = self.document("state-machine.json")
        for name, document in (("design-tokens.json", tokens), ("motion-spec.json", motion), ("state-machine.json", machine)):
            if document.get("schemaVersion") != 1:
                self.fail(f"{name}: schemaVersion must be 1")
            if document.get("pathBase") == "repository root":
                for source in document.get("sources", []):
                    self.local_target(source, REPOSITORY, REPOSITORY, f"{name}:sources")
            elif isinstance(document.get("sources"), dict):
                for source in document["sources"].values():
                    self.local_target(source, PACKAGE, REPOSITORY, f"{name}:sources")
        themes = tokens.get("themes", {})
        for name in ("dark", "light"):
            palette = themes.get(name, {}) if isinstance(themes, dict) else {}
            for role in ("canvas", "surface", "text", "textSecondary", "x", "o", "playerOne", "playerTwo", "focus", "action", "onAction"):
                color = palette.get(role, {}) if isinstance(palette, dict) else {}
                if not isinstance(color, dict):
                    self.fail(f"design-tokens.json: {name}.{role} must be an object")
                    continue
                hexadecimal, integers, floats = color.get("hex"), color.get("rgba8"), color.get("rgba")
                valid_hex = isinstance(hexadecimal, str) and re.fullmatch(r"#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?", hexadecimal)
                valid_ints = isinstance(integers, list) and len(integers) == 4 and all(type(v) is int and 0 <= v <= 255 for v in integers)
                valid_floats = isinstance(floats, list) and len(floats) == 4 and all(type(v) in (int, float) and 0 <= v <= 1 for v in floats)
                if not (valid_hex and valid_ints and valid_floats):
                    self.fail(f"design-tokens.json: invalid {name}.{role} hex/RGBA")
                else:
                    expected = [int(hexadecimal[n:n + 2], 16) for n in (1, 3, 5)]
                    if len(hexadecimal) == 9:
                        expected.append(int(hexadecimal[7:9], 16))
                    else:
                        expected.append(255)
                    if integers != expected or any(abs(i / 255 - f) > 0.000002 for i, f in zip(integers, floats)):
                        self.fail(f"design-tokens.json: inconsistent {name}.{role} hex/RGBA")
        for key in ("typography", "wordmark", "board", "piece", "spacingDp", "layouts"):
            if not isinstance(tokens.get(key), dict) or not tokens[key]:
                self.fail(f"design-tokens.json: missing/non-object {key}")
        animations = motion.get("animations", [])
        if not isinstance(animations, list) or not any(isinstance(a, dict) and a.get("id") == "home-wordmark-entrance" for a in animations):
            self.fail("motion-spec.json: missing home-wordmark-entrance")
        clock = motion.get("presentationClock", {})
        if not isinstance(clock, dict):
            self.fail("motion-spec.json: presentationClock must be an object")
            clock = {}
        clock_rows = clock.get("stageDelays", [])
        if not isinstance(clock_rows, list):
            self.fail("motion-spec.json: stageDelays must be a list")
            clock_rows = []
        actual = {row.get("stage"): (row.get("normalMs"), row.get("reducedMs")) for row in clock_rows if isinstance(row, dict)}
        if actual != DELAYS:
            self.fail(f"motion-spec.json: presentation delays differ from contract: {actual}")
        if set(clock.get("untimedStages", [])) != STAGES - DELAYS.keys():
            self.fail("motion-spec.json: untimed stage inventory differs from contract")
        # State schema is intentionally validated by names as well as shape:
        # prose mentioning a stage is not enough to count as a defined state.
        states = machine.get("states", machine.get("stages", []))
        if isinstance(states, dict):
            names = set(states)
        elif isinstance(states, list):
            names = {s if isinstance(s, str) else s.get("id", s.get("name", s.get("stage"))) for s in states if isinstance(s, (str, dict))}
        else:
            names = set()
        if names != STAGES:
            self.fail(f"state-machine.json: expected all nine stages, found {sorted(str(n) for n in names)}")
        if isinstance(states, dict):
            for name, state in states.items():
                if not isinstance(state, dict):
                    self.fail(f"state-machine.json: state {name} must be an object")
                    continue
                duration = state.get("durationMs", {})
                if not isinstance(duration, dict):
                    self.fail(f"state-machine.json: durationMs for {name} must be an object")
                    continue
                expected = DELAYS.get(name, (None, None))
                if (duration.get("normal"), duration.get("reduced")) != expected:
                    self.fail(f"state-machine.json: duration mismatch for {name}")
                if "durationMs" not in state:
                    self.fail(f"state-machine.json: missing explicit durationMs for {name}")
        transitions = machine.get("transitions")
        if not isinstance(transitions, list) or not transitions:
            self.fail("state-machine.json: transitions must be a nonempty list")
        else:
            pseudo_states = {"OUTSIDE_GAME", "ANY", "mode.initialStage", "newMode.initialStage"}
            for index, transition in enumerate(transitions):
                if not isinstance(transition, dict):
                    self.fail(f"state-machine.json: transition[{index}] must be an object")
                    continue
                for edge in ("from", "to"):
                    if transition.get(edge) not in STAGES | pseudo_states:
                        self.fail(f"state-machine.json: transition[{index}] has unknown {edge}")
                if not transition.get("cause"):
                    self.fail(f"state-machine.json: transition[{index}] needs a cause")
                if transition.get("from") not in {"OUTSIDE_GAME", "ANY", "TERMINAL"} and not transition.get("guard"):
                    self.fail(f"state-machine.json: transition[{index}] needs a guard")

    @staticmethod
    def winning_lines(board, symbol):
        return [line for line in WIN_LINES if all(board[cell] == symbol for cell in line)]

    def golden(self):
        name = "golden-fixtures.json"
        document = self.document(name)
        if document.get("schemaVersion") != 1:
            self.fail(f"{name}: schemaVersion must be 1")
        if document.get("pathBase") != "repository root":
            self.fail(f"{name}: pathBase must be 'repository root'")
        sources = document.get("sources")
        if not isinstance(sources, list) or not sources:
            self.fail(f"{name}: sources must be a nonempty list")
        else:
            for source in sources:
                self.local_target(source, REPOSITORY, REPOSITORY, f"{name}:sources")

        encoding = document.get("encoding", {})
        if encoding.get("cellOrder") != "row-major" or encoding.get("cellIndices") != list(range(9)):
            self.fail(f"{name}: cells must be row-major indices 0 through 8")
        if encoding.get("boardValues") != [None, "X", "O"]:
            self.fail(f"{name}: boardValues must be [null, 'X', 'O']")
        if encoding.get("winningLines") != WIN_LINES:
            self.fail(f"{name}: winning-line order differs from the core contract")
        if encoding.get("initialBag") != {"X": 5, "O": 5}:
            self.fail(f"{name}: initial bag must contain five X and five O pieces")

        required_groups = document.get("requiredFixtureGroups")
        if required_groups != list(GOLDEN_IDS):
            self.fail(
                f"{name}: requiredFixtureGroups must exactly match the ordered fixture-group contract"
            )

        def board(value, context):
            valid = isinstance(value, list) and len(value) == 9 and all(cell in (None, "X", "O") for cell in value)
            if not valid:
                self.fail(f"{context}: board must contain exactly nine null/X/O cells")
                return None
            return value

        # All named fixture groups are append-only compatibility surfaces. IDs
        # protect downstream XCTest/Kotlin parameterized-test routing.
        for section, required_ids in GOLDEN_IDS.items():
            rows = document.get(section)
            if not isinstance(rows, list):
                self.fail(f"{name}: {section} must be a list")
                continue
            ids = [row.get("id") for row in rows if isinstance(row, dict)]
            if len(ids) != len(set(ids)):
                self.fail(f"{name}: duplicate ID in {section}")
            missing = required_ids - set(ids)
            if missing:
                self.fail(f"{name}: {section} is missing IDs {sorted(missing)}")

        # Recursively catch malformed boards, including presentation/restoration
        # snapshots, without dictating how either platform serializes state.
        def visit(value, context="root"):
            if isinstance(value, dict):
                for key, child in value.items():
                    child_context = f"{context}.{key}"
                    if key in {"board", "expectedBoard"}:
                        board(child, child_context)
                    else:
                        visit(child, child_context)
            elif isinstance(value, list):
                for index, child in enumerate(value):
                    visit(child, f"{context}[{index}]")
        visit(document)

        for index, game in enumerate(document.get("scriptedGames", [])):
            context = f"{name}:scriptedGames[{index}]"
            if not isinstance(game, dict):
                self.fail(f"{context}: expected an object")
                continue
            mode, actor = game.get("mode"), game.get("starter")
            if mode not in {"PIC_PAC", "CLASSIC"} or actor not in {"ONE", "TWO"}:
                self.fail(f"{context}: invalid mode/starter")
                continue
            cells, remaining = [None] * 9, {"X": 5, "O": 5}
            outcome = None
            for move_index, move in enumerate(game.get("moves", [])):
                move_context = f"{context}:moves[{move_index}]"
                if outcome is not None:
                    self.fail(f"{move_context}: move follows a terminal position")
                    break
                if not isinstance(move, dict) or move.get("actor") != actor:
                    self.fail(f"{move_context}: actors must alternate from starter")
                    break
                symbol = move.get("draw") if mode == "PIC_PAC" else ("X" if actor == "ONE" else "O")
                cell = move.get("cell")
                if symbol not in {"X", "O"} or type(cell) is not int or cell not in range(9):
                    self.fail(f"{move_context}: invalid symbol/cell")
                    break
                if cells[cell] is not None:
                    self.fail(f"{move_context}: occupied cell")
                    break
                if mode == "PIC_PAC":
                    if remaining[symbol] <= 0:
                        self.fail(f"{move_context}: drawn symbol is exhausted")
                        break
                    remaining[symbol] -= 1
                cells[cell] = symbol
                lines = self.winning_lines(cells, symbol)
                if lines:
                    outcome = {"kind": "WIN", "actor": actor, "symbol": symbol, "lines": lines}
                elif all(value is not None for value in cells):
                    outcome = {"kind": "DRAW"}
                actor = "TWO" if actor == "ONE" else "ONE"
            expected = game.get("expected", {})
            if cells != expected.get("board"):
                self.fail(f"{context}: simulated board differs from expected board")
            if outcome != expected.get("outcome"):
                self.fail(f"{context}: simulated outcome differs from expected outcome")
            if mode == "PIC_PAC" and (remaining["X"], remaining["O"]) != (expected.get("remainingX"), expected.get("remainingO")):
                self.fail(f"{context}: simulated bag counts differ from expected counts")

        for index, row in enumerate(document.get("bagProbabilities", [])):
            context = f"{name}:bagProbabilities[{index}]"
            if not isinstance(row, dict):
                self.fail(f"{context}: expected an object")
                continue
            x, o, expected = row.get("remainingX"), row.get("remainingO"), row.get("expected", {})
            if type(x) is not int or type(o) is not int or x < 0 or o < 0 or x + o <= 0:
                self.fail(f"{context}: remaining counts must be nonnegative with a positive total")
                continue
            total = x + o
            if expected.get("hiddenTotal") != total:
                self.fail(f"{context}: hiddenTotal must equal remainingX + remainingO")
            if expected.get("x") != {"numerator": x, "denominator": total} or expected.get("o") != {"numerator": o, "denominator": total}:
                self.fail(f"{context}: probability fractions do not match bag counts")

        for index, row in enumerate(document.get("deterministicDraws", [])):
            context = f"{name}:deterministicDraws[{index}]"
            if not isinstance(row, dict):
                self.fail(f"{context}: expected an object")
                continue
            x, o, bound, result = (row.get(key) for key in ("remainingX", "remainingO", "nextIntBound", "scriptedResult"))
            if not all(type(value) is int for value in (x, o, bound, result)) or bound != x + o or result not in range(bound):
                self.fail(f"{context}: invalid random bound/result")
                continue
            expected_symbol = "X" if result < x else "O"
            if row.get("expectedSymbol") != expected_symbol:
                self.fail(f"{context}: expectedSymbol does not match weighted-draw boundary")

        for index, trace in enumerate(document.get("scriptedRandomTraces", [])):
            context = f"{name}:scriptedRandomTraces[{index}]"
            if not isinstance(trace, dict):
                self.fail(f"{context}: expected an object")
                continue
            consumer = trace.get("consumer")
            steps = trace.get("steps")
            if consumer not in {"PIC_PAC_SESSION", "RANDOM_AGENT"}:
                self.fail(f"{context}: unknown scripted-random consumer")
                continue
            if not isinstance(steps, list) or not steps:
                self.fail(f"{context}: steps must be a nonempty list")
                continue
            if [step.get("call") for step in steps if isinstance(step, dict)] != list(range(1, len(steps) + 1)):
                self.fail(f"{context}: call indices must be contiguous and one-based")

            if consumer == "PIC_PAC_SESSION":
                initial = trace.get("initial", {})
                x, o = initial.get("remainingX"), initial.get("remainingO")
                occupied = set()
                if (x, o) != (5, 5):
                    self.fail(f"{context}: session trace must begin with the canonical 5+5 bag")
                    continue
                for step_index, step in enumerate(steps):
                    step_context = f"{context}:steps[{step_index}]"
                    if not isinstance(step, dict):
                        self.fail(f"{step_context}: expected an object")
                        continue
                    bound, result = step.get("nextIntBound"), step.get("scriptedResult")
                    if type(bound) is not int or type(result) is not int or bound != x + o or result not in range(bound):
                        self.fail(f"{step_context}: invalid or out-of-order bag bound/result")
                        continue
                    expected_symbol = "X" if result < x else "O"
                    if step.get("expectedSymbol") != expected_symbol:
                        self.fail(f"{step_context}: expectedSymbol does not match weighted-draw boundary")
                    if expected_symbol == "X":
                        x -= 1
                    else:
                        o -= 1
                    cell = step.get("placeCell")
                    if type(cell) is not int or cell not in range(9) or cell in occupied:
                        self.fail(f"{step_context}: placeCell must be a unique row-major cell")
                    else:
                        occupied.add(cell)
            else:
                for step_index, step in enumerate(steps):
                    step_context = f"{context}:steps[{step_index}]"
                    if not isinstance(step, dict):
                        self.fail(f"{step_context}: expected an object")
                        continue
                    cells = board(step.get("board"), step_context)
                    legal = step.get("legalCells")
                    bound, result = step.get("nextIntBound"), step.get("scriptedResult")
                    expected_cell = step.get("expectedCell")
                    if cells is None:
                        continue
                    actual_legal = [cell for cell, value in enumerate(cells) if value is None]
                    if legal != actual_legal:
                        self.fail(f"{step_context}: legalCells differ from the row-major empty cells")
                    if type(bound) is not int or bound != len(actual_legal) or type(result) is not int or result not in range(bound):
                        self.fail(f"{step_context}: invalid random-agent bound/result")
                    elif expected_cell != actual_legal[result]:
                        self.fail(f"{step_context}: scripted result does not select expectedCell")
                    held = step.get("heldSymbol")
                    remaining_x, remaining_o = step.get("remainingX"), step.get("remainingO")
                    if held not in {"X", "O"} or not all(type(value) is int for value in (remaining_x, remaining_o)):
                        self.fail(f"{step_context}: invalid held symbol or remaining counts")
                    elif cells.count("X") + remaining_x + (1 if held == "X" else 0) != 5 or cells.count("O") + remaining_o + (1 if held == "O" else 0) != 5:
                        self.fail(f"{step_context}: random-agent state violates bag conservation")

        for index, row in enumerate(document.get("aiChoices", [])):
            context = f"{name}:aiChoices[{index}]"
            if not isinstance(row, dict):
                self.fail(f"{context}: expected an object")
                continue
            cells = board(row.get("board"), context)
            expected_cell = row.get("expectedCell")
            if cells is None or type(expected_cell) is not int or expected_cell not in range(9) or cells[expected_cell] is not None:
                self.fail(f"{context}: expectedCell must be an empty board cell")
                continue
            held = row.get("heldSymbol")
            remaining_x, remaining_o = row.get("remainingX"), row.get("remainingO")
            if held not in {"X", "O"} or not all(type(value) is int for value in (remaining_x, remaining_o)):
                self.fail(f"{context}: invalid held symbol or remaining counts")
                continue
            if cells.count("X") + remaining_x + (1 if held == "X" else 0) != 5 or cells.count("O") + remaining_o + (1 if held == "O" else 0) != 5:
                self.fail(f"{context}: AI state violates bag conservation")
            if row.get("agent") == "HEURISTIC":
                trial = cells.copy()
                trial[expected_cell] = row.get("heldSymbol")
                if not self.winning_lines(trial, row.get("heldSymbol")):
                    self.fail(f"{context}: heuristic fixture's expected cell is not an immediate win")
            if row.get("agent") == "RANDOM_BASELINE":
                legal = row.get("legalCells")
                result = row.get("scriptedNextIntResult")
                if not isinstance(legal, list) or type(result) is not int or result not in range(len(legal)) or legal[result] != expected_cell:
                    self.fail(f"{context}: scripted random index does not select expectedCell")

        if document.get("graphOracle") != {
            "chanceStates": 11065,
            "decisionStates": 21314,
            "terminalStates": 6648,
            "totalStates": 39027,
        }:
            self.fail(f"{name}: graph oracle counts differ from the Kotlin reachability oracle")

    def repository_security(self):
        """Reject tracked signing containers/properties without reading secrets."""
        try:
            result = subprocess.run(
                ["git", "ls-files", "-z"], cwd=REPOSITORY,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
            )
        except OSError as error:
            self.warnings.append(f"Could not inventory tracked files for signing material: {error}")
            return
        if result.returncode != 0:
            self.warnings.append("Could not inventory tracked files for signing material")
            return
        for raw in result.stdout.split(b"\0"):
            if not raw:
                continue
            path = raw.decode("utf-8", errors="replace")
            lower, basename = path.lower(), Path(path).name.lower()
            if lower.endswith((".jks", ".keystore", ".p12", ".pfx")) or basename == "keystore.properties":
                self.fail(f"Tracked signing-sensitive file is forbidden: {path}")

    def release(self, strict):
        document = self.document("release-identity.json")
        android = document.get("android", {})
        play = document.get("play", {})
        if not isinstance(android, dict) or not isinstance(play, dict):
            self.fail("release-identity.json: android and play must be objects")
        else:
            candidate_code = android.get("versionCode")
            highest_code = play.get("highestUploadedVersionCodeConfirmed")
            if type(candidate_code) is not int or type(highest_code) is not int or candidate_code <= highest_code:
                self.fail("release-identity.json: Android versionCode must exceed the confirmed Play maximum")
            if play.get("candidateVersionCodeValid") is not True:
                self.fail("release-identity.json: candidateVersionCodeValid must be true")
            if play.get("appSigningKeyChangeRequested") is not False:
                self.fail("release-identity.json: app-signing key change must remain false")
            if play.get("canonicalReleasePath") != "local-manual-signing-and-owner-manual-upload":
                self.fail("release-identity.json: local manual signing/upload must remain canonical")
        git = document.get("git", {})
        if not isinstance(git, dict):
            self.fail("release-identity.json: git must be an object")
            return
        revision, tag = git.get("releaseCommit"), git.get("releaseTag")
        valid_revision = isinstance(revision, str) and re.fullmatch(r"[0-9a-fA-F]{40}", revision)
        valid_tag = isinstance(tag, str) and bool(tag.strip())
        if strict and not (valid_revision and valid_tag):
            self.fail("Strict release: git.releaseCommit must be 40 hex characters and git.releaseTag must be non-null/nonempty")
        elif not (valid_revision and valid_tag):
            self.warnings.append("Release commit/tag unresolved: portable package validation is not exact-release certification")
        url = git.get("repositoryUrl")
        if not isinstance(url, str) or not url.startswith("https://github.com/"):
            self.fail("release-identity.json: git.repositoryUrl must identify the canonical HTTPS GitHub repository")

    def run(self, strict):
        self.scan_documents()
        self.records("reference/screenshot-manifest.json", ("screenshots", "derived"), REFERENCE, REFERENCE)
        self.records("assets-manifest.json", ("assets",), PACKAGE, REPOSITORY)
        self.checksums()
        self.contracts()
        self.golden()
        self.repository_security()
        self.release(strict)
        for warning in self.warnings:
            print(f"WARNING: {warning}")
        for error in self.errors:
            print(f"ERROR: {error}")
        print(f"Checked {len(self.json_documents)} JSON documents, {self.link_count} relative Markdown links, {self.hash_count} file hashes; {len(self.errors)} error(s).")
        if not self.errors:
            print("PASS: portable handoff verification (read-only; no network or Git mutation).")
        return 1 if self.errors else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict-release", action="store_true", help="Require non-null annotated-tag identity metadata and a full release SHA; does not contact GitHub")
    arguments = parser.parse_args()
    return Verifier().run(arguments.strict_release)


if __name__ == "__main__":
    sys.exit(main())
