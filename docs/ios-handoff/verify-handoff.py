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
REQUIRED = (
    "PIC_PAC_POE_IOS_HANDOFF.md", "ANDROID_TO_SWIFTUI_MAP.md",
    "SCREENS_AND_ACCESSIBILITY.md", "IOS_PARITY_CHECKLIST.md",
    "ASSET_MANIFEST.md", "MAC_CODEX_BOOTSTRAP_PROMPT.md",
    "release-identity.json", "design-tokens.json", "motion-spec.json",
    "state-machine.json", "assets-manifest.json",
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

    def release(self, strict):
        document = self.document("release-identity.json")
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
