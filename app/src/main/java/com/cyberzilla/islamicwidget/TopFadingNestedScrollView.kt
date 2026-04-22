package com.cyberzilla.islamicwidget

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

class TopFadingNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    init {
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength((48 * resources.displayMetrics.density).toInt())
    }

    override fun getBottomFadingEdgeStrength(): Float = 0f
}
