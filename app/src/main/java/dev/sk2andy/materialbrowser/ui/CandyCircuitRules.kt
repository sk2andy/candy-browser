package dev.sk2andy.materialbrowser.ui

import kotlin.random.Random

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
)

internal data class CandyCircuitGameState(
    val tiles: List<CandyCircuitTile> = CandyCircuitRules.initialBoard(),
    val movesRemaining: Int = CandyCircuitRules.STARTING_MOVES,
    val score: Int = 0,
    val bestScore: Int = 0,
    val combo: Int = 0,
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
        branch(rotation = 1),
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
        random: Random = Random.Default,
    ): CandyCircuitGameState {
        if (state.isGameOver || tileIndex !in state.tiles.indices || state.tiles.size != BOARD_SIZE) {
            return state
        }

        val rotatedTile = state.tiles[tileIndex].rotateClockwise()
        val rotatedTiles = state.tiles.toMutableList().apply {
            this[tileIndex] = rotatedTile
        }
        val closedComponents = closedComponents(rotatedTiles)
        val nextCombo = if (closedComponents.isEmpty()) 0 else state.combo + 1
        val pointsGained = closedComponents.sumOf { component -> component.tileIndices.size * POINTS_PER_TILE } *
            nextCombo
        val nextScore = state.score + pointsGained
        val movesGained = closedComponents.size * MOVES_PER_CLOSED_COMPONENT
        val refilledTiles = refillClosedComponents(
            tiles = rotatedTiles,
            components = closedComponents,
            random = random,
        )

        return state.copy(
            tiles = refilledTiles,
            movesRemaining = state.movesRemaining - 1 + movesGained,
            score = nextScore,
            bestScore = maxOf(state.bestScore, nextScore),
            combo = nextCombo,
            lastRotatedIndex = tileIndex,
            lastPointsGained = pointsGained,
            lastMovesGained = movesGained,
            lastClosedTileIndices = closedComponents.flatMapTo(linkedSetOf()) { it.tileIndices },
            hasNewBest = nextScore > state.bestScore,
        )
    }

    fun restart(state: CandyCircuitGameState): CandyCircuitGameState =
        initialState(bestScore = state.bestScore)

    fun closedComponents(tiles: List<CandyCircuitTile>): List<CandyCircuitClosedComponent> {
        if (tiles.size != BOARD_SIZE) return emptyList()

        val neighbors = tiles.indices.map { index -> reciprocalNeighbors(tiles, index).toSet() }
        val bridgeEdges = bridgeEdges(neighbors)
        val visited = mutableSetOf<Int>()
        return buildList {
            tiles.indices.forEach { startIndex ->
                if (startIndex in visited) return@forEach
                if (cycleNeighbors(startIndex, neighbors, bridgeEdges).isEmpty()) return@forEach

                val component = connectedCycleComponent(
                    neighbors = neighbors,
                    bridgeEdges = bridgeEdges,
                    startIndex = startIndex,
                )
                visited += component
                if (component.size < MINIMUM_CLOSED_TILE_COUNT) return@forEach

                add(
                    CandyCircuitClosedComponent(
                        tileIndices = component.sorted().toSet(),
                    ),
                )
            }
        }
    }

    /**
     * A scored loop is removed in the same reducer step that awards it. Every participating tile
     * receives a different random shape or orientation, and the resulting board starts loop-free.
     */
    private fun refillClosedComponents(
        tiles: List<CandyCircuitTile>,
        components: List<CandyCircuitClosedComponent>,
        random: Random,
    ): List<CandyCircuitTile> {
        if (components.isEmpty()) return tiles

        val replacementIndices = components
            .flatMapTo(linkedSetOf()) { it.tileIndices }
            .sorted()
        repeat(MAX_RANDOM_REFILL_ATTEMPTS) {
            val refilledTiles = tiles.toMutableList().apply {
                replacementIndices.forEach { index ->
                    val currentTile = tiles[index].normalized()
                    val options = TILE_OPTIONS.filterNot { tile -> tile == currentTile }
                    this[index] = options[random.nextInt(options.size)]
                }
            }
            if (closedComponents(refilledTiles).isEmpty()) return refilledTiles
        }

        return tiles.mapIndexed { index, tile ->
            val randomStraight = straight(rotation = random.nextInt(STRAIGHT_ROTATION_COUNT))
            if (index in replacementIndices && randomStraight == tile.normalized()) {
                straight(rotation = randomStraight.rotation + 1)
            } else {
                randomStraight
            }
        }
    }

    private fun connectedCycleComponent(
        neighbors: List<Set<Int>>,
        bridgeEdges: Set<Pair<Int, Int>>,
        startIndex: Int,
    ): Set<Int> {
        val pending = ArrayDeque<Int>().apply { add(startIndex) }
        val component = linkedSetOf<Int>()
        while (pending.isNotEmpty()) {
            val index = pending.removeFirst()
            if (!component.add(index)) continue
            cycleNeighbors(index, neighbors, bridgeEdges).forEach { neighbor ->
                if (neighbor !in component) pending.add(neighbor)
            }
        }
        return component
    }

    private fun bridgeEdges(neighbors: List<Set<Int>>): Set<Pair<Int, Int>> {
        val discoveryOrder = IntArray(neighbors.size) { UNVISITED }
        val lowestReachableOrder = IntArray(neighbors.size) { UNVISITED }
        val bridges = linkedSetOf<Pair<Int, Int>>()
        var nextOrder = 0

        fun visit(index: Int, parent: Int?) {
            discoveryOrder[index] = nextOrder
            lowestReachableOrder[index] = nextOrder
            nextOrder += 1

            neighbors[index].forEach { neighbor ->
                if (discoveryOrder[neighbor] == UNVISITED) {
                    visit(neighbor, index)
                    lowestReachableOrder[index] = minOf(
                        lowestReachableOrder[index],
                        lowestReachableOrder[neighbor],
                    )
                    if (lowestReachableOrder[neighbor] > discoveryOrder[index]) {
                        bridges += edge(index, neighbor)
                    }
                } else if (neighbor != parent) {
                    lowestReachableOrder[index] = minOf(
                        lowestReachableOrder[index],
                        discoveryOrder[neighbor],
                    )
                }
            }
        }

        neighbors.indices.forEach { index ->
            if (discoveryOrder[index] == UNVISITED) visit(index, parent = null)
        }
        return bridges
    }

    private fun cycleNeighbors(
        index: Int,
        neighbors: List<Set<Int>>,
        bridgeEdges: Set<Pair<Int, Int>>,
    ): List<Int> = neighbors[index].filterNot { neighbor ->
        edge(index, neighbor) in bridgeEdges
    }

    private fun edge(first: Int, second: Int): Pair<Int, Int> =
        minOf(first, second) to maxOf(first, second)

    private fun reciprocalNeighbors(
        tiles: List<CandyCircuitTile>,
        tileIndex: Int,
    ): List<Int> = connections(tiles[tileIndex]).mapNotNull { direction ->
        neighborIndex(tileIndex, direction)?.takeIf { neighbor ->
            direction.opposite in connections(tiles[neighbor])
        }
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

    private fun CandyCircuitTile.normalized(): CandyCircuitTile = copy(
        rotation = Math.floorMod(rotation, rotationCount(shape)),
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
    private const val MAX_RANDOM_REFILL_ATTEMPTS = 64
    private const val STRAIGHT_ROTATION_COUNT = 2
    private const val UNVISITED = -1

    private val TILE_OPTIONS = buildList {
        repeat(2) { rotation -> add(straight(rotation)) }
        repeat(4) { rotation -> add(curve(rotation)) }
        repeat(4) { rotation -> add(branch(rotation)) }
    }
}
