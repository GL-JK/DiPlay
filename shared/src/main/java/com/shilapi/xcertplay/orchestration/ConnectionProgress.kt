package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.R

/**
 * Maps the controller's fine-grained [CarPlayStatus] onto a small, user-visible progress model.
 *
 * The waiting screen used to show one sentence that changed as the connection advanced, so a slow
 * step (a hotspot that takes five seconds, a Bluetooth page that does not answer) looked like the
 * app had stalled. This turns the same status stream into a step number and a percentage, and names
 * each phase so the driver can see which part is taking time.
 *
 * Pure data: no context, no resources beyond string ids, so it stays unit-testable.
 */
internal object ConnectionProgress {
    const val STEP_COUNT = 7

    /** 1-based index of the step that is currently running. */
    fun step(status: CarPlayStatus): Int = when (status) {
        CarPlayStatus.DiscoveringMfi,
        CarPlayStatus.WaitingForMfi,
        CarPlayStatus.RequestingMfiPermission,
        CarPlayStatus.MfiReady -> 1

        CarPlayStatus.StartingHotspot,
        is CarPlayStatus.HotspotReady -> 2

        CarPlayStatus.WaitingForPairedIphone -> 3

        CarPlayStatus.ConnectingBluetooth -> 4

        CarPlayStatus.AttachingNetwork,
        CarPlayStatus.ConnectingControl -> 5

        CarPlayStatus.RunningWireless,
        CarPlayStatus.DiscoveringIphone,
        CarPlayStatus.WaitingForIphone,
        CarPlayStatus.RequestingIphonePermission,
        CarPlayStatus.WaitingForReenumeration,
        CarPlayStatus.SelectingConfiguration,
        CarPlayStatus.OpeningDataPaths,
        CarPlayStatus.Pairing -> 6

        CarPlayStatus.WirelessActive,
        CarPlayStatus.WirelessActiveFallback,
        CarPlayStatus.RunningControl -> 7

        CarPlayStatus.ControlEnded -> 6
        is CarPlayStatus.Failed -> 1
    }

    /**
     * Progress in tenths of a percent, so callers keep integer maths. A step that has just started is
     * at its own boundary; the bar therefore never runs backwards between statuses of one phase.
     */
    fun permille(status: CarPlayStatus): Int {
        val step = step(status)
        val base = ((step - 1) * 1000) / STEP_COUNT
        val span = 1000 / STEP_COUNT
        // Within a phase, completion of the phase itself is the milestone (a phase has no sub-events
        // exposed), except for the active phase which sits just past its boundary while it runs.
        val within = when {
            isTerminal(status) -> span
            else -> span / 4
        }
        return (base + within).coerceIn(0, 1000)
    }

    /** True once CarPlay is up; the caller hides the waiting screen at this point. */
    fun isConnected(status: CarPlayStatus): Boolean = when (status) {
        CarPlayStatus.WirelessActive,
        CarPlayStatus.WirelessActiveFallback,
        CarPlayStatus.RunningControl -> true
        else -> false
    }

    fun isTerminal(status: CarPlayStatus): Boolean = isConnected(status) || status is CarPlayStatus.Failed

    /** Resource ids for the seven visible steps, in order. */
    val stepTitles: List<Int> = listOf(
        R.string.conn_step_mfi,
        R.string.conn_step_hotspot,
        R.string.conn_step_wait_iphone,
        R.string.conn_step_bluetooth,
        R.string.conn_step_iap2,
        R.string.conn_step_airplay,
        R.string.conn_step_session,
    )

    /** Resource ids for each step's short trailing detail, in order. */
    val stepDetails: List<Int> = listOf(
        R.string.conn_detail_mfi,
        R.string.conn_detail_hotspot,
        R.string.conn_detail_wait_iphone,
        R.string.conn_detail_bluetooth,
        R.string.conn_detail_iap2,
        R.string.conn_detail_airplay,
        R.string.conn_detail_session,
    )
}
