package com.fletech.android.pulltorefresh.pullRatio

import kotlin.math.ln
import kotlin.math.log

/**
 * Created by flocsy on 2018-01-27.
 */
class LogarithmicPullRatio : PullRatio {
    private val logBase = 1.002f // base of logarithm, but it's basically the rate of pull
    private val shiftLeft = 1f / ln(logBase) // shift the graph left
    private val shiftDown = log(shiftLeft, logBase) // shift the graph down

    override fun scrollTop(pullY: Float): Float {
        val scrollTop = log(pullY + shiftLeft, logBase) - shiftDown
//        Log.d(javaClass.simpleName, "dynamicScrollTop: pullY: $pullY, scrollTop: $scrollTop")
        return scrollTop
    }
}
