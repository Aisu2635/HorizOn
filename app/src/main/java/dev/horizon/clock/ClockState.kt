package dev.horizon.clock

/** Formats the big clock digits, e.g. "9:41" (12h) or "21:41" (24h). */
fun formatClockTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    require(hour in 0..23) { "hour out of range: $hour" }
    require(minute in 0..59) { "minute out of range: $minute" }
    val mm = minute.toString().padStart(2, '0')
    if (is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12:$mm"
}
