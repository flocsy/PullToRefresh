package com.fletech.android.pulltorefresh.pullRatio

/**
 * Created by flocsy on 2018-01-27.
 */
class LinearPullRatio : PullRatio {
    private val ratio = .5f

    override fun scrollTop(pullY: Float): Float {
        return pullY * ratio
    }
}
