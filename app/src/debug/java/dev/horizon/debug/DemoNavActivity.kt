package dev.horizon.debug

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import dev.horizon.DeskActivity
import dev.horizon.nav.NavInfo
import dev.horizon.nav.NavRepository
import dev.horizon.nav.NavState
import dev.horizon.settings.SettingsRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Debug builds only. Plays a made-up route through the directions panel, so it can be tested
 * and screenshotted without Google Maps or moving: turns change every few seconds and the
 * distance counts down. Turns directions on (notification access is still needed), then opens
 * the desk screen:
 *
 *     adb shell am start -n dev.horizon/.debug.DemoNavActivity
 *     adb shell am start -n dev.horizon/.debug.DemoNavActivity --ez stop true
 *
 * Real Maps notifications, if any, replace the demo as they arrive.
 */
class DemoNavActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("stop", false)) {
            DemoRoute.stop()
        } else {
            DemoRoute.start(applicationContext)
            startActivity(Intent(this, DeskActivity::class.java))
        }
        finish()
    }
}

private object DemoRoute {
    private class Step(val instruction: String, val road: String?, val meters: Int)

    private val steps = listOf(
        Step("Head north", null, 200),
        Step("Turn right", "onto Broadway St", 300),
        Step("Turn left", "toward Main St", 250),
        Step("Keep right", "at the fork", 150),
        Step("Make a U-turn", null, 100),
        Step("Turn right", "onto Park Ave", 200),
    )
    private val routeMeters = steps.sumOf { it.meters }

    /** Metres "driven" per tick: a brisk 25 m/s so a turn passes every few seconds. */
    private const val METERS_PER_TICK = 25
    private const val TICK_MS = 1_000L
    private const val ARRIVED_MS = 5_000L
    private const val KEY = "demo"
    private const val NBSP = ' '

    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private var packageName = ""
    private var timeFormat: java.text.DateFormat? = null
    private var travelled = 0

    private val tick = object : Runnable {
        override fun run() {
            if (travelled >= routeMeters) {
                post(
                    NavInfo(
                        distanceToTurn = null,
                        instruction = "Arrive at destination",
                        road = null,
                        arrival = null,
                        etaTime = null,
                        remainingMeters = 0,
                        tripProgress = 1f,
                        imperial = false,
                        starting = false,
                    ),
                )
                handler.postDelayed({ stop() }, ARRIVED_MS)
                return
            }
            post(infoAt(travelled))
            travelled += METERS_PER_TICK
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start(context: Context) {
        packageName = context.packageName
        timeFormat = DateFormat.getTimeFormat(context)
        stop()
        scope.launch { SettingsRepository(context).setShowNavigation(true) }
        travelled = 0
        handler.post(tick)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        if (NavRepository.state.value?.key == KEY) NavRepository.clear()
    }

    private fun infoAt(position: Int): NavInfo {
        var stepEnd = 0
        val step = steps.first { stepEnd += it.meters; position < stepEnd }
        val toTurn = stepEnd - position
        val remaining = routeMeters - position
        // Pretend 10 m/s for the arrival time.
        val arrival = timeFormat?.format(Date(System.currentTimeMillis() + remaining * 100L)).orEmpty()
        return NavInfo(
            distanceToTurn = "$toTurn${NBSP}m",
            instruction = step.instruction,
            road = step.road,
            arrival = "Arrive $arrival",
            etaTime = arrival,
            remainingMeters = remaining,
            tripProgress = position.toFloat() / routeMeters,
            imperial = false,
            starting = false,
        )
    }

    private fun post(info: NavInfo) {
        NavRepository.post(NavState(KEY, packageName, info, maneuverIcon = null, openIntent = null))
    }
}
