package com.fletech.android.pulltorefresh.refreshAnimationView

import android.content.Context
import android.util.AttributeSet
import com.airbnb.lottie.LottieAnimationView

/**
 * Created by flocsy on 12/01/2018.
 */
abstract class LottieRefreshAnimationView @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        LottieAnimationView(context, attrs, defStyleAttr), RefreshAnimationView {
}
