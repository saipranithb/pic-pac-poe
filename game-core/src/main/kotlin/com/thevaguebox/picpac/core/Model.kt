package com.thevaguebox.picpac.core

enum class Symbol { X, O;
    fun other(): Symbol = if (this == X) O else X
}

enum class Player { ONE, TWO;
    fun other(): Player = if (this == ONE) TWO else ONE
    val label: String get() = if (this == ONE) "Player 1" else "Player 2"
}

@JvmInline
value class Cell private constructor(val index: Int) {
    companion object {
        val ALL: List<Cell> = (0..8).map(::Cell)
        fun of(index: Int): Cell {
            require(index in 0..8) { "Cell index must be 0..8" }
            return Cell(index)
        }
    }
}

data class WinningLine(val first: Cell, val second: Cell, val third: Cell) {
    val cells: List<Cell> = listOf(first, second, third)
}

@JvmInline
value class Board private constructor(val code: Int) {
    operator fun get(cell: Cell): Symbol? = when ((code / POWERS[cell.index]) % 3) {
        1 -> Symbol.X
        2 -> Symbol.O
        else -> null
    }

    fun place(cell: Cell, symbol: Symbol): Board {
        require(this[cell] == null) { "Cell ${cell.index} is occupied" }
        val digit = if (symbol == Symbol.X) 1 else 2
        return Board(code + digit * POWERS[cell.index])
    }

    val occupiedCount: Int
        get() {
            var value = code
            var count = 0
            repeat(9) {
                if (value % 3 != 0) count++
                value /= 3
            }
            return count
        }

    val isFull: Boolean get() = occupiedCount == 9

    fun count(symbol: Symbol): Int {
        val wanted = if (symbol == Symbol.X) 1 else 2
        var value = code
        var count = 0
        repeat(9) {
            if (value % 3 == wanted) count++
            value /= 3
        }
        return count
    }

    fun legalCells(): List<Cell> = Cell.ALL.filter { this[it] == null }

    fun winningLines(symbol: Symbol): List<WinningLine> = WIN_LINES.filter { line ->
        line.cells.all { this[it] == symbol }
    }

    fun hasWinner(): Boolean = winningLines(Symbol.X).isNotEmpty() || winningLines(Symbol.O).isNotEmpty()

    fun symbols(): List<Symbol?> = Cell.ALL.map { this[it] }

    companion object {
        private val POWERS = intArrayOf(1, 3, 9, 27, 81, 243, 729, 2187, 6561)
        const val STATE_COUNT: Int = 19_683
        val EMPTY: Board = Board(0)
        val WIN_LINES: List<WinningLine> = listOf(
            WinningLine(Cell.of(0), Cell.of(1), Cell.of(2)),
            WinningLine(Cell.of(3), Cell.of(4), Cell.of(5)),
            WinningLine(Cell.of(6), Cell.of(7), Cell.of(8)),
            WinningLine(Cell.of(0), Cell.of(3), Cell.of(6)),
            WinningLine(Cell.of(1), Cell.of(4), Cell.of(7)),
            WinningLine(Cell.of(2), Cell.of(5), Cell.of(8)),
            WinningLine(Cell.of(0), Cell.of(4), Cell.of(8)),
            WinningLine(Cell.of(2), Cell.of(4), Cell.of(6)),
        )

        fun fromCode(code: Int): Board {
            require(code in 0 until STATE_COUNT) { "Invalid base-3 board code" }
            return Board(code)
        }

        fun fromSymbols(symbols: List<Symbol?>): Board {
            require(symbols.size == 9)
            var board = EMPTY
            symbols.forEachIndexed { index, symbol ->
                if (symbol != null) board = board.place(Cell.of(index), symbol)
            }
            return board
        }
    }
}

@JvmInline
value class TurnToken(val value: Long)
