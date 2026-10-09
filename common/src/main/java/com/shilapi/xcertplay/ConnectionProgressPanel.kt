package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.ConnectionProgress
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The compact connecting indicator: one meta line (step counter on the left, percentage and
 * elapsed clock on the right), a thin progress bar, and the name of the phase that is running.
 *
 * An earlier revision listed all seven steps as rows. That read well on a phone but added roughly
 * 300dp to the waiting panel, and the panel is measured at its natural height on a regular screen
 * where it must fit without scrolling (see PreparationLayoutTest). The hosted head units are short,
 * so the block stays compact: it is the same five-line panel as before plus about 48dp.
 */
internal class ConnectionProgressPanel(
    private val context: Context,
    private val density: Float,
) {
    private val counterView = TextView(context).apply {
        text = context.getString(R.string.conn_step_counter, 1, ConnectionProgress.STEP_COUNT)
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val valueView = TextView(context).apply {
        text = context.getString(
            R.string.conn_value, context.getString(R.string.conn_progress_percent, 0), "0.0")
        textSize = 12f
        gravity = Gravity.END
    }

    private val barFill = View(context)

    private val stepView = TextView(context).apply {
        text = context.getString(STEP_TITLES[0])
        textSize = 13f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private val barTrack: FrameLayout = FrameLayout(context).also { track ->
        val barHeight = dp(8)
        track.addView(barFill, FrameLayout.LayoutParams(0, barHeight))
        track.background = GradientDrawable().apply {
            setColor(TRACK_COLOR)
            cornerRadius = barHeight / 2f
        }
        barFill.background = GradientDrawable().apply {
            setColor(ACCENT_COLOR)
            cornerRadius = barHeight / 2f
        }
    }

    /** The column that the host adds to its waiting panel. Exactly 48dp plus margins tall. */
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val meta = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        meta.addView(counterView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        meta.addView(valueView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(meta, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(barTrack, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(6) })
        addView(stepView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        })
    }

    private var startedAtMillis = 0L
    private var ticker: Runnable? = null

    /** Set once the session is up, so the bar stops climbing and the clock stops counting. */
    private var completed = false
    private var lastElapsedText = "0.0"

    /** Restarts the elapsed clock. Called when a fresh connection attempt begins. */
    fun begin(handler: android.os.Handler) {
        startedAtMillis = System.currentTimeMillis()
        completed = false
        lastPermille = 0
        stop(handler)
        val runnable = object : Runnable {
            override fun run() {
                if (startedAtMillis == 0L) return
                val seconds = (System.currentTimeMillis() - startedAtMillis) / 1000.0
                lastElapsedText = String.format(Locale.US, "%.1f", seconds)
                paintValue(lastPermille, lastElapsedText)
                handler.postDelayed(this, 250L)
            }
        }
        ticker = runnable
        handler.post(runnable)
    }

    fun stop(handler: android.os.Handler) {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    /**
     * The session is up: stop the clock and show a full bar. Without this the panel kept counting
     * while waiting for the controller's later WirelessActive* status, which only arrives once the
     * tunneled iAP2 channel (or, on the fallback path, the first rendered frame) is ready.
     */
    fun complete(handler: android.os.Handler) {
        if (completed) return
        completed = true
        stop(handler)
        lastPermille = 1000
        counterView.text = context.getString(R.string.conn_step_counter, ConnectionProgress.STEP_COUNT, ConnectionProgress.STEP_COUNT)
        paintValue(1000, lastElapsedText)
        // Full width, so it is correct even before the track has been measured. The track's own
        // rounded background does the clipping, so a MATCH_PARENT fill reads as a 100% bar.
        barFill.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(8))
        barFill.requestLayout()
        stepView.text = context.getString(R.string.conn_connected)
    }

    private var lastPermille = 0

    private fun paintValue(permille: Int, elapsed: String) {
        val percent = context.getString(R.string.conn_progress_percent, (permille / 10f).roundToInt())
        valueView.text = context.getString(R.string.conn_value, percent, elapsed)
    }

    /** Applies a controller status to the bar, the counter and the phase name. */
    fun update(status: CarPlayStatus) {
        // Already finished: a trailing status from the same attempt must not push the bar back.
        if (completed) return
        val step = ConnectionProgress.step(status).coerceIn(1, ConnectionProgress.STEP_COUNT)
        val permille = ConnectionProgress.permille(status)
        lastPermille = permille
        counterView.text = context.getString(R.string.conn_step_counter, step, ConnectionProgress.STEP_COUNT)
        paintValue(permille, elapsedText())
        val fraction = permille / 1000f
        barTrack.post {
            val width = barTrack.width
            barFill.layoutParams = (barFill.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = (width * fraction).roundToInt()
            }
            barFill.requestLayout()
        }
        val title = context.getString(STEP_TITLES[step - 1])
        val detail = context.getString(STEP_DETAILS[step - 1])
        stepView.text = context.getString(R.string.conn_step_line, title, detail)
        stepView.alpha = if (ConnectionProgress.isConnected(status)) 1f else 0.9f
    }

    private fun elapsedText(): String {
        if (startedAtMillis == 0L) return "0.0"
        return String.format(Locale.US, "%.1f", (System.currentTimeMillis() - startedAtMillis) / 1000.0)
    }

    /** Re-applies colours for the current light/dark appearance. */
    fun paint(dark: Boolean) {
        val text = if (dark) Color.parseColor("#E8EAED") else Color.parseColor("#1F2226")
        val muted = if (dark) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
        counterView.setTextColor(muted)
        valueView.setTextColor(if (dark) ACCENT_COLOR else Color.parseColor("#1A63D8"))
        stepView.setTextColor(text)
        (barTrack.background as? GradientDrawable)?.setColor(
            if (dark) TRACK_COLOR else Color.parseColor("#E3E6EA"))
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        /** Resource ids for the seven steps, in order. Living here (the common module) rather than
         *  in :shared keeps the orchestration layer free of R references. */
        val STEP_TITLES = intArrayOf(
            R.string.conn_step_mfi,
            R.string.conn_step_hotspot,
            R.string.conn_step_wait_iphone,
            R.string.conn_step_bluetooth,
            R.string.conn_step_iap2,
            R.string.conn_step_airplay,
            R.string.conn_step_session,
        )

        /** Resource id per step for the short trailing detail. */
        val STEP_DETAILS = intArrayOf(
            R.string.conn_detail_mfi,
            R.string.conn_detail_hotspot,
            R.string.conn_detail_wait_iphone,
            R.string.conn_detail_bluetooth,
            R.string.conn_detail_iap2,
            R.string.conn_detail_airplay,
            R.string.conn_detail_session,
        )

        val ACCENT_COLOR = Color.parseColor("#2E7DFF")
        val TRACK_COLOR = Color.parseColor("#24282F")
    }
}
