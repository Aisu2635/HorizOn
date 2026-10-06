package dev.horizon.nav

import java.util.Locale

/** Navigation apps whose turn-by-turn notification we mirror. Nothing from any other app is read. */
internal val NAV_PACKAGES = setOf(
    "com.google.android.apps.maps",
    "com.google.android.apps.navlite", // Google Maps Go
)

/** The parts of a navigation notification we read, as plain values so parsing is testable on the JVM. */
data class NavFields(
    val packageName: String,
    val category: String?,
    val isOngoing: Boolean,
    /** `100 m · Turn left toward Street A`, or a bare instruction such as `Head north`. */
    val title: String?,
    /** `Arrive 7:53 pm` */
    val subText: String?,
    /** The distance to the next turn on its own (`100 m`), empty when there is none. */
    val shortCriticalText: String?,
    /** Metres travelled along the route (Android 16 ProgressStyle). */
    val progress: Int,
    /** Route length in metres, or 0 before the route is known. */
    val progressMax: Int,
)

/** The next maneuver, parsed from a navigation notification. Strings are kept as the app wrote them. */
data class NavInfo(
    /** `100 m`, or null when the app shows a bare instruction. */
    val distanceToTurn: String?,
    /** `Turn left` */
    val instruction: String,
    /** `toward Street A`, or null when the instruction names no road. */
    val road: String?,
    /** The whole arrival line, e.g. `Arrive 7:53 pm`. */
    val arrival: String?,
    /** Just the time from [arrival] (`7:53 pm`) when it could be picked out. */
    val etaTime: String?,
    /** Distance left on the route, or null before the route is known. */
    val remainingMeters: Int?,
    /** 0..1 along the route, or null before the route is known. */
    val tripProgress: Float?,
    /** Whether the app shows distances in feet and miles. */
    val imperial: Boolean,
    /** The route is still being set up ("Starting navigation…"). */
    val starting: Boolean,
)

object NavParser {
    const val CATEGORY_NAVIGATION = "navigation"

    /** Splits `100 m · Turn left` into its distance and instruction. */
    private val TITLE_SEPARATOR = Regex("""\s+[·•]\s+""")

    /** Words that start the road part of an English instruction, e.g. "Turn left | toward Street A". */
    private val ROAD_CONNECTORS = listOf(" toward ", " towards ", " onto ", " on ", " at ")

    private val IMPERIAL_UNIT = Regex("""(ft|mi|yd)\.?$""", RegexOption.IGNORE_CASE)

    private const val ARRIVE_PREFIX = "Arrive "

    /**
     * Whether a notification is a navigation app's turn-by-turn notification. Checked before any
     * of its text is read. The channel is not used: Maps switches channels between posts.
     */
    fun isNavigation(packageName: String, category: String?, isOngoing: Boolean): Boolean =
        packageName in NAV_PACKAGES && category == CATEGORY_NAVIGATION && isOngoing

    /** Parses [fields], or returns null when they are not a turn-by-turn notification. */
    fun parse(fields: NavFields): NavInfo? {
        if (!isNavigation(fields.packageName, fields.category, fields.isOngoing)) return null
        val title = fields.title?.trim().orEmpty()
        if (title.isEmpty()) return null

        val parts = title.split(TITLE_SEPARATOR, limit = 2)
        val titleDistance = if (parts.size == 2) parts[0].trim().takeIf { it.isNotEmpty() } else null
        val maneuver = if (parts.size == 2) parts[1].trim() else title
        val distance = fields.shortCriticalText?.trim()?.takeIf { it.isNotEmpty() } ?: titleDistance
        val (instruction, road) = splitRoad(maneuver)

        val arrival = fields.subText?.trim()?.takeIf { it.isNotEmpty() }
        val etaTime = arrival?.takeIf { it.startsWith(ARRIVE_PREFIX) }?.removePrefix(ARRIVE_PREFIX)?.trim()

        val hasRoute = fields.progressMax > 0
        val travelled = fields.progress.coerceIn(0, fields.progressMax.coerceAtLeast(0))
        return NavInfo(
            distanceToTurn = distance,
            instruction = instruction,
            road = road,
            arrival = arrival,
            etaTime = etaTime,
            remainingMeters = if (hasRoute) fields.progressMax - travelled else null,
            tripProgress = if (hasRoute) travelled.toFloat() / fields.progressMax else null,
            imperial = distance?.let { IMPERIAL_UNIT.containsMatchIn(it.normalizeSpaces()) } ?: false,
            starting = !hasRoute,
        )
    }

    /** "Turn left toward Street A" → ("Turn left", "toward Street A"). Other languages stay whole. */
    private fun splitRoad(maneuver: String): Pair<String, String?> {
        val cut = ROAD_CONNECTORS
            .map { maneuver.indexOf(it) }
            .filter { it > 0 }
            .minOrNull() ?: return maneuver to null
        return maneuver.substring(0, cut).trim() to maneuver.substring(cut).trim()
    }
}

/**
 * Formats a distance left on the route in the same unit system the navigation app uses:
 * `850 m`, `4.2 km`, `82 km`; or `350 ft`, `2.9 mi`, `51 mi`.
 */
fun formatRouteDistance(meters: Int, imperial: Boolean, locale: Locale = Locale.getDefault()): String {
    val m = meters.coerceAtLeast(0)
    return if (imperial) {
        val miles = m / 1609.344
        when {
            miles < 0.1 -> "${(m * 3.28084 / 50).toInt() * 50} ft"
            miles < 100 -> String.format(locale, "%.1f mi", miles)
            else -> String.format(locale, "%.0f mi", miles)
        }
    } else {
        when {
            m < 1000 -> "${m / 10 * 10} m"
            m < 100_000 -> String.format(locale, "%.1f km", m / 1000.0)
            else -> String.format(locale, "%.0f km", m / 1000.0)
        }
    }
}

private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')
