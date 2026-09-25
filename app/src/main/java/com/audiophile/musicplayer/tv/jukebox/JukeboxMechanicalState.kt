package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Full mechanical disc-change state machine for the physical jukebox.
 *
 * These are the states of the machine itself — not UI screens. Every record
 * change walks the whole sequence in order, like the real 45-rpm changers of
 * the 1960s. The [JukeboxMachineFrame] snapshot drives every animated
 * mechanism (changer rail, gripper claws, tonearm, platter motor, magazine).
 */
enum class JukeboxMechanicalState(val label: String) {
    PLAYING("Playing"),
    LIFTING_NEEDLE("Lifting Needle"),
    RETURNING_TONEARM("Returning Tonearm"),
    STOPPING_PLATTER("Stopping Platter"),
    REMOVING_RECORD("Removing Record"),
    RETURNING_RECORD("Returning Record"),
    ROTATING_MAGAZINE("Rotating Magazine"),
    EXTRACTING_RECORD("Extracting Record"),
    MOVING_RECORD_TO_PLATTER("Moving Record To Platter"),
    LOWERING_RECORD("Lowering Record"),
    CLAMPING_RECORD("Clamping Record"),
    SPINNING_UP("Spinning Up"),
    POSITIONING_TONEARM("Positioning Tonearm"),
    LOWERING_NEEDLE("Lowering Needle")
}

/**
 * Animation snapshot of the whole machine at one instant.
 *
 * - [changerX]   0f = selector parked at the magazine, 1f = over the platter.
 * - [discPresence]  0f = platter empty, 1f = record seated on the platter.
 * - [tonearmLifted] 0f = stylus riding the groove, 1f = raised on the cue lever.
 * - [tonearmAngle]  resting 18°, tracking 28°→46° as the needle walks inward,
 *                   fully clear 14° during disc changes.
 * - [platterSpin]  0f..1f motor speed (with real spin-up / spin-down inertia).
 * - [magazineIndex] 0f..1f — the circular magazine cartridge rotation.
 */
@Stable
data class JukeboxMachineFrame(
    val state: JukeboxMechanicalState = JukeboxMechanicalState.PLAYING,
    val phaseProgress: Float = 0f,
    val changerX: Float = 0f,
    val discPresence: Float = 0f,
    val isClamping: Boolean = false,
    val tonearmLifted: Float = 1f,
    val tonearmAngle: Float = 18f,
    val platterSpin: Float = 0f,
    val magazineIndex: Float = 0f
) {
    val isChanging: Boolean get() = state != JukeboxMechanicalState.PLAYING

    /** True when the selector arm is travelling between magazine and platter. */
    val isTransferring: Boolean get() =
        state == JukeboxMechanicalState.REMOVING_RECORD ||
            state == JukeboxMechanicalState.RETURNING_RECORD ||
            state == JukeboxMechanicalState.MOVING_RECORD_TO_PLATTER
}

/**
 * Drives one full mechanical record-change sequence, emitting a fresh
 * [JukeboxMachineFrame] on every animation tick. Pass a snapshot sink (e.g. a
 * `MutableStateFlow`) to consume the frames.
 *
 * If the platter currently holds a record ([discPresent] = true) the machine
 * first unloads it (needle lift → tonearm return → platter stop → remove →
 * return to magazine). Otherwise it starts directly at magazine indexing for a
 * cold load. Every track change ends with the stylus on the groove and the
 * platter at full speed, ready for playback.
 */
suspend fun driveJukeboxChangeSequence(
    discPresent: Boolean,
    emit: (JukeboxMachineFrame) -> Unit
) {
    // --- Unload phase (only when a record is already on the platter) ---
    var frame = JukeboxMachineFrame(state = JukeboxMechanicalState.PLAYING, discPresence = if (discPresent) 1f else 0f)

    if (discPresent) {
        // 1. Hydraulic cue lever lifts the stylus off the groove.
        frame = animatePhase(
            from = frame,
            state = JukeboxMechanicalState.LIFTING_NEEDLE,
            durationMs = 450,
            easing = LinearOutSlowInEasing,
            emit = emit
        ) { p, f -> f.copy(tonearmLifted = p) }

        // 2. The tonearm swings back to its rest cradle.
        frame = animatePhase(
            from = frame,
            state = JukeboxMechanicalState.RETURNING_TONEARM,
            durationMs = 850,
            easing = FastOutSlowInEasing,
            emit = emit
        ) { p, f -> f.copy(tonearmAngle = 46f - (46f - 14f) * easeInsOut(p)) }

        // 3. Platter motor decelerates to a stop.
        frame = animatePhase(
            from = frame,
            state = JukeboxMechanicalState.STOPPING_PLATTER,
            durationMs = 700,
            easing = LinearOutSlowInEasing,
            emit = emit
        ) { p, f -> f.copy(platterSpin = 1f - p) }

        // 4. Selector arm swings from magazine to platter and clamps the record.
        frame = animatePhase(
            from = frame,
            state = JukeboxMechanicalState.REMOVING_RECORD,
            durationMs = 900,
            easing = FastOutSlowInEasing,
            emit = emit
        ) { p, f ->
            f.copy(
                changerX = easeInsOut(p),
                isClamping = p > 0.72f,
                discPresence = if (p > 0.45f) (1f - (p - 0.45f) / 0.55f).coerceIn(0f, 1f) else 1f
            )
        }

        // 5. Selector returns the lifted record to the magazine cartridge.
        frame = animatePhase(
            from = frame,
            state = JukeboxMechanicalState.RETURNING_RECORD,
            durationMs = 800,
            easing = FastOutSlowInEasing,
            emit = emit
        ) { p, f -> f.copy(changerX = 1f - easeInsOut(p), isClamping = p > 0.45f, discPresence = if (p < 0.55f) 0f else 0f) }
    }

    // --- Load phase (always runs) ---
    // 6. Magazine cartridge rotates the next selection into the load position.
    frame = animatePhase(
        from = frame.copy(state = JukeboxMechanicalState.PLAYING),
        state = JukeboxMechanicalState.ROTATING_MAGAZINE,
        durationMs = 650,
        easing = LinearOutSlowInEasing,
        emit = emit
    ) { p, f -> f.copy(magazineIndex = (f.magazineIndex + p) % 1f, changerX = 0f, isClamping = false) }

    // 7. Selector clamps the fresh record and extracts it from its slot.
    frame = animatePhase(
        from = frame,
        state = JukeboxMechanicalState.EXTRACTING_RECORD,
        durationMs = 700,
        easing = LinearOutSlowInEasing,
        emit = emit
    ) { p, f ->
        f.copy(
            changerX = easeInsOut(p).coerceAtMost(0.38f),
            isClamping = p > 0.4f
        )
    }

    // 8. Selector travels across the machine to the platter spindle.
    frame = animatePhase(
        from = frame,
        state = JukeboxMechanicalState.MOVING_RECORD_TO_PLATTER,
        durationMs = 950,
        easing = FastOutSlowInEasing,
        emit = emit
    ) { p, f -> f.copy(changerX = 0.38f + easeInsOut(p) * 0.62f, isClamping = p < 0.8f) }

    // 9. Record lowers onto the spindle.
    frame = animatePhase(
        from = frame,
        state = JukeboxMechanicalState.LOWERING_RECORD,
        durationMs = 650,
        easing = FastOutSlowInEasing,
        emit = emit
    ) { p, f -> f.copy(discPresence = easeInsOut(p)) }

    // 10. Clamp engages; selector releases and retracts to the magazine.
    frame = animatePhase(
        from = frame,
        state = JukeboxMechanicalState.CLAMPING_RECORD,
        durationMs = 700,
        easing = LinearOutSlowInEasing,
        emit = emit
    ) { p, f -> f.copy(isClamping = p < 0.55f, changerX = (1f - easeInsOut(p)).coerceIn(0f, 0.55f), discPresence = 1f) }

    // 11. Platter motor ramps up to 33⅓ RPM.
    frame = animatePhase(
        from = frame.copy(changerX = 0f),
        state = JukeboxMechanicalState.SPINNING_UP,
        durationMs = 700,
        easing = FastOutSlowInEasing,
        emit = emit
    ) { p, f -> f.copy(platterSpin = p) }

    // 12. Tonearm swings inward to the lead-in groove.
    frame = animatePhase(
        from = frame,
        state = JukeboxMechanicalState.POSITIONING_TONEARM,
        durationMs = 850,
        easing = FastOutSlowInEasing,
        emit = emit
    ) { p, f -> f.copy(tonearmAngle = 14f + easeInsOut(p) * 14f) }

    // 13. Cue lever lowers the stylus onto the vinyl — needle drop.
    frame = animatePhase(
        from = frame,
        state = JukeboxMechanicalState.LOWERING_NEEDLE,
        durationMs = 500,
        easing = FastOutSlowInEasing,
        emit = emit
    ) { p, f -> f.copy(tonearmLifted = 1f - p) }

    emit(
        JukeboxMachineFrame(
            state = JukeboxMechanicalState.PLAYING,
            discPresence = 1f,
            tonearmLifted = 0f,
            tonearmAngle = 28f,
            platterSpin = 1f
        )
    )
}

/**
 * Animates one mechanical phase and pushes every tick into [emit] so the
 * marquee label and physical mechanisms stay in sync with the motion (not
 * just the final pose of each phase).
 */
private suspend fun animatePhase(
    from: JukeboxMachineFrame,
    state: JukeboxMechanicalState,
    durationMs: Int,
    easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing,
    emit: (JukeboxMachineFrame) -> Unit,
    transform: (Float, JukeboxMachineFrame) -> JukeboxMachineFrame
): JukeboxMachineFrame {
    val progress = Animatable(0f)
    var latest = from.copy(state = state, phaseProgress = 0f)
    emit(latest)
    progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = durationMs, easing = easing)
    ) {
        latest = transform(value, from).copy(state = state, phaseProgress = value)
        emit(latest)
    }
    latest = latest.copy(state = state, phaseProgress = 1f)
    emit(latest)
    return latest
}

private fun easeInsOut(t: Float): Float =
    t * t * (3f - 2f * t)

/** Convenience: a [MutableStateFlow] that buffers the latest [JukeboxMachineFrame]. */
fun jukeboxMachineFlow(): MutableStateFlow<JukeboxMachineFrame> =
    MutableStateFlow(JukeboxMachineFrame())