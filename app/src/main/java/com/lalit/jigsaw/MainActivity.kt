package com.lalit.jigsaw

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var preview: ArtPreview
    private lateinit var picker: ArtPicker
    private lateinit var segmented: Segmented
    private lateinit var content: LinearLayout

    private var artIndex = 0
    private var difficultyIndex = Difficulty.MEDIUM.ordinal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Palette.BG_TOP, Palette.BG_BOTTOM)
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)

        buildContent()

        root.setOnApplyWindowInsetsListener { _, insets ->
            val top = insets.systemWindowInsetTop
            val bottom = insets.systemWindowInsetBottom
            content.setPadding(dpi(22f), top + dpi(14f), dpi(22f), bottom + dpi(24f))
            insets
        }
        root.requestApplyInsets()
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

    private fun buildContent() {
        content.addView(wordmark())
        content.addView(gap(16))

        preview = ArtPreview(this)
        preview.setArt(Art.items[artIndex])
        content.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpi(6f) })

        content.addView(gap(14))

        picker = ArtPicker(this)
        picker.onSelect = { index ->
            artIndex = index
            preview.setArt(Art.items[index])
        }
        content.addView(picker, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        picker.setSelected(artIndex)

        content.addView(gap(18))
        content.addView(sectionLabel("DIFFICULTY"))
        content.addView(gap(9))

        segmented = Segmented(this)
        segmented.setSelected(difficultyIndex)
        segmented.onSelect = { difficultyIndex = it }
        content.addView(segmented, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpi(58f)
        ))

        content.addView(gap(20))

        val start = PillButton(this).apply {
            text = "Start puzzle"
            primary = true
            onClick = { startGame() }
        }
        content.addView(start, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpi(56f)
        ))

        content.addView(gap(12))
        val hint = TextView(this).apply {
            text = "Drag pieces onto the board — they snap when close."
            setTextColor(Palette.TEXT_FAINT)
            textSize = 11.5f
            typeface = Type.body
            gravity = Gravity.CENTER
        }
        content.addView(hint)

        // gentle entrance
        preview.fadeUpIn(60)
        picker.fadeUpIn(120)
        segmented.fadeUpIn(180)
        start.fadeUpIn(240)
    }

    private fun wordmark(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            text = "JIGSAW"
            setTextColor(Palette.TEXT)
            textSize = 34f
            typeface = Type.display
            letterSpacing = 0.12f
            includeFontPadding = false
        }
        val sub = TextView(this).apply {
            text = "A calm little puzzle game"
            setTextColor(Palette.TEXT_DIM)
            textSize = 13f
            typeface = Type.body
            includeFontPadding = false
        }
        row.addView(title)
        row.addView(sub, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpi(4f) })
        row.fadeUpIn(0)
        return row
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Palette.TEXT_FAINT)
        textSize = 10f
        typeface = Type.title
        letterSpacing = 0.22f
        includeFontPadding = false
    }

    private fun gap(dp: Int): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dpi(dp.toFloat()))
    }

    private fun startGame() {
        val art = Art.items[artIndex]
        val difficulty = Difficulty.of(difficultyIndex)
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_ART, art.id)
            putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.hold)
    }
}
