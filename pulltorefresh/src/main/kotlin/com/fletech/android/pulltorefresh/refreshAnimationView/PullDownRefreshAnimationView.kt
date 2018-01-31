package com.fletech.android.pulltorefresh.refreshAnimationView

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.fletech.android.pulltorefresh.PullDownAnimation

/**
* Created by flocsy on 2018-01-17.
*/
class PullDownRefreshAnimationView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        LottieRefreshAnimationView(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    private var refreshTriggerHeight = 0

    override fun setup(parent: PullDownAnimation) {
        refreshTriggerHeight = parent.REFRESH_TRIGGER_HEIGHT_PX
//        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setAnimation(parent.ANIMATION_ASSET_NAME)
    }

    override fun addViewToParent(parent : ViewGroup) {
        parent.addView(this, 0)
    }

    override fun onPullDownLayout(parent: View, target: View, changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        target.let {
            val pWidth = parent.measuredWidth
            val pLeft = parent.paddingLeft
            val pTop = parent.paddingTop
            val tTop = it.top
            Log.d("PullDownRefreshAnimView", "onPullDownLayout($changed, $l, $t, $r, $b), parent:{w:$pWidth, l:$pLeft, t:$pTop}, target.top:$tTop")

            //Our refresh animation is above parent's first child
            layout(pLeft, tTop - refreshTriggerHeight, pWidth, tTop + pTop)
        }
    }

    override fun animateToStartPosition() : Animator? {
        return ObjectAnimator.ofInt(this, "top", -refreshTriggerHeight)
    }

    override fun animateToRefreshPosition(): Animator? {
        return ObjectAnimator.ofInt(this, "top", 0)
    }
}
