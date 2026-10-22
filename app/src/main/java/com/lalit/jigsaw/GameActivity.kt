package com.lalit.jigsaw

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class GameActivity : Activity(), PuzzleView.Listener {

    companion object {
        const val EXTRA_ART = "art"
        const val EXTRA_DIFFICULTY = "difficulty"
    }

    private lateinit var artwork: Artwork
    private lateinit var difficulty: Difficulty
    private lateinit var puzzle: PuzzleView
    private lateinit var timerLabel: TextView
    private lateinit var subtitle: TextView
    private lateinit var progressLine: ProgressLine
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var root: FrameLayout
    private lateinit var guideButton: PillButton

    private var sound: Sound? = null
    private var startAt = 0L
    private var elapsedMs = 0L
    private var running = false
    private var resultShown = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            elapsedMs = SystemClock.elapsedRealtime() - startAt
            timerLabel.text = format(elapsedMs)
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        artwork = Art.byId(intent.getStringExtra(EXTRA_ART))
        difficulty = Difficulty.ofName(intent.getStringExtra(EXTRA_DIFFICULTY))
        setupWindow()

        // Decode before the first layout so the board is ready immediately.
        ArtCache.get(resources, artwork.res, 1200)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        puzzle = PuzzleView(this, artwork, difficulty)
        puzzle.listener = this
        puzzle.soundListener = { snapped ->
            if (snapped) sound?.snap() else sound?.win()
        }
        root.addView(puzzle, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        buildHud()
        sound = Sound(this)

        root.viewTreeObserver.addOnGlobalLayoutListener {
            puzzle.setContentInsets(topBar.bottom.toFloat(), bottomBar.top.toFloat())
        }
    }

    private fun setupWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun buildHud() {
        topBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val fade = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xE6090A12.toInt(), 0x00090A12)
        )
        topBar.background = fade

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
        }

        val back = CircleButton(this).apply { onClick = { finish() } }
        row.addView(back, LinearLayout.LayoutParams(dpi(40f), dpi(40f)))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), 0, dpi(8f), 0)
        }
        val titleView = TextView(this).apply {
            text = artwork.title
            setTextColor(Palette.TEXT)
            textSize = 16.5f
            typeface = Type.title
            includeFontPadding = false
        }
        subtitle = TextView(this).apply {
            text = "${difficulty.shape} · ${difficulty.count} pieces"
            setTextColor(Palette.TEXT_DIM)
            textSize = 11.5f
            typeface = Type.body
            includeFontPadding = false
        }
        titles.addView(titleView)
        titles.addView(subtitle)
        row.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        timerLabel = TextView(this).apply {
            text = "0:00"
            setTextColor(Palette.TEXT)
            textSize = 15f
            typeface = Type.mono
            gravity = Gravity.CENTER
            setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(6f))
            background = roundRectDrawable(Palette.SURFACE_HI.withAlpha(0.75f), dpi(14f).toFloat())
        }
        row.addView(timerLabel)

        topBar.addView(row)

        progressLine = ProgressLine(this)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(3f))
        lp.setMargins(dpi(14f), 0, dpi(14f), dpi(6f))
        topBar.addView(progressLine, lp)

        val topParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        root.addView(topBar, topParams)

        // bottom tools
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(6f))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xE6090A12.toInt(), 0x00090A12)
            )
        }

        guideButton = toolButton("Guide") {
            val on = !puzzle.isGuideOn()
            puzzle.setGuide(on)
            guideButton.setActive(on)
        }
        guideButton.setActive(true)
        val hintButton = toolButton("Hint") { puzzle.hint() }
        val mixButton = toolButton("Mix") { puzzle.shuffle() }

        bottomBar.addView(guideButton, toolParams())
        bottomBar.addView(hintButton, toolParams())
        bottomBar.addView(mixButton, toolParams())

        val bottomParams = FrameLayout.LayoutParams(
            minOf(resources.displayMetrics.widthPixels, dpi(540f)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        root.addView(bottomBar, bottomParams)

        applyInsets()
    }

    private fun toolButton(label: String, action: () -> Unit): PillButton =
        PillButton(this).apply {
            text = label
            onClick = action
        }

    private fun toolParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        0, dpi(44f), 1f
    ).apply { setMargins(dpi(4f), 0, dpi(4f), 0) }

    private fun applyInsets() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            val row = topBar.getChildAt(0) as View
            row.setPadding(dpi(14f), top + dpi(10f), dpi(14f), dpi(10f))
            bottomBar.setPadding(dpi(10f), dpi(10f), dpi(10f), bottom + dpi(6f))
            insets
        }
        root.requestApplyInsets()
    }

    // ------------------------------------------------------------- listener

    override fun onFirstPick() {
        if (running) return
        running = true
        startAt = SystemClock.elapsedRealtime() - elapsedMs
        handler.post(ticker)
    }

    override fun onProgress(placed: Int, total: Int) {
        progressLine.setFraction(placed.toFloat() / total.toFloat().coerceAtLeast(1f))
        subtitle.text = if (placed == total) {
            "Solved · ${difficulty.count} pieces"
        } else {
            "${difficulty.shape} · $placed of $total placed"
        }
    }

    override fun onSolved() {
        running = false
        elapsedMs = if (startAt == 0L) 0L else SystemClock.elapsedRealtime() - startAt
        timerLabel.text = format(elapsedMs)
        handler.removeCallbacks(ticker)
        handler.postDelayed({ showResult() }, 950)
    }

    // --------------------------------------------------------------- result

    private fun showResult() {
        if (isFinishing || resultShown) return
        resultShown = true

        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(0x99060710.toInt())
        scrim.alpha = 0f
        scrim.isClickable = true

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRectDrawable(Palette.SURFACE, dp(26f), 0x1FFFFFFF, dp(1f))
            setPadding(dpi(24f), dpi(24f), dpi(24f), dpi(20f))
        }

        val eyebrow = TextView(this).apply {
            text = "PUZZLE COMPLETE"
            setTextColor(artwork.accent)
            textSize = 10.5f
            typeface = Type.title
            letterSpacing = 0.2f
            includeFontPadding = false
        }
        val title = TextView(this).apply {
            text = "Beautifully done"
            setTextColor(Palette.TEXT)
            textSize = 25f
            typeface = Type.display
            includeFontPadding = false
        }
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        stats.addView(statBlock("TIME", format(elapsedMs)), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        stats.addView(statBlock("PIECES", difficulty.count.toString()), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        stats.addView(statBlock("GRID", difficulty.shape), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))

        val again = PillButton(this).apply {
            text = "Play again"
            primary = true
            onClick = { dismissResult(scrim); puzzle.restart(); resetTimer() }
        }
        val fresh = PillButton(this).apply {
            text = "New puzzle"
            onClick = { finish() }
        }

        card.addView(eyebrow)
        card.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpi(6f) })
        card.addView(stats, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpi(20f) })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpi(22f), 0, 0)
        }
        buttons.addView(again, LinearLayout.LayoutParams(0, dpi(52f), 1.35f).apply {
            setMargins(0, 0, dpi(5f), 0)
        })
        buttons.addView(fresh, LinearLayout.LayoutParams(0, dpi(52f), 1f).apply {
            setMargins(dpi(5f), 0, 0, 0)
        })
        card.addView(buttons)

        val cardParams = FrameLayout.LayoutParams(
            minOf(resources.displayMetrics.widthPixels - dpi(44f), dpi(360f)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        scrim.addView(card, cardParams)
        root.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        scrim.animate().alpha(1f).setDuration(220).start()
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(320).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun statBlock(label: String, value: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply {
            text = label
            setTextColor(Palette.TEXT_FAINT)
            textSize = 9.5f
            typeface = Type.title
            letterSpacing = 0.15f
            includeFontPadding = false
        })
        box.addView(TextView(this).apply {
            text = value
            setTextColor(Palette.TEXT)
            textSize = 17f
            typeface = Type.mono
            includeFontPadding = false
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpi(3f) })
        return box
    }

    private fun dismissResult(scrim: View) {
        resultShown = false
        root.removeView(scrim)
    }

    private fun resetTimer() {
        running = false
        elapsedMs = 0L
        startAt = 0L
        timerLabel.text = "0:00"
        handler.removeCallbacks(ticker)
    }

    private fun format(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60, total % 60)
    }

    override fun onBackPressed() {
        finish()
        overridePendingTransition(R.anim.hold, R.anim.fade_out)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        sound?.release()
        super.onDestroy()
    }
}
