package dev.horizon.clock

/** How often the layout moves to its next burn-in offset. */
const val BURN_IN_STEP_MINUTES = 2

// A loop of small offsets (dp) around the origin; the whole layout walks through it
// so no bright pixel stays lit in exactly one place for long.
private val shiftPattern = listOf(
    0 to 0, 3 to 1, 1 to 3, -2 to 2, -3 to -1, -1 to -3, 2 to -2,
)

/** The (x, y) shift in dp for a given minute since the epoch. */
fun burnInShift(epochMinute: Long): Pair<Int, Int> =
    shiftPattern[Math.floorMod(epochMinute / BURN_IN_STEP_MINUTES, shiftPattern.size.toLong()).toInt()]
