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
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.ConnectionProgress
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The seven-step connecting panel: a percentage, a progress bar, an elapsed clock and one row per
 * phase. It owns its views so the host activity only has to build it, feed it statuses, and colour
 * it. Replaces the single sentence that previously described every phase identically.
 */
internal class ConnectionProgressPanel(
    private val context: Context,
    private val density: Float,
) {
    private val percentView = TextView(context).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        text = context.getString(R.string.conn_progress_percent, 0)
    }

    private val barFill = View(context)
    private val counterView = TextView(context).apply {
        text = context.getString(R.string.conn_step_counter, 1, ConnectionProgress.STEP_COUNT)
    }
    private val elapsedView = TextView(context).apply {
        text = context.getString(R.string.conn_elapsed, "0.0")
    }

    private data class StepRow(val badge: TextView, val label: TextView, val detail: TextView, val container: LinearLayout)

    private val steps: List<StepRow> = ConnectionProgress.stepTitles.indices.map { index ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val badge = TextView(context).apply {
            text = (index + 1).toString()
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(24), dp(24)))
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(context).apply { text = context.getString(ConnectionProgress.stepTitles[index]) }
        val detail = TextView(context).apply {
            text = context.getString(ConnectionProgress.stepDetails[index])
            visibility = View.GONE
        }
        column.addView(label)
        column.addView(detail)
        row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(13)
        })
        StepRow(badge, label, detail, row)
    }

    /** The column that the host adds to its waiting panel. */
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(percentView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val track = FrameLayout(context)
        val barHeight = dp(9)
        track.addView(barFill, FrameLayout.LayoutParams(0, barHeight))
        track.background = GradientDrawable().apply {
            setColor(TRACK_COLOR)
            cornerRadius = barHeight / 2f
        }
        barFill.background = GradientDrawable().apply {
            setColor(ACCENT_COLOR)
            cornerRadius = barHeight / 2f
        }
        addView(track, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, barHeight).apply { topMargin = dp(6) })
        val meta = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        meta.addView(counterView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        meta.addView(elapsedView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(meta, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            steps.forEach { addView(it.container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
    }

    private val barTrack = view.getChildAt(1) as FrameLayout
    private var startedAtMillis = 0L
    private var lastStep = 0
    private var ticker: Runnable? = null

    /** Restarts the elapsed clock. Called when a fresh connection attempt begins. */
    fun begin(handler: android.os.Handler) {
        startedAtMillis = System.currentTimeMillis()
        lastStep = 0
        stop(handler)
        val runnable = object : Runnable {
            override fun run() {
                if (startedAtMillis == 0L) return
                val seconds = (System.currentTimeMillis() - startedAtMillis) / 1000.0
                elapsedView.text = context.getString(
                    R.string.conn_elapsed, String.format(Locale.US, "%.1f", seconds))
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

    /** Applies a controller status to the bar and the step list. */
    fun update(status: CarPlayStatus) {
        val step = ConnectionProgress.step(status)
        val permille = ConnectionProgress.permille(status)
        lastStep = step
        percentView.text = context.getString(R.string.conn_progress_percent, (permille / 10f).roundToInt())
        counterView.text = context.getString(R.string.conn_step_counter, step, ConnectionProgress.STEP_COUNT)
        val fraction = permille / 1000f
        barTrack.post {
            val width = barTrack.width
            barFill.layoutParams = (barFill.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = (width * fraction).roundToInt()
            }
            barFill.requestLayout()
        }
        steps.forEachIndexed { index, row ->
            val number = index + 1
            val done = number < step
            val active = number == step
            row.container.setBackgroundColor(if (active) ACTIVE_ROW_COLOR else Color.TRANSPARENT)
            row.badge.text = if (done) "✓" else number.toString()
            row.badge.background = GradientDrawable().apply {
                setColor(when {
                    done -> READY_COLOR
                    active -> ACCENT_COLOR
                    else -> WAIT_COLOR
                })
                cornerRadius = dp(12).toFloat()
            }
            row.badge.setTextColor(when {
                done -> Color.parseColor("#062B12")
                active -> Color.WHITE
                else -> Color.parseColor("#6B7280")
            })
            row.label.alpha = if (active || done) 1f else 0.55f
            row.detail.visibility = if (active) View.VISIBLE else View.GONE
        }
    }

    /** Re-applies colours for the current light/dark appearance. */
    fun paint(dark: Boolean) {
        val text = if (dark) Color.parseColor("#E8EAED") else Color.parseColor("#1F2226")
        val muted = if (dark) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
        percentView.setTextColor(if (dark) ACCENT_COLOR else Color.parseColor("#1A63D8"))
        counterView.setTextColor(muted)
        elapsedView.setTextColor(muted)
        steps.forEach { row ->
            row.label.setTextColor(text)
            row.detail.setTextColor(muted)
        }
        (barTrack.background as? GradientDrawable)?.setColor(if (dark) TRACK_COLOR else Color.parseColor("#E3E6EA"))
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        val ACCENT_COLOR = Color.parseColor("#2E7DFF")
        val READY_COLOR = Color.parseColor("#22C55E")
        val WAIT_COLOR = Color.parseColor("#3A3F47")
        val TRACK_COLOR = Color.parseColor("#24282F")
        val ACTIVE_ROW_COLOR = Color.parseColor("#1D2532")
    }
}
