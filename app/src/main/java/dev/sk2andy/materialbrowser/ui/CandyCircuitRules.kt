package dev.sk2andy.materialbrowser.ui

internal enum class CandyCircuitDirection(
    val rowOffset: Int,
    val columnOffset: Int,
) {
    North(rowOffset = -1, columnOffset = 0),
    East(rowOffset = 0, columnOffset = 1),
    South(rowOffset = 1, columnOffset = 0),
    West(rowOffset = 0, columnOffset = -1),
    ;

    val opposite: CandyCircuitDirection
        get() = entries[(ordinal + 2) % entries.size]
}

internal enum class CandyCircuitTileShape {
    Straight,
    Curve,
    Branch,
}

internal data class CandyCircuitTile(
    val shape: CandyCircuitTileShape,
    val rotation: Int = 0,
)

internal data class CandyCircuitClosedComponent(
    val tileIndices: Set<Int>,
    val fingerprint: String,
)

internal data class CandyCircuitGameState(
    val tiles: List<CandyCircuitTile> = CandyCircuitRules.initialBoard(),
    val movesRemaining: Int = CandyCircuitRules.STARTING_MOVES,
    val score: Int = 0,
    val bestScore: Int = 0,
    val combo: Int = 0,
    val scoredFingerprints: Set<String> = emptySet(),
    val lastRotatedIndex: Int? = null,
    val lastPointsGained: Int = 0,
    val lastMovesGained: Int = 0,
    val lastClosedTileIndices: Set<Int> = emptySet(),
    val hasNewBest: Boolean = false,
) {
    val isGameOver: Boolean
        get() = movesRemaining == 0

    val lastClosedTileCount: Int
        get() = lastClosedTileIndices.size
}

internal object CandyCircuitRules {
    const val ROW_COUNT = 4
    const val COLUMN_COUNT = 4
    const val STARTING_MOVES = 12
    const val MOVES_PER_CLOSED_COMPONENT = 2

    fun initialState(bestScore: Int = 0): CandyCircuitGameState =
        CandyCircuitGameState(bestScore = bestScore.coerceAtLeast(0))

    fun initialBoard(): List<CandyCircuitTile> = listOf(
        curve(rotation = 1),
        curve(rotation = 2),
        straight(rotation = 1),
        curve(rotation = 2),
        curve(rotation = 0),
        curve(rotation = 2),
        straight(rotation = 0),
        straight(rotation = 0),
        curve(rotation = 1),
        branch(rotation = 2),
        curve(rotation = 2),
        straight(rotation = 0),
        curve(rotation = 0),
        branch(rotation = 0),
        curve(rotation = 2),
        curve(rotation = 3),
    )

    fun connections(tile: CandyCircuitTile): Set<CandyCircuitDirection> {
        val turns = Math.floorMod(tile.rotation, rotationCount(tile.shape))
        val baseConnections = when (tile.shape) {
            CandyCircuitTileShape.Straight -> setOf(
                CandyCircuitDirection.North,
                CandyCircuitDirection.South,
            )
            CandyCircuitTileShape.Curve -> setOf(
                CandyCircuitDirection.North,
                CandyCircuitDirection.East,
            )
            CandyCircuitTileShape.Branch -> setOf(
                CandyCircuitDirection.North,
                CandyCircuitDirection.East,
                CandyCircuitDirection.West,
            )
        }
        return baseConnections.mapTo(linkedSetOf()) { direction ->
            CandyCircuitDirection.entries[(direction.ordinal + turns) % DIRECTION_COUNT]
        }
    }

    fun rotate(
        state: CandyCircuitGameState,
        tileIndex: Int,
    ): CandyCircuitGameState {
        if (state.isGameOver || tileIndex !in state.tiles.indices || state.tiles.size != BOARD_SIZE) {
            return state
        }

        val rotatedTile = state.tiles[tileIndex].rotateClockwise()
        val rotatedTiles = state.tiles.toMutableList().apply {
            this[tileIndex] = rotatedTile
        }
        val closedComponents = closedComponents(rotatedTiles)
        val newComponents = closedComponents.filterNot { component ->
            component.fingerprint in state.scoredFingerprints
        }
        val nextCombo = if (newComponents.isEmpty()) 0 else state.combo + 1
        val pointsGained = newComponents.sumOf { component -> component.tileIndices.size * POINTS_PER_TILE } *
            nextCombo
        val nextScore = state.score + pointsGained
        val movesGained = newComponents.size * MOVES_PER_CLOSED_COMPONENT
        val refilledTiles = refillClosedComponents(
            tiles = rotatedTiles,
            components = closedComponents,
        )

        return state.copy(
            tiles = refilledTiles,
            movesRemaining = state.movesRemaining - 1 + movesGained,
            score = nextScore,
            bestScore = maxOf(state.bestScore, nextScore),
            combo = nextCombo,
            scoredFingerprints = state.scoredFingerprints + newComponents.map { it.fingerprint },
            lastRotatedIndex = tileIndex,
            lastPointsGained = pointsGained,
            lastMovesGained = movesGained,
            lastClosedTileIndices = newComponents.flatMapTo(linkedSetOf()) { it.tileIndices },
            hasNewBest = nextScore > state.bestScore,
        )
    }

    fun restart(state: CandyCircuitGameState): CandyCircuitGameState =
        initialState(bestScore = state.bestScore)

    fun closedComponents(tiles: List<CandyCircuitTile>): List<CandyCircuitClosedComponent> {
        if (tiles.size != BOARD_SIZE) return emptyList()

        val visited = mutableSetOf<Int>()
        return buildList {
            tiles.indices.forEach { startIndex ->
                if (startIndex in visited) return@forEach

                val component = connectedComponent(
                    tiles = tiles,
                    startIndex = startIndex,
                )
                visited += component
                if (component.size < MINIMUM_CLOSED_TILE_COUNT) return@forEach
                if (!component.all { index -> hasOnlyReciprocalConnections(tiles, index) }) {
                    return@forEach
                }

                val sortedIndices = component.sorted()
                add(
                    CandyCircuitClosedComponent(
                        tileIndices = sortedIndices.toSet(),
                        fingerprint = sortedIndices.joinToString(separator = "|") { index ->
                            val directions = connections(tiles[index])
                                .sortedBy(CandyCircuitDirection::ordinal)
                                .joinToString(separator = "") { direction -> direction.name.take(1) }
                            "$index:$directions"
                        },
                    ),
                )
            }
        }
    }

    /**
     * A scored loop is removed in the same reducer step that awards it. Refill tiles are fixed by
     * board index, so a round is reproducible and the board cannot continue with that closed loop.
     */
    private fun refillClosedComponents(
        tiles: List<CandyCircuitTile>,
        components: List<CandyCircuitClosedComponent>,
    ): List<CandyCircuitTile> {
        if (components.isEmpty()) return tiles

        val replacementIndices = components.flatMapTo(linkedSetOf()) { it.tileIndices }
        return tiles.mapIndexed { index, tile ->
            if (index in replacementIndices) REFILL_TILES[index] else tile
        }
    }

    private fun connectedComponent(
        tiles: List<CandyCircuitTile>,
        startIndex: Int,
    ): Set<Int> {
        val pending = ArrayDeque<Int>().apply { add(startIndex) }
        val component = linkedSetOf<Int>()
        while (pending.isNotEmpty()) {
            val index = pending.removeFirst()
            if (!component.add(index)) continue
            reciprocalNeighbors(tiles, index).forEach { neighbor ->
                if (neighbor !in component) pending.add(neighbor)
            }
        }
        return component
    }

    private fun reciprocalNeighbors(
        tiles: List<CandyCircuitTile>,
        tileIndex: Int,
    ): List<Int> = connections(tiles[tileIndex]).mapNotNull { direction ->
        neighborIndex(tileIndex, direction)?.takeIf { neighbor ->
            direction.opposite in connections(tiles[neighbor])
        }
    }

    private fun hasOnlyReciprocalConnections(
        tiles: List<CandyCircuitTile>,
        tileIndex: Int,
    ): Boolean = connections(tiles[tileIndex]).all { direction ->
        neighborIndex(tileIndex, direction)?.let { neighbor ->
            direction.opposite in connections(tiles[neighbor])
        } == true
    }

    private fun neighborIndex(
        tileIndex: Int,
        direction: CandyCircuitDirection,
    ): Int? {
        val row = tileIndex / COLUMN_COUNT + direction.rowOffset
        val column = tileIndex % COLUMN_COUNT + direction.columnOffset
        return if (row in 0 until ROW_COUNT && column in 0 until COLUMN_COUNT) {
            row * COLUMN_COUNT + column
        } else {
            null
        }
    }

    private fun CandyCircuitTile.rotateClockwise(): CandyCircuitTile = copy(
        rotation = rotation + 1,
    )

    private fun rotationCount(shape: CandyCircuitTileShape): Int = when (shape) {
        CandyCircuitTileShape.Straight -> 2
        CandyCircuitTileShape.Curve,
        CandyCircuitTileShape.Branch,
        -> 4
    }

    private fun straight(rotation: Int) = CandyCircuitTile(
        shape = CandyCircuitTileShape.Straight,
        rotation = rotation,
    )

    private fun curve(rotation: Int) = CandyCircuitTile(
        shape = CandyCircuitTileShape.Curve,
        rotation = rotation,
    )

    private fun branch(rotation: Int) = CandyCircuitTile(
        shape = CandyCircuitTileShape.Branch,
        rotation = rotation,
    )

    private const val BOARD_SIZE = ROW_COUNT * COLUMN_COUNT
    private const val DIRECTION_COUNT = 4
    private const val MINIMUM_CLOSED_TILE_COUNT = 4
    private const val POINTS_PER_TILE = 100

    private val REFILL_TILES = listOf(
        curve(rotation = 2),
        curve(rotation = 3),
        straight(rotation = 0),
        curve(rotation = 3),
        curve(rotation = 1),
        curve(rotation = 3),
        straight(rotation = 1),
        straight(rotation = 1),
        curve(rotation = 2),
        branch(rotation = 3),
        curve(rotation = 3),
        straight(rotation = 1),
        curve(rotation = 1),
        branch(rotation = 1),
        curve(rotation = 3),
        curve(rotation = 0),
    )
}
