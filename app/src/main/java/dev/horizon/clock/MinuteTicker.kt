package dev.horizon.clock

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZonedDateTime

/**
 * Emits the current time immediately, then once at the start of every minute.
 * Reads the default zone on each tick, so time zone changes show up within a minute.
 */
fun minuteTicks(now: () -> ZonedDateTime = ZonedDateTime::now): Flow<ZonedDateTime> = flow {
    while (true) {
        val time = now()
        emit(time)
        delay(millisUntilNextMinute(time.toInstant().toEpochMilli()))
    }
}
