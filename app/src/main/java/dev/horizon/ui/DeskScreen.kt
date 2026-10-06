package dev.horizon.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.horizon.clock.burnInShift
import dev.horizon.clock.formatClockDate
import dev.horizon.clock.minuteTicks
import dev.horizon.device.BatteryStatus
import dev.horizon.device.batteryStatus
import dev.horizon.device.hasBluetoothPermission
import dev.horizon.device.headsetStatus
import dev.horizon.device.openOtherApp
import dev.horizon.media.MediaRepository
import dev.horizon.media.NowPlaying
import dev.horizon.nav.NavRepository
import dev.horizon.nav.NavState
import dev.horizon.settings.ClockFace
import dev.horizon.settings.SettingsRepository
import dev.horizon.ui.faces.ClockFaceContent
import dev.horizon.ui.faces.ClockTime
import dev.horizon.ui.nav.DirectionsPill
import dev.horizon.ui.nav.DirectionsPrompt
import dev.horizon.ui.nav.NavPanel
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import dev.horizon.ui.theme.PillSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** How long the controls stay up after the last tap. */
private const val CONTROLS_TIMEOUT_MS = 4_000L

/** Horizontal drag distance that switches between the player and the full clock. */
private const val SWIPE_THRESHOLD_PX = 120f

/** How long the "headphones connected" banner stays up. */
private const val HEADSET_BANNER_MS = 4_000L

/** Which arrangement the standby screen shows. */
private enum class DeskLayout {
    /** Clock (or directions) on the left, player card on the right. */
    Split,

    /** Navigating with nothing playing: directions on the left, the clock on the right. */
    Directions,

    /** The full-screen clock. */
    Clock,
}

/**
 * The standby screen.
 * - Music available (or access not yet granted): clock on the left, player card on the right.
 *   While Google Maps is navigating (and directions are on), directions replace the clock.
 * - Navigating with no music: directions on the left, the clock on the right.
 * - Otherwise, or after a swipe: the full-screen clock, with Now Playing and directions pills.
 * Tap anywhere for the controls (clock style and Close); swipe sideways to switch layouts.
 */
@Composable
fun DeskScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val settings = remember(context) { SettingsRepository(context) }
    val media = remember(context) { MediaRepository(context) }
    val scope = rememberCoroutineScope()

    val now by remember { minuteTicks() }.collectAsStateWithLifecycle(initialValue = ZonedDateTime.now())
    val battery by remember(context) { context.batteryStatus() }.collectAsStateWithLifecycle(initialValue = null)
    val face by settings.clockFace.collectAsStateWithLifecycle(initialValue = null)
    val promptDismissed by settings.musicPromptDismissed.collectAsStateWithLifecycle(initialValue = null)

    // Access can change while we're in Settings, so re-check every time we come back.
    var hasAccess by remember { mutableStateOf(media.hasAccess()) }
    LifecycleResumeEffect(Unit) {
        hasAccess = media.hasAccess()
        onPauseOrDispose { }
    }
    val nowPlaying by remember(hasAccess) { if (hasAccess) media.nowPlaying else flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)

    // Directions mirrored from Google Maps; the listener only fills this while the setting is on.
    val showNavigation by settings.showNavigation.collectAsStateWithLifecycle(initialValue = false)
    val navState by NavRepository.state.collectAsStateWithLifecycle()
    val nav = navState.takeIf { hasAccess && showNavigation }
    // Offer directions once when Maps is navigating and the user hasn't chosen yet.
    val mapsNavigating by NavRepository.navigating.collectAsStateWithLifecycle()
    val navPromptDismissed by settings.navPromptDismissed.collectAsStateWithLifecycle(initialValue = true)
    val showNavPrompt = hasAccess && mapsNavigating && !showNavigation && !navPromptDismissed

    // Bluetooth headphones: detected without permission; name and battery need "Nearby devices".
    var bluetoothGranted by remember { mutableStateOf(context.hasBluetoothPermission()) }
    var bluetoothDeclined by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        bluetoothGranted = context.hasBluetoothPermission()
        onPauseOrDispose { }
    }
    val requestBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothGranted = granted
        if (!granted) bluetoothDeclined = true
    }
    val headset by remember(bluetoothGranted) { context.headsetStatus() }.collectAsStateWithLifecycle(initialValue = null)
    val headsetSlot: @Composable () -> Unit = {
        val current = headset
        when {
            current == null -> Unit
            bluetoothGranted || bluetoothDeclined -> HeadsetBadge(current)
            else -> HeadsetPermissionChip(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            })
        }
    }
    // A larger banner for a few seconds when headphones connect while we're on screen. The first
    // value after (re)starting is what was already connected, so it never shows just for opening the app.
    var bannerVisible by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(bluetoothGranted) {
        if (!bluetoothGranted) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Track connected/disconnected only: the name can change right after connecting
            // (generic "Headphones" until the Bluetooth profile answers), which isn't a new connection.
            context.headsetStatus()
                .map { it != null }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { connected ->
                    bannerVisible = connected
                    if (connected) {
                        delay(HEADSET_BANNER_MS)
                        bannerVisible = false
                    }
                }
        }
    }

    val locale = LocalLocale.current.platformLocale
    val time = ClockTime(now.hour, now.minute, DateFormat.is24HourFormat(context))
    val date = formatClockDate(now.toLocalDate(), locale)
    // The clock shrinks into the date line while the directions take its place.
    val dateAndTime = "$date • ${DateFormat.getTimeFormat(context).format(Date.from(now.toInstant())).uppercase(locale)}"

    val epochMinute = TimeUnit.SECONDS.toMinutes(now.toEpochSecond())
    val (shiftX, shiftY) = burnInShift(epochMinute)

    val showAccessCard = !hasAccess && promptDismissed == false
    val cardAvailable = nowPlaying != null || showAccessCard
    // The user's swipe choice; reset whenever music or directions show up so they appear.
    var preferFullClock by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(nowPlaying != null) { if (nowPlaying != null) preferFullClock = false }
    LaunchedEffect(nav != null) { if (nav != null) preferFullClock = false }
    val canSwitch = cardAvailable || nav != null
    val layout = when {
        preferFullClock -> DeskLayout.Clock
        cardAvailable -> DeskLayout.Split
        nav != null -> DeskLayout.Directions
        else -> DeskLayout.Clock
    }

    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    // Bumped on every interaction with the controls, restarting the hide timer.
    var controlsTouch by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, controlsTouch) {
        if (controlsVisible) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    fun openAccessSettings() {
        try {
            context.startActivity(media.accessSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            context.startActivity(media.accessListSettingsIntent())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(canSwitch) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { _, amount -> dragged += amount },
                    onDragEnd = {
                        if (canSwitch && abs(dragged) > SWIPE_THRESHOLD_PX) preferFullClock = !preferFullClock
                    },
                )
            }
            .clickable(
                interactionSource = null,
                indication = null,
                onClickLabel = if (controlsVisible) "Hide controls" else "Show controls",
            ) { controlsVisible = !controlsVisible },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .offset(shiftX.dp, shiftY.dp),
        ) {
            // Wait for the stored face before drawing, so the wrong one never flashes up.
            val currentFace = face ?: return@Box
            // Sits in the layout under the clock, so it never covers the music card.
            val prompt: @Composable () -> Unit = {
                AnimatedVisibility(visible = showNavPrompt, enter = fadeIn(), exit = fadeOut()) {
                    DirectionsPrompt(
                        onShow = { scope.launch { settings.setShowNavigation(true) } },
                        onNotNow = { scope.launch { settings.setNavPromptDismissed(true) } },
                        modifier = Modifier.widthIn(max = 460.dp).padding(bottom = 16.dp),
                    )
                }
            }
            val directions: (@Composable (NavState, String) -> Unit) = { current, header ->
                NavPanel(
                    nav = current,
                    header = header,
                    battery = battery,
                    headsetSlot = headsetSlot,
                    onTap = { controlsVisible = !controlsVisible },
                    onOpenApp = { activity?.openOtherApp(current.packageName, current.openIntent) },
                )
            }
            AnimatedContent(
                targetState = layout,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "layout",
            ) { target ->
                when (target) {
                    DeskLayout.Split -> {
                        SplitLayout(currentFace, time, date, battery, nav, { directions(it, dateAndTime) }, prompt, headsetSlot) {
                            val playing = nowPlaying
                            if (playing != null) {
                                PlayerCard(
                                    nowPlaying = playing,
                                    onPlayPause = media::playPause,
                                    onPrevious = media::skipPrevious,
                                    onNext = media::skipNext,
                                    onTap = { controlsVisible = !controlsVisible },
                                    onOpenApp = { activity?.let(media::openPlayerApp) },
                                    onSeek = media::seekTo,
                                )
                            } else {
                                MusicAccessCard(
                                    onAllow = ::openAccessSettings,
                                    onNotNow = { scope.launch { settings.setMusicPromptDismissed(true) } },
                                )
                            }
                        }
                    }
                    DeskLayout.Directions -> {
                        // Keep showing the last directions while the layout fades out.
                        val current = nav ?: navState
                        // Directions where the clock usually is, and the clock where the music would be.
                        Row(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight()
                                    .padding(start = 40.dp, end = 12.dp, top = 28.dp, bottom = 28.dp),
                            ) {
                                if (current != null) directions(current, date)
                            }
                            Crossfade(
                                currentFace,
                                label = "clockFace",
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight()
                                    .padding(top = 20.dp, bottom = 20.dp, end = 24.dp),
                            ) {
                                ClockFaceContent(it, time)
                            }
                        }
                    }
                    DeskLayout.Clock -> {
                        FullLayout(
                            currentFace, time, date, battery, nowPlaying, nav, prompt, headsetSlot,
                            onShowPlayer = { preferFullClock = false },
                            onShowDirections = { preferFullClock = false },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = bannerVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
        ) {
            headset?.let { HeadsetConnectedBanner(it) }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            ControlsBar(
                selected = face ?: ClockFace.Default,
                onSelect = {
                    controlsTouch++
                    scope.launch { settings.setClockFace(it) }
                },
                // Offer a way back to the music prompt after "Not now".
                onShowMusic = if (!hasAccess && promptDismissed == true) {
                    {
                        controlsTouch++
                        preferFullClock = false
                        scope.launch { settings.setMusicPromptDismissed(false) }
                    }
                } else {
                    null
                },
                // Directions need notification access, like music.
                directionsOn = if (hasAccess) showNavigation else null,
                onToggleDirections = {
                    controlsTouch++
                    scope.launch { settings.setShowNavigation(!showNavigation) }
                },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun SplitLayout(
    face: ClockFace,
    time: ClockTime,
    date: String,
    battery: BatteryStatus?,
    nav: NavState?,
    directions: @Composable (NavState) -> Unit,
    prompt: @Composable () -> Unit,
    headsetSlot: @Composable () -> Unit,
    card: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        if (nav != null) {
            Box(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .padding(start = 40.dp, end = 12.dp, top = 28.dp, bottom = 28.dp),
            ) {
                directions(nav)
            }
        } else {
            Column(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .padding(start = 40.dp, end = 12.dp, top = 28.dp, bottom = 28.dp),
            ) {
                DateLabel(date)
                Crossfade(face, label = "clockFace", modifier = Modifier.weight(1f)) {
                    ClockFaceContent(it, time)
                }
                prompt()
                // Headphones above the phone battery; the column is too narrow to fit both on one line.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    headsetSlot()
                    if (battery != null) BatteryLabel(battery, spelledOut = true)
                }
            }
        }
        Box(
            Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .padding(top = 20.dp, bottom = 20.dp, end = 24.dp),
        ) {
            card()
        }
    }
}

@Composable
private fun FullLayout(
    face: ClockFace,
    time: ClockTime,
    date: String,
    battery: BatteryStatus?,
    nowPlaying: NowPlaying?,
    nav: NavState?,
    prompt: @Composable () -> Unit,
    headsetSlot: @Composable () -> Unit,
    onShowPlayer: () -> Unit,
    onShowDirections: () -> Unit,
) {
    val hasPills = nowPlaying != null || nav != null
    Column(Modifier.fillMaxSize()) {
        StatusRow(date, battery, accessory = headsetSlot)
        Crossfade(face, label = "clockFace", modifier = Modifier.weight(1f)) {
            ClockFaceContent(it, time, Modifier.padding(bottom = if (hasPills) 8.dp else 40.dp))
        }
        Box(Modifier.align(Alignment.CenterHorizontally)) { prompt() }
        if (hasPills) {
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (nav != null) {
                    DirectionsPill(nav, onClick = onShowDirections, modifier = Modifier.widthIn(max = 360.dp))
                }
                if (nowPlaying != null) {
                    NowPlayingPill(nowPlaying, onClick = onShowPlayer, modifier = Modifier.widthIn(max = 420.dp))
                }
            }
        }
    }
}

@Composable
private fun ControlsBar(
    selected: ClockFace,
    onSelect: (ClockFace) -> Unit,
    onShowMusic: (() -> Unit)?,
    directionsOn: Boolean?,
    onToggleDirections: () -> Unit,
    onClose: () -> Unit,
) {
    val pill = RoundedCornerShape(50)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .background(PillSurface, pill)
                .padding(4.dp)
                .selectableGroup(),
        ) {
            ClockFace.entries.forEach { option ->
                val isSelected = option == selected
                Box(
                    Modifier
                        .background(if (isSelected) Color(0xFF2E2925) else Color.Transparent, pill)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(option.label, color = if (isSelected) Amber else Muted, fontSize = 15.sp)
                }
            }
        }
        if (onShowMusic != null) {
            TextButton(onClick = onShowMusic, shape = pill, modifier = Modifier.background(PillSurface, pill)) {
                Text("♪  Music", color = Paper, fontSize = 15.sp)
            }
        }
        if (directionsOn != null) {
            TextButton(
                onClick = onToggleDirections,
                shape = pill,
                modifier = Modifier
                    .background(PillSurface, pill)
                    .semantics { stateDescription = if (directionsOn) "On" else "Off" },
            ) {
                Text(
                    if (directionsOn) "➤  Directions on" else "➤  Directions off",
                    color = if (directionsOn) Amber else Muted,
                    fontSize = 15.sp,
                )
            }
        }
        TextButton(onClick = onClose, shape = pill, modifier = Modifier.background(PillSurface, pill)) {
            Text("✕  Close", color = Paper, fontSize = 15.sp)
        }
    }
}
