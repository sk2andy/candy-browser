package dev.sk2andy.materialbrowser.ui

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
        val next = CandyCircuitRules.rotate(CandyCircuitGameState(), tileIndex = 5)

        assertEquals(13, next.movesRemaining)
        assertEquals(400, next.score)
        assertEquals(400, next.bestScore)
        assertEquals(1, next.combo)
        assertEquals(400, next.lastPointsGained)
        assertEquals(2, next.lastMovesGained)
        assertEquals(4, next.lastClosedTileCount)
        assertEquals(setOf(0, 1, 4, 5), next.lastClosedTileIndices)
        assertEquals(
            listOf(
                CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2),
                CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3),
                CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 1),
                CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3),
            ),
            listOf(next.tiles[0], next.tiles[1], next.tiles[4], next.tiles[5]),
        )
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
        val scoringState = CandyCircuitRules.rotate(CandyCircuitGameState(), tileIndex = 5)
        val next = CandyCircuitRules.rotate(scoringState, tileIndex = 0)

        assertEquals(400, next.score)
        assertEquals(0, next.combo)
        assertEquals(0, next.lastPointsGained)
        assertEquals(0, next.lastMovesGained)
        assertEquals(0, next.lastClosedTileCount)
    }

    @Test
    fun `same loop fingerprint cannot score twice after its refill`() {
        val readyBoard = loopReadyBoard()
        val closedBoard = readyBoard.toMutableList().apply {
            this[5] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3)
        }
        val fingerprint = CandyCircuitRules.closedComponents(closedBoard).single().fingerprint
        val state = CandyCircuitGameState(
            tiles = readyBoard,
            scoredFingerprints = setOf(fingerprint),
        )
        val reclosed = CandyCircuitRules.rotate(state, tileIndex = 5)

        assertEquals(0, reclosed.score)
        assertEquals(0, reclosed.combo)
        assertEquals(0, reclosed.lastPointsGained)
        assertEquals(1, reclosed.scoredFingerprints.size)
        assertTrue(CandyCircuitRules.closedComponents(reclosed.tiles).isEmpty())
    }

    @Test
    fun `dangling endpoint prevents otherwise connected tiles from closing`() {
        val twoByTwoWithDanglingBranch = emptyBoard().toMutableList().apply {
            this[0] = CandyCircuitTile(CandyCircuitTileShape.Branch, rotation = 1)
            this[1] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 2)
            this[4] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 0)
            this[5] = CandyCircuitTile(CandyCircuitTileShape.Curve, rotation = 3)
        }

        assertTrue(CandyCircuitRules.closedComponents(twoByTwoWithDanglingBranch).isEmpty())
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
        assertTrue(restarted.scoredFingerprints.isEmpty())
        assertFalse(restarted.hasNewBest)
        assertNotEquals(played, restarted)
    }

    @Test
    fun `only a strictly higher score marks a new personal best`() {
        val tied = CandyCircuitRules.rotate(CandyCircuitGameState(bestScore = 400), tileIndex = 5)
        val beaten = CandyCircuitRules.rotate(CandyCircuitGameState(bestScore = 300), tileIndex = 5)
        val followingMove = CandyCircuitRules.rotate(beaten, tileIndex = 0)

        assertFalse(tied.hasNewBest)
        assertTrue(beaten.hasNewBest)
        assertFalse(followingMove.hasNewBest)
    }

    @Test
    fun `closed component fingerprint is stable for same board`() {
        val closed = CandyCircuitRules.rotate(CandyCircuitGameState(), tileIndex = 5)

        assertEquals(
            CandyCircuitRules.closedComponents(closed.tiles),
            CandyCircuitRules.closedComponents(closed.tiles.toList()),
        )
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

    private companion object {
        val North = CandyCircuitDirection.North
        val East = CandyCircuitDirection.East
        val South = CandyCircuitDirection.South
        val West = CandyCircuitDirection.West
    }
}
