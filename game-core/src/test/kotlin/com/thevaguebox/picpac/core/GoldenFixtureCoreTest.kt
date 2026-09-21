package com.thevaguebox.picpac.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import com.thevaguebox.picpac.testing.FixtureContractException
import com.thevaguebox.picpac.testing.GoldenFixtureDocument
import com.thevaguebox.picpac.testing.intValues
import com.thevaguebox.picpac.testing.optionalArray
import com.thevaguebox.picpac.testing.optionalObject
import com.thevaguebox.picpac.testing.optionalString
import com.thevaguebox.picpac.testing.requireArray
import com.thevaguebox.picpac.testing.requireBoolean
import com.thevaguebox.picpac.testing.requireInt
import com.thevaguebox.picpac.testing.requireLong
import com.thevaguebox.picpac.testing.requireObject
import com.thevaguebox.picpac.testing.requireString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class GoldenFixtureCoreTest {
    private val fixtures = GoldenFixtureDocument.load()
    private val handledGroups = setOf(
        "scriptedGames",
        "ruleTransitions",
        "bagProbabilities",
        "deterministicDraws",
        "scriptedRandomTraces",
        "aiChoices",
        "presentationScenarios",
        "restorationScenarios",
    )

    @Test fun `Kotlin owns every required fixture group`() {
        fixtures.requireHandledGroups(handledGroups)
        assertEquals("34 original cases plus two bounded-random traces", 36, fixtures.caseCount)
    }

    @Test fun `adapter rejects an unknown schema version`() {
        withMutatedFixture { root -> root.addProperty("schemaVersion", 2) }.use { path ->
            assertThrows(FixtureContractException::class.java) { GoldenFixtureDocument.load(path) }
        }
    }

    @Test fun `adapter rejects a required group without a Kotlin owner`() {
        withMutatedFixture { root ->
            root.requireArray("requiredFixtureGroups").add("futureCases")
            root.add("futureCases", JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "future-case") })
            })
        }.use { path ->
            val mutated = GoldenFixtureDocument.load(path)
            assertThrows(FixtureContractException::class.java) { mutated.requireHandledGroups(handledGroups) }
        }
    }

    @Test fun `scripted game fixtures execute through canonical rules`() {
        fixtures.cases("scriptedGames").forEach { fixture -> fixture.verify {
            val value = fixture.value
            val starter = Player.valueOf(value.requireString("starter"))
            val expected = value.requireObject("expected")
            when (value.requireString("mode")) {
                "PIC_PAC" -> {
                    var state = PicPacRules.newGame(starter)
                    value.requireArray("moves").forEach { element ->
                        val move = element.requireObject("move")
                        assertEquals(Player.valueOf(move.requireString("actor")), state.activePlayer)
                        val drawn = PicPacRules.draw(state, Symbol.valueOf(move.requireString("draw")))
                        assertTrue(drawn is TransitionResult.Accepted)
                        state = (drawn as TransitionResult.Accepted).state
                        val token = (state.phase as PicPacPhase.AwaitingPlacement).token
                        val placed = PicPacRules.place(state, Cell.of(move.requireInt("cell")), token)
                        assertTrue(placed is TransitionResult.Accepted)
                        state = (placed as TransitionResult.Accepted).state
                    }
                    assertEquals(expected.requireArray("board").symbols(), state.board.symbols())
                    assertEquals(expected.requireInt("remainingX"), state.remainingX)
                    assertEquals(expected.requireInt("remainingO"), state.remainingO)
                    assertOutcome(expected.requireObject("outcome"), (state.phase as PicPacPhase.Terminal).outcome)
                }
                "CLASSIC" -> {
                    var state = ClassicRules.newGame(starter)
                    value.requireArray("moves").forEach { element ->
                        val move = element.requireObject("move")
                        assertEquals(Player.valueOf(move.requireString("actor")), state.activePlayer)
                        val placed = ClassicRules.place(state, Cell.of(move.requireInt("cell")), state.turnToken)
                        assertTrue(placed is TransitionResult.Accepted)
                        state = (placed as TransitionResult.Accepted).state
                    }
                    assertEquals(expected.requireArray("board").symbols(), state.board.symbols())
                    assertOutcome(expected.requireObject("outcome"), requireNotNull(state.outcome))
                }
                else -> error("unsupported scripted-game mode")
            }
        } }
    }

    @Test fun `rule transition fixtures execute through canonical rules`() {
        fixtures.cases("ruleTransitions").forEach { fixture -> fixture.verify {
            val value = fixture.value
            assertEquals("PIC_PAC", value.requireString("mode"))
            val initial = picPacState(value.requireObject("initial"))
            val before = initial.copy()
            val input = value.requireObject("input")
            val result = when (input.requireString("action")) {
                "DRAW" -> PicPacRules.draw(initial, Symbol.valueOf(input.requireString("symbol")))
                "PLACE" -> PicPacRules.place(
                    initial,
                    Cell.of(input.requireInt("cell")),
                    TurnToken(input.requireLong("turnToken")),
                )
                else -> error("unsupported rule action")
            }
            val expected = value.requireObject("expected")
            if (expected.requireBoolean("accepted")) {
                assertTrue(result is TransitionResult.Accepted)
                val accepted = result as TransitionResult.Accepted
                assertTransitionState(expected, accepted.state)
                assertEvent(expected.requireObject("event"), accepted.event)
            } else {
                assertEquals(
                    TransitionResult.Rejected(RejectionReason.valueOf(expected.requireString("rejection"))),
                    result,
                )
                if (expected.requireBoolean("stateUnchanged")) assertEquals(before, initial)
            }
        } }
    }

    @Test fun `bag probability fixtures execute through state probabilities`() {
        fixtures.cases("bagProbabilities").forEach { fixture -> fixture.verify {
            val value = fixture.value
            val state = stateForBag(value.requireInt("remainingX"), value.requireInt("remainingO"))
            val expected = value.requireObject("expected")
            assertEquals(expected.requireInt("hiddenTotal"), state.hiddenTotal)
            assertFraction(expected.requireObject("x"), state.nextXProbability)
            assertFraction(expected.requireObject("o"), state.nextOProbability)
        } }
    }

    @Test fun `deterministic draw fixtures execute through the game session`() {
        fixtures.cases("deterministicDraws").forEach { fixture -> fixture.verify {
            val value = fixture.value
            val state = stateForBag(value.requireInt("remainingX"), value.requireInt("remainingO"))
            assertEquals(value.requireInt("nextIntBound"), state.hiddenTotal)
            val draws = RecordingIterator(listOf(value.requireInt("scriptedResult")))
            val session = PicPacGameSession.forTesting(state, draws)
            val result = session.reveal() as TransitionResult.Accepted
            assertEquals(Symbol.valueOf(value.requireString("expectedSymbol")), (result.state.phase as PicPacPhase.AwaitingPlacement).held)
            assertEquals(1, draws.callCount)
            assertTrue(draws.isExhausted)
        } }
    }

    @Test fun `scripted bag random trace preserves bound and call order`() {
        fixtures.cases("scriptedRandomTraces")
            .filter { it.value.requireString("consumer") == "PIC_PAC_SESSION" }
            .forEach { fixture -> fixture.verify {
                val steps = fixture.value.requireArray("steps").map { it.requireObject("step") }
                val draws = RecordingIterator(steps.map { it.requireInt("scriptedResult") })
                val session = PicPacGameSession.forTesting(PicPacRules.newGame(), draws)
                steps.forEachIndexed { index, step ->
                    assertEquals(index + 1, step.requireInt("call"))
                    assertEquals(step.requireInt("nextIntBound"), session.state.hiddenTotal)
                    val reveal = session.reveal() as TransitionResult.Accepted
                    assertEquals(index + 1, draws.callCount)
                    val phase = reveal.state.phase as PicPacPhase.AwaitingPlacement
                    assertEquals(Symbol.valueOf(step.requireString("expectedSymbol")), phase.held)
                    val placement = session.place(Cell.of(step.requireInt("placeCell")), phase.token)
                    assertTrue(placement is TransitionResult.Accepted)
                }
                assertTrue(draws.isExhausted)
            } }
    }

    private fun assertTransitionState(expected: JsonObject, actual: PicPacState) {
        expected.optionalArray("board")?.let { assertEquals(it.symbols(), actual.board.symbols()) }
        expected.optionalString("activePlayer")?.let { assertEquals(Player.valueOf(it), actual.activePlayer) }
        if (expected.has("remainingX")) assertEquals(expected.requireInt("remainingX"), actual.remainingX)
        if (expected.has("remainingO")) assertEquals(expected.requireInt("remainingO"), actual.remainingO)
        if (expected.has("hiddenTotal")) assertEquals(expected.requireInt("hiddenTotal"), actual.hiddenTotal)
        expected.optionalString("phase")?.let { assertEquals(it, actual.phase.fixtureName()) }
        expected.optionalString("heldSymbol")?.let {
            assertEquals(Symbol.valueOf(it), (actual.phase as PicPacPhase.AwaitingPlacement).held)
        }
        if (expected.has("turnToken")) {
            assertEquals(expected.requireLong("turnToken"), (actual.phase as PicPacPhase.AwaitingPlacement).token.value)
        }
    }

    private fun assertEvent(expected: JsonObject, actual: GameEvent) {
        when (expected.requireString("kind")) {
            "PIECE_REVEALED" -> {
                actual as GameEvent.PieceRevealed
                assertEquals(Player.valueOf(expected.requireString("actor")), actual.player)
                assertEquals(Symbol.valueOf(expected.requireString("symbol")), actual.symbol)
            }
            "PIECE_PLACED" -> {
                actual as GameEvent.PiecePlaced
                assertEquals(Player.valueOf(expected.requireString("actor")), actual.player)
                assertEquals(Symbol.valueOf(expected.requireString("symbol")), actual.symbol)
                assertEquals(Cell.of(expected.requireInt("cell")), actual.cell)
            }
            else -> error("unsupported fixture event")
        }
    }

    private fun assertOutcome(expected: JsonObject, actual: GameOutcome) {
        when (expected.requireString("kind")) {
            "DRAW" -> assertEquals(GameOutcome.Draw, actual)
            "WIN" -> {
                actual as GameOutcome.Win
                assertEquals(Player.valueOf(expected.requireString("actor")), actual.player)
                assertEquals(Symbol.valueOf(expected.requireString("symbol")), actual.symbol)
                val expectedLines = expected.requireArray("lines").map { line ->
                    line.requireArray("winning line").intValues("winning line")
                }
                assertEquals(expectedLines, actual.lines.map { line -> line.cells.map(Cell::index) })
            }
            else -> error("unsupported outcome")
        }
    }

    private fun assertFraction(expected: JsonObject, actual: Double) {
        assertEquals(
            expected.requireInt("numerator").toDouble() / expected.requireInt("denominator"),
            actual,
            0.0,
        )
    }

    private fun picPacState(value: JsonObject): PicPacState {
        val board = Board.fromSymbols(value.requireArray("board").symbols())
        val outcome = value.optionalObject("outcome")?.let(::gameOutcome)
        val phase = when (value.requireString("phase")) {
            "AWAITING_DRAW" -> PicPacPhase.AwaitingDraw
            "AWAITING_PLACEMENT" -> PicPacPhase.AwaitingPlacement(
                Symbol.valueOf(value.requireString("heldSymbol")),
                TurnToken(value.requireLong("turnToken")),
            )
            "TERMINAL" -> PicPacPhase.Terminal(requireNotNull(outcome))
            else -> error("unsupported phase")
        }
        return PicPacState(
            board = board,
            activePlayer = Player.valueOf(value.requireString("activePlayer")),
            remainingX = value.requireInt("remainingX"),
            remainingO = value.requireInt("remainingO"),
            phase = phase,
            starter = Player.valueOf(value.requireString("starter")),
            revision = value.requireLong("revision"),
        )
    }

    private fun gameOutcome(value: JsonObject): GameOutcome = when (value.requireString("kind")) {
        "DRAW" -> GameOutcome.Draw
        "WIN" -> {
            val board = value.optionalArray("board")
            val lines = value.requireArray("lines").map { element ->
                val cells = element.requireArray("winning line").intValues("winning line").map(Cell::of)
                WinningLine(cells[0], cells[1], cells[2])
            }
            check(board == null)
            GameOutcome.Win(
                Player.valueOf(value.requireString("actor")),
                Symbol.valueOf(value.requireString("symbol")),
                lines,
            )
        }
        else -> error("unsupported outcome")
    }

    private fun stateForBag(remainingX: Int, remainingO: Int): PicPacState {
        val missingX = 5 - remainingX
        val missingO = 5 - remainingO
        val held = when {
            missingX + missingO == 0 -> null
            missingX + missingO == 1 && missingX == 1 -> Symbol.X
            missingX + missingO == 1 && missingO == 1 -> Symbol.O
            else -> null
        }
        val boardX = missingX - if (held == Symbol.X) 1 else 0
        val boardO = missingO - if (held == Symbol.O) 1 else 0
        val board = (0 until Board.STATE_COUNT)
            .asSequence()
            .map(Board::fromCode)
            .first { candidate ->
                candidate.count(Symbol.X) == boardX &&
                    candidate.count(Symbol.O) == boardO &&
                    !candidate.hasWinner()
            }
        return PicPacState(
            board = board,
            activePlayer = Player.ONE,
            remainingX = remainingX,
            remainingO = remainingO,
            phase = held?.let { PicPacPhase.AwaitingPlacement(it, TurnToken(1)) } ?: PicPacPhase.AwaitingDraw,
            starter = Player.ONE,
            revision = 0,
        )
    }

    private fun JsonArray.symbols(): List<Symbol?> = map { value ->
        if (value.isJsonNull) null else Symbol.valueOf(value.asString)
    }

    private fun PicPacPhase.fixtureName(): String = when (this) {
        PicPacPhase.AwaitingDraw -> "AWAITING_DRAW"
        is PicPacPhase.AwaitingPlacement -> "AWAITING_PLACEMENT"
        is PicPacPhase.Terminal -> "TERMINAL"
    }

    private fun withMutatedFixture(mutation: (JsonObject) -> Unit): TemporaryFixture {
        val source = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .map { it.resolve("docs/ios-handoff/golden-fixtures.json") }
            .first(Files::isRegularFile)
        val root = Files.newBufferedReader(source).use { JsonParser.parseReader(it).asJsonObject }
        mutation(root)
        val temporary = Files.createTempFile("picpac-golden-fixtures-", ".json")
        Files.newBufferedWriter(temporary).use { GsonBuilder().create().toJson(root, it) }
        return TemporaryFixture(temporary)
    }

    private class TemporaryFixture(private val path: Path) : AutoCloseable {
        fun use(block: (Path) -> Unit) {
            try {
                block(path)
            } finally {
                close()
            }
        }

        override fun close() {
            Files.deleteIfExists(path)
        }
    }

    private class RecordingIterator(private val values: List<Int>) : Iterator<Int> {
        private var index = 0
        val callCount: Int get() = index
        val isExhausted: Boolean get() = index == values.size
        override fun hasNext(): Boolean = index < values.size
        override fun next(): Int = values.getOrElse(index++) { error("unexpected random call ${index + 1}") }
    }
}
