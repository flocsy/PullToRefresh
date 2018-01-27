package com.fletech.android.pulltorefresh.refreshAnimationView

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.fletech.android.pulltorefresh.PullDownAnimation

/**
 * Created by flocsy on 2018-01-12.
 */
class StaticRefreshAnimationView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        LottieRefreshAnimationView(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    override fun setup(parent: PullDownAnimation) {
    }

    override fun addViewToParent(parent : ViewGroup) {
    }

    override fun offsetTopAndBottom(offset: Int) {
    }

    override fun onPullDownLayout(parent: View, target: View, changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    }

    override fun animateToStartPosition(): Animator? {
        return null
    }

    override fun animateToRefreshPosition(): Animator? {
        return null
    }
}
