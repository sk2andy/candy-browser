package dev.sk2andy.materialbrowser.ui

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyCircuitRulesTest {
    @Test
    fun `straight tiles alternate between vertical and horizontal connections`() {
        val vertical = CandyCircuitRules.connections(
            CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 0),
        )
        val horizontal = CandyCircuitRules.connections(
            CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 1),
        )

        assertEquals(setOf(North, South), vertical)
        assertEquals(setOf(East, West), horizontal)
        assertEquals(
            vertical,
            CandyCircuitRules.connections(
                CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 2),
            ),
        )
    }

    @Test
    fun `curve and branch connections rotate clockwise`() {
        assertEquals(
            setOf(East, South),
            CandyCircuitRules.connections(
                CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 1),
            ),
        )
        assertEquals(
            setOf(East, South, West),
            CandyCircuitRules.connections(
                CandyCircuitTile(CandyCircuitTileShape.Branch, rotation = 2),
            ),
        )
    }

    @Test
    fun `initial board is deterministic and starts without a closed loop`() {
        val first = CandyCircuitRules.initialBoard()
        val second = CandyCircuitRules.initialBoard()

        assertEquals(16, first.size)
        assertEquals(first, second)
        assertEquals(CandyCircuitTileShape.entries.toSet(), first.map { it.shape }.toSet())
        assertTrue(CandyCircuitRules.closedComponents(first).isEmpty())
    }

    @Test
    fun `one clockwise turn closes loop refills its tiles and awards moves`() {
        val before = CandyCircuitGameState()
        val closedTiles = before.tiles.toMutableList().apply {
            this[5] = this[5].copy(rotation = this[5].rotation + 1)
        }
        val next = CandyCircuitRules.rotate(before, tileIndex = 5, random = Random(7))

        assertEquals(13, next.movesRemaining)
        assertEquals(400, next.score)
        assertEquals(400, next.bestScore)
        assertEquals(1, next.combo)
        assertEquals(400, next.lastPointsGained)
        assertEquals(2, next.lastMovesGained)
        assertEquals(4, next.lastClosedTileCount)
        assertEquals(setOf(0, 1, 4, 5), next.lastClosedTileIndices)
        next.lastClosedTileIndices.forEach { index ->
            assertNotEquals(closedTiles[index].normalizedForTest(), next.tiles[index])
        }
        before.tiles.indices
            .filterNot(next.lastClosedTileIndices::contains)
            .forEach { index -> assertEquals(before.tiles[index], next.tiles[index]) }
        assertTrue(CandyCircuitRules.closedComponents(next.tiles).isEmpty())
    }

    @Test
    fun `visibly closed two by two loop is detected before refill`() {
        val closedBoard = loopReadyBoard().toMutableList().apply {
            this[5] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3)
        }

        val components = CandyCircuitRules.closedComponents(closedBoard)

        assertEquals(1, components.size)
        assertEquals(setOf(0, 1, 4, 5), components.single().tileIndices)
    }

    @Test
    fun `each scored component gives two moves after rotation cost`() {
        val state = CandyCircuitGameState(
            tiles = loopReadyBoard(),
            movesRemaining = 1,
        )

        val next = CandyCircuitRules.rotate(state, tileIndex = 5)

        assertEquals(2, CandyCircuitRules.MOVES_PER_CLOSED_COMPONENT)
        assertEquals(2, next.movesRemaining)
        assertFalse(next.isGameOver)
    }

    @Test
    fun `non scoring turn resets combo`() {
        val scoringState = CandyCircuitRules.rotate(
            CandyCircuitGameState(),
            tileIndex = 5,
            random = Random(21),
        )
        val next = CandyCircuitRules.rotate(
            scoringState.copy(tiles = emptyBoard()),
            tileIndex = 0,
            random = Random(22),
        )

        assertEquals(400, next.score)
        assertEquals(0, next.combo)
        assertEquals(0, next.lastPointsGained)
        assertEquals(0, next.lastMovesGained)
        assertEquals(0, next.lastClosedTileCount)
    }

    @Test
    fun `same circuit can score again after its tiles were refilled`() {
        val first = CandyCircuitRules.rotate(
            CandyCircuitGameState(tiles = loopReadyBoard()),
            tileIndex = 5,
            random = Random(11),
        )
        val rebuilt = first.copy(tiles = loopReadyBoard())

        val second = CandyCircuitRules.rotate(rebuilt, tileIndex = 5, random = Random(12))

        assertEquals(800, second.lastPointsGained)
        assertEquals(1_200, second.score)
        assertEquals(setOf(0, 1, 4, 5), second.lastClosedTileIndices)
        assertTrue(CandyCircuitRules.closedComponents(second.tiles).isEmpty())
    }

    @Test
    fun `different random seeds produce different loop free refills`() {
        val closedIndices = setOf(0, 1, 4, 5)
        val refills = (0 until 8).map { seed ->
            val next = CandyCircuitRules.rotate(
                CandyCircuitGameState(tiles = loopReadyBoard()),
                tileIndex = 5,
                random = Random(seed),
            )

            assertTrue(CandyCircuitRules.closedComponents(next.tiles).isEmpty())
            next.tiles.filterIndexed { index, _ -> index in closedIndices }
        }

        assertTrue(refills.toSet().size > 1)
    }

    @Test
    fun `same random seed reproduces refill`() {
        val state = CandyCircuitGameState(tiles = loopReadyBoard())

        val first = CandyCircuitRules.rotate(state, tileIndex = 5, random = Random(71))
        val second = CandyCircuitRules.rotate(state, tileIndex = 5, random = Random(71))

        assertEquals(first.tiles, second.tiles)
    }

    @Test
    fun `closed cycle scores even when one tile has a dangling connection`() {
        val twoByTwoWithDanglingBranch = emptyBoard().toMutableList().apply {
            this[0] = CandyCircuitTile(CandyCircuitTileShape.Branch, rotation = 1)
            this[1] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
            this[4] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 0)
            this[5] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3)
        }

        val component = CandyCircuitRules.closedComponents(twoByTwoWithDanglingBranch).single()

        assertEquals(setOf(0, 1, 4, 5), component.tileIndices)
    }

    @Test
    fun `closed cycle ignores reciprocal tail connected through a bridge`() {
        val loopWithTail = emptyBoard().toMutableList().apply {
            this[0] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 1)
            this[1] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
            this[4] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 0)
            this[5] = CandyCircuitTile(CandyCircuitTileShape.Branch, rotation = 0)
            this[6] = CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 1)
        }

        val component = CandyCircuitRules.closedComponents(loopWithTail).single()

        assertEquals(setOf(0, 1, 4, 5), component.tileIndices)
    }

    @Test
    fun `screenshot circuit is detected despite unmatched branch`() {
        val screenshotBoard = screenshotBoard()

        val component = CandyCircuitRules.closedComponents(screenshotBoard).single()

        assertEquals(setOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 14, 15), component.tileIndices)
    }

    @Test
    fun `screenshot circuit scores when final tile closes it`() {
        val readyBoard = screenshotBoard().toMutableList().apply {
            this[5] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
        }

        val next = CandyCircuitRules.rotate(
            CandyCircuitGameState(tiles = readyBoard),
            tileIndex = 5,
            random = Random(41),
        )

        assertEquals(1_200, next.lastPointsGained)
        assertEquals(setOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 14, 15), next.lastClosedTileIndices)
        assertTrue(CandyCircuitRules.closedComponents(next.tiles).isEmpty())
    }

    @Test
    fun `reciprocal six tile network with branches is closed`() {
        val branchLoop = emptyBoard().toMutableList().apply {
            this[0] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 1)
            this[1] = CandyCircuitTile(CandyCircuitTileShape.Branch, rotation = 2)
            this[2] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
            this[4] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 0)
            this[5] = CandyCircuitTile(CandyCircuitTileShape.Branch, rotation = 0)
            this[6] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3)
        }

        val component = CandyCircuitRules.closedComponents(branchLoop).single()

        assertEquals(setOf(0, 1, 2, 4, 5, 6), component.tileIndices)
    }

    @Test
    fun `invalid tile index and finished round do not mutate state`() {
        val state = CandyCircuitGameState()
        assertSame(state, CandyCircuitRules.rotate(state, tileIndex = -1))
        assertSame(state, CandyCircuitRules.rotate(state, tileIndex = 16))

        val finished = state.copy(movesRemaining = 0)
        assertSame(finished, CandyCircuitRules.rotate(finished, tileIndex = 10))
    }

    @Test
    fun `twelfth move ends round`() {
        var state = CandyCircuitGameState(tiles = emptyBoard())
        repeat(12) {
            state = CandyCircuitRules.rotate(state, tileIndex = 15)
        }

        assertEquals(0, state.movesRemaining)
        assertTrue(state.isGameOver)
    }

    @Test
    fun `restart restores board and keeps best score`() {
        val played = CandyCircuitRules.rotate(CandyCircuitGameState(bestScore = 900), tileIndex = 5)
        val restarted = CandyCircuitRules.restart(played)

        assertEquals(CandyCircuitRules.initialBoard(), restarted.tiles)
        assertEquals(12, restarted.movesRemaining)
        assertEquals(0, restarted.score)
        assertEquals(900, restarted.bestScore)
        assertEquals(0, restarted.combo)
        assertFalse(restarted.hasNewBest)
        assertNotEquals(played, restarted)
    }

    @Test
    fun `only a strictly higher score marks a new personal best`() {
        val tied = CandyCircuitRules.rotate(
            CandyCircuitGameState(bestScore = 400),
            tileIndex = 5,
            random = Random(31),
        )
        val beaten = CandyCircuitRules.rotate(
            CandyCircuitGameState(bestScore = 300),
            tileIndex = 5,
            random = Random(32),
        )
        val followingMove = CandyCircuitRules.rotate(
            beaten.copy(tiles = emptyBoard()),
            tileIndex = 0,
            random = Random(33),
        )

        assertFalse(tied.hasNewBest)
        assertTrue(beaten.hasNewBest)
        assertFalse(followingMove.hasNewBest)
    }

    private fun emptyBoard(): List<CandyCircuitTile> = List(16) {
        CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 0)
    }

    private fun loopReadyBoard(): List<CandyCircuitTile> = emptyBoard().toMutableList().apply {
        this[0] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 1)
        this[1] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
        this[4] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 0)
        this[5] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
    }

    private fun screenshotBoard(): List<CandyCircuitTile> = emptyBoard().toMutableList().apply {
        this[1] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 1)
        this[2] = CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 1)
        this[3] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
        this[4] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 1)
        this[5] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3)
        this[7] = CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 0)
        this[8] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 0)
        this[9] = CandyCircuitTile(CandyCircuitTileShape.Branch, rotation = 0)
        this[10] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
        this[11] = CandyCircuitTile(CandyCircuitTileShape.Straight, rotation = 0)
        this[14] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 0)
        this[15] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3)
    }

    private fun CandyCircuitTile.normalizedForTest(): CandyCircuitTile = copy(
        rotation = when (shape) {
            CandyCircuitTileShape.Straight -> Math.floorMod(rotation, 2)
            CandyCircuitTileShape.Curve,
            CandyCircuitTileShape.Branch,
            -> Math.floorMod(rotation, 4)
        },
    )

    private companion object {
        val North = CandyCircuitDirection.North
        val East = CandyCircuitDirection.East
        val South = CandyCircuitDirection.South
        val West = CandyCircuitDirection.West
    }
}
