package com.fletech.android.pulltorefresh.refreshAnimationView

import android.animation.Animator
import android.support.annotation.FloatRange
import android.view.View
import android.view.ViewGroup
import com.fletech.android.pulltorefresh.PullDownAnimation

/**
 * Created by flocsy on 12/01/2018.
 */
interface RefreshAnimationView {
    fun setup(parent: PullDownAnimation)
    fun addViewToParent(parent : ViewGroup)
    fun setProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float)
    fun offsetTopAndBottom(offset: Int)
    fun onPullDownLayout(parent: View, target: View, changed: Boolean, l: Int, t: Int, r: Int, b: Int)
    fun animateToStartPosition(): Animator?
    fun animateToRefreshPosition(): Animator?
}
