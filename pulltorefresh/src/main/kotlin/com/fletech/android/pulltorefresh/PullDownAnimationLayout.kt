package com.fletech.android.pulltorefresh

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.AttrRes
import android.support.v7.widget.TintTypedArray
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.fletech.android.pulltorefresh.pullRatio.LogarithmicPullRatio
import com.fletech.android.pulltorefresh.pullRatio.PullRatio
import com.fletech.android.pulltorefresh.refreshAnimationView.LottieRefreshAnimationView
import com.fletech.android.pulltorefresh.refreshAnimationView.PullDownRefreshAnimationView
import java.lang.Math.abs
import java.lang.Math.min
import java.util.*

/**
 *
 * @author Andre Breton
 * @author Gavriel Fleischer
 *
 *
 * A Custom PulltoRefresh library which is able to display a Lottie Animation.
 *
 * Based on SwipeRefreshLayout native Android library.
 * 'https://developer.android.com/reference/android/support/v4/widget/SwipeRefreshLayout.html#SwipeRefreshLayout(android.content.Context)'
 * `onInterceptTouchEvent()` intercepts touch events and knows when the user is dragging or not.
 * Touch event prevention when the animation is still running
 *
 * `onTouchEvent()` checks if the target pull distance is reached to call `refreshTrigger`.
 * If the user did not drag long enough the view snaps back to its original position.
 *
 * Customization includes:
 * - The height of the View "MAX_PULL_HEIGHT_PX"
 * - The JSON animation "ANIMATION_ASSET_NAME"
 * - The maximum duration of the resetting animation "MAX_OFFSET_ANIMATION_DURATION_MS"
 *
 */

class PullDownAnimationLayout(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) :
        FrameLayout(context, attrs, defStyleAttr), Animator.AnimatorListener, PullDownAnimation {
    internal constructor(context: Context, attrs: AttributeSet?) :
            this(context, attrs, 0)
    internal constructor(context: Context) :
            this(context, null)

    companion object {
        val TAG = PullDownAnimationLayout::class.simpleName

        init {
            Log.d(TAG, "version: ${BuildConfig.VERSION_NAME}, build time: ${Date(BuildConfig.TIMESTAMP)}")
        }
    }

    private val EXTRA_SUPER_STATE = javaClass.canonicalName + ".EXTRA_SUPER_STATE"
    private val EXTRA_IS_REFRESHING = javaClass.canonicalName + ".EXTRA_IS_REFRESHING"
    private val EXTRA_LOOP_ANIMATION = javaClass.canonicalName + ".EXTRA_LOOP_ANIMATION"
    private val EXTRA_TARGET_OFFSET_TOP = javaClass.canonicalName + ".EXTRA_TARGET_OFFSET_TOP"

    //Customizable Properties
    private val DEFAULT_ANIMATION_ASSET_NAME = "pulse_loader.json"
    private val DEFAULT_ANIMATION_RESOURCE_ID = -1
    private val DEFAULT_AUTO_TRIGGER_REFRESH = false
    private val DEFAULT_CONTINUE_ANIMATION_UNTIL_OVER = false
    private val DEFAULT_DRAG_RATE = .5f
    private val DEFAULT_ENABLE_PULL_WHEN_REFRESHING = true
    private val DEFAULT_MAX_PULL_HEIGHT_PX = 0
    private val DEFAULT_MAX_OFFSET_ANIMATION_DURATION_MS = 400 // ms
    private val DEFAULT_PULL_TO_ANIMATION_PERCENT_RATIO = 1f
    private val DEFAULT_REFRESH_TRIGGER_HEIGHT_PX = dpToPx(100)
    private val DEFAULT_RETRIEVE_WHEN_REFRESH_TRIGGERED = false
    private val DEFAULT_RETRIEVE_WHEN_RELEASED = false

    override var ANIMATION_ASSET_NAME = DEFAULT_ANIMATION_ASSET_NAME
    private var ANIMATION_RESOURCE_ID = DEFAULT_ANIMATION_RESOURCE_ID
    private var AUTO_TRIGGER_REFRESH = DEFAULT_AUTO_TRIGGER_REFRESH
    private var CONTINUE_ANIMATION_UNTIL_OVER = DEFAULT_CONTINUE_ANIMATION_UNTIL_OVER
    private var DRAG_RATE = DEFAULT_DRAG_RATE
    private var ENABLE_PULL_WHEN_REFRESHING = DEFAULT_ENABLE_PULL_WHEN_REFRESHING
    override var MAX_PULL_HEIGHT_PX = DEFAULT_MAX_PULL_HEIGHT_PX
    private var MAX_OFFSET_ANIMATION_DURATION_MS = DEFAULT_MAX_OFFSET_ANIMATION_DURATION_MS
    private var PULL_TO_ANIMATION_PERCENT_RATIO = DEFAULT_PULL_TO_ANIMATION_PERCENT_RATIO
    override var REFRESH_TRIGGER_HEIGHT_PX = DEFAULT_REFRESH_TRIGGER_HEIGHT_PX
    private var RETRIEVE_WHEN_REFRESH_TRIGGERED = DEFAULT_RETRIEVE_WHEN_REFRESH_TRIGGERED
    private var RETRIEVE_WHEN_RELEASED = DEFAULT_RETRIEVE_WHEN_RELEASED
    //</editor-fold>

    // pull/drag related:
    private var beingDragged: Boolean = false
    private var initialTargetTop: Int = 0
    private var initialAbsoluteMotionY: Float = 0f
    private var activePointerId: Int = 0
    private val currentOffsetTop: Int
        inline get() = target.top
    private var currentDragPercent: Float = 0f

    private val canStillScrollUp: ((PullDownAnimationLayout, View?) -> Boolean)? = null
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    override var pullRatio : PullRatio = LogarithmicPullRatio()
    override var onRefreshListener: (() -> Unit?)? = null

    private var isRetrieving: Boolean = false
    private var retrieveAnimation: AnimatorSet? = null
    private var retrieveTargetHeight = 0

    private var canRefresh: Boolean = true
    private var isRefreshing: Boolean = false
    private var isAnimating: Boolean = false
    private var isProgressEnabled: Boolean = true // used to disable the progress frames when releasing after refresh and animation already finished
    private var stopAnimationWhenRetrieved = false
    private var loopAnimation = false

    init {
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {
        initializeStyledAttributes(attrs)

        post {
            refreshAnimation.addViewToParent(this)
        }

        //This ViewGroup does not draw things on the canvas
        setWillNotDraw(false)
    }

    @SuppressLint("RestrictedApi")
    private fun initializeStyledAttributes(attrs: AttributeSet?) {
        val styledAttributes = TintTypedArray.obtainStyledAttributes(context, attrs, R.styleable.PullDownAnimationLayout, 0, 0)
        initializeAnimation(styledAttributes)
        initializeAutoTriggerRefresh(styledAttributes)
        initializeDragRate(styledAttributes)
        initializeEnablePullWhenRefreshing(styledAttributes)
        initializeMaxPullHeight(styledAttributes)
        initializeMaxRetrieveAnimationDuration(styledAttributes)
        initializePullToAnimationPercentRatio(styledAttributes)
        initializeRefreshTriggerHeight(styledAttributes)
        initializeRetrieveWhenRefreshTriggered(styledAttributes)
        initializeRetrieveWhenReleased(styledAttributes)
        initializeContinueAnimationUntilOver(styledAttributes)
        styledAttributes.recycle()
    }

    @SuppressLint("RestrictedApi")
    private fun initializeAnimation(styledAttributes: TintTypedArray) {
        val lottieAnimationAssetStr = R.styleable.PullDownAnimationLayout_ptrLottieAnimationAsset
        val lottieAnimationIdInt = R.styleable.PullDownAnimationLayout_ptrLottieAnimationId
        if (styledAttributes.hasValue(lottieAnimationIdInt)) {
            ANIMATION_RESOURCE_ID = styledAttributes.getResourceId(lottieAnimationIdInt, -1)
        }
        else if (styledAttributes.hasValue(lottieAnimationAssetStr)) {
            ANIMATION_ASSET_NAME = styledAttributes.getString(lottieAnimationAssetStr)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeAutoTriggerRefresh(styledAttributes: TintTypedArray) {
        val autoTriggerStyleableBoolean = R.styleable.PullDownAnimationLayout_ptrAutoTriggerRefresh
        if (styledAttributes.hasValue(autoTriggerStyleableBoolean)) {
            AUTO_TRIGGER_REFRESH = styledAttributes.getBoolean(autoTriggerStyleableBoolean, DEFAULT_AUTO_TRIGGER_REFRESH)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeDragRate(styledAttributes: TintTypedArray) {
        val dragRateStyleableFloat = R.styleable.PullDownAnimationLayout_ptrDragRate
        if (styledAttributes.hasValue(dragRateStyleableFloat)) {
            DRAG_RATE = styledAttributes.getFloat(dragRateStyleableFloat, DEFAULT_DRAG_RATE)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeEnablePullWhenRefreshing(styledAttributes: TintTypedArray) {
        val enableStyleableBoolean = R.styleable.PullDownAnimationLayout_ptrEnablePullWhenRefreshing
        if (styledAttributes.hasValue(enableStyleableBoolean)) {
            ENABLE_PULL_WHEN_REFRESHING = styledAttributes.getBoolean(enableStyleableBoolean, DEFAULT_ENABLE_PULL_WHEN_REFRESHING)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeMaxPullHeight(styledAttributes: TintTypedArray) {
        val heightStyleableDimension = R.styleable.PullDownAnimationLayout_ptrMaxPullHeight
        if (styledAttributes.hasValue(heightStyleableDimension)) {
            MAX_PULL_HEIGHT_PX = styledAttributes.getDimensionPixelSize(heightStyleableDimension, DEFAULT_MAX_PULL_HEIGHT_PX)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeMaxRetrieveAnimationDuration(styledAttributes: TintTypedArray) {
        val durationStyleableInt = R.styleable.PullDownAnimationLayout_ptrMaxRetrieveAnimationDuration
        if (styledAttributes.hasValue(durationStyleableInt)) {
            MAX_OFFSET_ANIMATION_DURATION_MS = styledAttributes.getInteger(durationStyleableInt, DEFAULT_MAX_OFFSET_ANIMATION_DURATION_MS)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializePullToAnimationPercentRatio(styledAttributes: TintTypedArray) {
        val pull2percentStyleableFloat = R.styleable.PullDownAnimationLayout_ptrPullToAnimationPercentRatio
        if (styledAttributes.hasValue(pull2percentStyleableFloat)) {
            PULL_TO_ANIMATION_PERCENT_RATIO = styledAttributes.getFloat(pull2percentStyleableFloat, DEFAULT_PULL_TO_ANIMATION_PERCENT_RATIO)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeRefreshTriggerHeight(styledAttributes: TintTypedArray) {
        val heightStyleableDimension = R.styleable.PullDownAnimationLayout_ptrRefreshTriggerHeight
        if (styledAttributes.hasValue(heightStyleableDimension)) {
            REFRESH_TRIGGER_HEIGHT_PX = styledAttributes.getDimensionPixelSize(heightStyleableDimension, DEFAULT_REFRESH_TRIGGER_HEIGHT_PX)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeRetrieveWhenReleased(styledAttributes: TintTypedArray) {
        val retrieveStyleableBoolean = R.styleable.PullDownAnimationLayout_ptrRetrieveWhenReleased
        if (styledAttributes.hasValue(retrieveStyleableBoolean)) {
            RETRIEVE_WHEN_RELEASED = styledAttributes.getBoolean(retrieveStyleableBoolean, DEFAULT_RETRIEVE_WHEN_RELEASED)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeRetrieveWhenRefreshTriggered(styledAttributes: TintTypedArray) {
        val retrieveStyleableBoolean = R.styleable.PullDownAnimationLayout_ptrRetrieveWhenRefreshTriggered
        if (styledAttributes.hasValue(retrieveStyleableBoolean)) {
            RETRIEVE_WHEN_REFRESH_TRIGGERED = styledAttributes.getBoolean(retrieveStyleableBoolean, DEFAULT_RETRIEVE_WHEN_REFRESH_TRIGGERED)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun initializeContinueAnimationUntilOver(styledAttributes: TintTypedArray) {
        val continueStyleableBoolean = R.styleable.PullDownAnimationLayout_ptrContinueAnimationUntilOver
        if (styledAttributes.hasValue(continueStyleableBoolean)) {
            CONTINUE_ANIMATION_UNTIL_OVER = styledAttributes.getBoolean(continueStyleableBoolean, CONTINUE_ANIMATION_UNTIL_OVER)
        }
    }

    //Think RecyclerView
    private val target by lazy {
        var localView: View = getChildAt(0)
        for (i in 0..childCount - 1) {
            val child = getChildAt(i)
            if (child !== refreshAnimation) {
                localView = child
            }
        }
        localView
    }

    private val refreshAnimation: LottieRefreshAnimationView by lazy {
        (if (ANIMATION_RESOURCE_ID != DEFAULT_ANIMATION_RESOURCE_ID)
            rootView.findViewById<LottieRefreshAnimationView>(ANIMATION_RESOURCE_ID)
        else
            PullDownRefreshAnimationView(context)
        ).apply {
            addAnimatorListener(this@PullDownAnimationLayout)
            setup(this@PullDownAnimationLayout)
        }
    }

    //<editor-fold desc="Save State">
    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(EXTRA_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(EXTRA_IS_REFRESHING, isRefreshing)
        bundle.putBoolean(EXTRA_LOOP_ANIMATION, loopAnimation)
        bundle.putInt(EXTRA_TARGET_OFFSET_TOP, currentOffsetTop)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable<Parcelable>(EXTRA_SUPER_STATE))
            loopAnimation = state.getBoolean(EXTRA_LOOP_ANIMATION)
            if (state.getBoolean(EXTRA_IS_REFRESHING)) {
                post {
                    setTargetOffsetTop(state.getInt(EXTRA_TARGET_OFFSET_TOP, 0))
                    onRefreshContinued()
                }
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="Setup Functions">

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        checkConditions()
    }

    /**
     * Prevent our ViewGroup from having more than one child.
     */
    private fun checkConditions() {
        if (childCount > 1) {
            throw IllegalStateException("You can attach only one child to the PullDownAnimationLayout!")
        }
    }

    private fun startAnimation() {
        Log.d(TAG, "startAnimation")
        loopAnimation = true
        refreshAnimation.loop(true)
        Log.d(TAG, "startAnimation: loopAnimation = $loopAnimation")
        refreshAnimation.resumeAnimation()
    }

    private fun stopAnimation() {
        loopAnimation = false
        refreshAnimation.loop(false)
        Log.d(TAG, "stopAnimation: loopAnimation = $loopAnimation")
        if (!CONTINUE_ANIMATION_UNTIL_OVER) {
            Log.d(TAG, "stopAnimation: !CONTINUE_ANIMATION_UNTIL_OVER")
            refreshAnimation.cancelAnimation()
        }
    }

    private fun onRetrieved() {
        currentDragPercent = target.top / REFRESH_TRIGGER_HEIGHT_PX.toFloat()
        Log.d(TAG, "onRetrieved: stopAnimationWhenRetrieved:$stopAnimationWhenRetrieved")
        if (stopAnimationWhenRetrieved) {
            stopAnimation()
        }
    }

    private val retrieveAnimatorListenerAdapter: AnimatorListenerAdapter by lazy {
        object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                isRetrieving = false
                if (target.top == retrieveTargetHeight) {
                    isProgressEnabled = !beingDragged && !isAnimating
                    currentDragPercent = retrieveTargetHeight / REFRESH_TRIGGER_HEIGHT_PX.toFloat()
                    Log.d(TAG, "retrieveAnimatorListenerAdapter.onAnimationEnd: target.top:${target.top} == retrieveTargetHeight:$retrieveTargetHeight: isRetrieving = $isRetrieving, isProgressEnabled = $isProgressEnabled, currentDragPercent = $currentDragPercent [beingDragged:$beingDragged, isAnimating:$isAnimating]")
                    target.requestLayout()
                    onRetrieved()
                }
                else {
                    Log.d(TAG, "retrieveAnimatorListenerAdapter.onAnimationEnd: target.top:${target.top} != retrieveTargetHeight:$retrieveTargetHeight: isRetrieving = $isRetrieving [isProgressEnabled:$isProgressEnabled, beingDragged:$beingDragged, isAnimating:$isAnimating]")
                }
            }

            override fun onAnimationCancel(animation: Animator?) {
                Log.d(TAG, "retrieveAnimatorListenerAdapter.onAnimationCancel")
            }
        }
    }

    private fun retrieve(retrieveToStart: Boolean) {
        if (beingDragged) {
            Log.d(TAG, "retrieve: beingDragged")
            onRetrieved()
            return
        }
        if (target.top > 0) {
            isRetrieving = true
            //calculated value to decide how long the reset animation should take
            val animationDuration = abs((MAX_OFFSET_ANIMATION_DURATION_MS * currentDragPercent).toLong())
            val animateProgress = !isAnimating && isProgressEnabled
            Log.d(TAG, "retrieve: isRetrieving = $isRetrieving, retrieveToStart:$retrieveToStart, animateProgress:$animateProgress, stopAnimationWhenRetrieved:$stopAnimationWhenRetrieved, currentDragPercent:$currentDragPercent, target.top:${target.top}, animationDuration:$animationDuration, isAnimating:$isAnimating, isProgressEnabled:$isProgressEnabled")
            val animators = mutableListOf<Animator>()
            retrieveTargetHeight = if (retrieveToStart) 0 else REFRESH_TRIGGER_HEIGHT_PX
            val targetRetrieveAnimation = ObjectAnimator.ofInt(target, "top", retrieveTargetHeight)
            val retrieveAnimation = if (retrieveToStart) refreshAnimation.animateToStartPosition() else refreshAnimation.animateToRefreshPosition()
            animators += targetRetrieveAnimation
            if (retrieveAnimation != null) {
                animators += retrieveAnimation
            }
            if (animateProgress) {
                animators += ObjectAnimator.ofFloat(refreshAnimation, "progress", 0f)
            }
            this.retrieveAnimation = AnimatorSet().apply {
                addListener(retrieveAnimatorListenerAdapter)
                playTogether(animators)
                duration = animationDuration
                start()
            }
        } else {
            Log.d(TAG, "retrieve: target.top <= 0")
            onRetrieved()
        }
    }

    private fun retrieveAndContinueAnimation() {
        val retrieveToStart = if (beingDragged) RETRIEVE_WHEN_REFRESH_TRIGGERED else RETRIEVE_WHEN_RELEASED
        stopAnimationWhenRetrieved = false
        Log.d(TAG, "retrieveAndContinueAnimation: stopAnimationWhenRetrieved = $stopAnimationWhenRetrieved, beingDragged:$beingDragged, RETRIEVE_WHEN_REFRESH_TRIGGERED:$RETRIEVE_WHEN_REFRESH_TRIGGERED, RETRIEVE_WHEN_RELEASED:$RETRIEVE_WHEN_RELEASED")
        retrieve(retrieveToStart)
    }

    private fun retrieveAndStopAnimation() {
        stopAnimationWhenRetrieved = true
        Log.d(TAG, "retrieveAndStopAnimation: stopAnimationWhenRetrieved = $stopAnimationWhenRetrieved")
        retrieve(true)
    }

    private fun cancelRetrieve() {
        isRetrieving = false
        Log.d(TAG, "cancelRetrieve: isRetrieving = $isRetrieving")
        retrieveAnimation?.cancel()
        retrieveAnimation = null
    }

    private fun dpToPx(dp: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    //</editor-fold>

    //<editor-fold desc="Layout Rendering">

    private fun scrollTop(pullY: Float): Float = pullRatio.scrollTop(pullY)

    private fun canRefresh() = !isRefreshing && !isAnimating && canRefresh

    private fun showProgress() {
        if (!isAnimating && isProgressEnabled) {
            Log.d(TAG, "showProgress: currentDragPercent:$currentDragPercent")
            refreshAnimation.progress = (PULL_TO_ANIMATION_PERCENT_RATIO * currentDragPercent) % 1f
        }
    }

// TODO: according to lint we should call performClick from onTouchEvent
//    override fun performClick(): Boolean {
//        return super.performClick()
//    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        //If the user is still moving the pointer don't respond yet
        if (!beingDragged) {
            val ret = super.onTouchEvent(motionEvent)
            Log.d(TAG, "onTouchEvent.super => $ret: !beingDragged")
            return ret
        }
        //Prevent the list from scrolling while a pull to refresh animation is ongoing
        if (isRefreshing && !ENABLE_PULL_WHEN_REFRESHING) {
            Log.d(TAG, "onTouchEvent => true: isRefreshing")
            return true
        }

        when (motionEvent.actionMasked) {
            ACTION_MOVE -> {
                if (isAnimating && !ENABLE_PULL_WHEN_REFRESHING) {
                    Log.d(TAG, "onTouchEvent => false: ACTION_MOVE: isAnimating")
                    return false
                }
                val pointerIndex = motionEvent.findPointerIndex(activePointerId)
                if (pointerIndex != 0) {
                    Log.d(TAG, "onTouchEvent => false: ACTION_MOVE: pointerIndex != 0")
                    return false
                }
                val y = motionEvent.getY(pointerIndex)

                val yDiff = y - initialAbsoluteMotionY
                val scrollTop = scrollTop(initialTargetTop + yDiff)
                currentDragPercent = scrollTop / REFRESH_TRIGGER_HEIGHT_PX.toFloat()
                Log.v(TAG, "onTouchEvent: ACTION_MOVE:, yDiff = $yDiff, scrollTop = $scrollTop, currentDragPercent = $currentDragPercent [y:$y, initialTargetTop:$initialTargetTop, REFRESH_TRIGGER_HEIGHT_PX:$REFRESH_TRIGGER_HEIGHT_PX]")
                if (currentDragPercent < 0) {
                    Log.d(TAG, "onTouchEvent => false: ACTION_MOVE: currentDragPercent:$currentDragPercent < 0")
                    return false
                }
                val targetY = if (MAX_PULL_HEIGHT_PX > 0) min(MAX_PULL_HEIGHT_PX, scrollTop.toInt()) else scrollTop.toInt()
                val offsetY = targetY - currentOffsetTop
                Log.v(TAG, "onTouchEvent: ACTION_MOVE: targetOffsetTop := $offsetY, targetY: $targetY, currentOffsetTop: $currentOffsetTop, initialTargetTop:$initialTargetTop, initialAbsoluteMotionY: $initialAbsoluteMotionY, isProgressEnabled: $isProgressEnabled")
                setTargetOffsetTop(offsetY)

                showProgress()

                if (AUTO_TRIGGER_REFRESH && scrollTop >= REFRESH_TRIGGER_HEIGHT_PX && canRefresh()) {
                    canRefresh = false
                    Log.d(TAG, "onTouchEvent: ACTION_MOVE: AUTO_TRIGGER_REFRESH && scrollTop >= REFRESH_TRIGGER_HEIGHT_PX && canRefresh(): canRefresh=$canRefresh [isRefreshing:$isRefreshing, isAnimating:$isAnimating]")
                    onRefreshStarted()
                }
                Log.v(TAG, "onTouchEvent: ACTION_MOVE: ended")
            }
            ACTION_POINTER_DOWN -> {
                activePointerId = motionEvent.getPointerId(motionEvent.actionIndex)
                Log.d(TAG, "onTouchEvent: ACTION_POINTER_DOWN: activePointerId=$activePointerId")
            }
            ACTION_POINTER_UP -> {
                Log.d(TAG, "onTouchEvent: ACTION_POINTER_UP")
                onSecondaryPointerUp(motionEvent)
            }
            ACTION_UP, ACTION_CANCEL -> {
                if (activePointerId == INVALID_POINTER_ID) {
                    Log.d(TAG, "onTouchEvent => false: ACTION_UP, ACTION_CANCEL: activePointerId == INVALID_POINTER_ID:$INVALID_POINTER_ID")
                    return false
                }
                val y = motionEvent.getY(motionEvent.findPointerIndex(activePointerId))
                val overScrollTop = scrollTop(y - initialAbsoluteMotionY + initialTargetTop)
                beingDragged = false
                activePointerId = INVALID_POINTER_ID
                Log.d(TAG, "onTouchEvent: ACTION_UP, ACTION_CANCEL: yDiff: ${y - initialAbsoluteMotionY + initialTargetTop} [$REFRESH_TRIGGER_HEIGHT_PX, $MAX_PULL_HEIGHT_PX], overScrollTop=$overScrollTop, beingDragged=$beingDragged")
                if (overScrollTop >= REFRESH_TRIGGER_HEIGHT_PX && canRefresh()) {
                    canRefresh = false
                    Log.d(TAG, "onTouchEvent: ACTION_UP, ACTION_CANCEL: overScrollTop:$overScrollTop >= REFRESH_TRIGGER_HEIGHT_PX=$REFRESH_TRIGGER_HEIGHT_PX && canRefresh(); canRefresh=$canRefresh [isRefreshing:$isRefreshing, isAnimating:$isAnimating]")
                    onRefreshStarted()
                }
                else {
                    Log.d(TAG, "onTouchEvent: ACTION_UP, ACTION_CANCEL: overScrollTop:$overScrollTop < REFRESH_TRIGGER_HEIGHT_PX=$REFRESH_TRIGGER_HEIGHT_PX || !canRefresh() [isRefreshing:$isRefreshing, isAnimating:$isAnimating, canRefresh:$canRefresh]")
                    if (isRefreshing) {
                        retrieveAndContinueAnimation()
                    } else {
                        retrieveAndStopAnimation()
                    }
                }
                Log.d(TAG, "onTouchEvent => true: ACTION_UP, ACTION_CANCEL: activePointerId=INVALID_POINTER_ID:$INVALID_POINTER_ID")
            }
            else -> {
                Log.d(TAG, "onTouchEvent => true: else: ${motionEvent.actionMasked}")
            }
        }

        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        //Prevent the list from scrolling while a pull to refresh animation is ongoing
        if (isRefreshing && !ENABLE_PULL_WHEN_REFRESHING) {
            Log.d(TAG, "onInterceptTouchEvent => true: isRefreshing && !ENABLE_PULL_WHEN_REFRESHING")
            return true
        }

        //Ignore scroll touch events when the user is not on the top of the list
        if (!isEnabled || canChildScrollUp()) {
            Log.v(TAG, "onInterceptTouchEvent => false: !(isEnabled=$isEnabled) || canChildScrollUp=${if (isEnabled) "true" else "?"}")
            return false
        }

        when (ev.actionMasked) {
            ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                beingDragged = false
                val motionY = getMotionEventY(ev)
                if (motionY == -1f) {
                    Log.d(TAG, "onInterceptTouchEvent => false: ACTION_DOWN: motionY == -1f, activePointerId=$activePointerId, beingDragged=$beingDragged")
                    return false
                }
                initialTargetTop = target.top
                initialAbsoluteMotionY = motionY
                if (isRetrieving && initialTargetTop > 0) {
                    Log.d(TAG, "onInterceptTouchEvent => true: ACTION_DOWN: re-touch initialTargetTop=$initialTargetTop > 0")
                    cancelRetrieve()
                    beingDragged = true
                }
                Log.d(TAG, "onInterceptTouchEvent => $beingDragged: ACTION_DOWN ended: beingDragged=$beingDragged, initialTargetTop=$initialTargetTop, initialAbsoluteMotionY=$initialAbsoluteMotionY, activePointerId=$activePointerId")
            }
            ACTION_MOVE -> {
                if (activePointerId == INVALID_POINTER_ID) {
                    Log.d(TAG, "onInterceptTouchEvent => false: ACTION_MOVE: activePointerId == INVALID_POINTER_ID:$INVALID_POINTER_ID")
                    return false
                }
                val y = getMotionEventY(ev)
                if (y == -1f) {
                    Log.d(TAG, "onInterceptTouchEvent => false: ACTION_MOVE: y == -1f")
                    return false
                }

                val yDiff = y - initialAbsoluteMotionY
                if ((yDiff > touchSlop || isRetrieving) && !beingDragged) {
                    beingDragged = true
                    canRefresh = !isRefreshing && !isAnimating
                    isProgressEnabled = !isAnimating
//                    isProgressEnabled = !beingDragged && !isAnimating
//                    if (!isAnimating) {
//                        Log.w(TAG, "Note: isAnimating:$isAnimating, thus isProgressEnabled=$isProgressEnabled although beingDragged=$beingDragged")
//                    }
                    Log.d(TAG, "onInterceptTouchEvent => $beingDragged: ACTION_MOVE: start dragging: target.top:${target.top}, y: $y, (yDiff:$yDiff > touchSlop:$touchSlop || isRetrieving:$isRetrieving initialTargetTop:$initialTargetTop > 0) && !beingDragged; beingDragged=$beingDragged, canRefresh=$canRefresh, isProgressEnabled=$isProgressEnabled [isAnimating:$isAnimating]")
                } else {
                    Log.v(TAG, "onInterceptTouchEvent => $beingDragged: ACTION_MOVE: don't drag: (yDiff:$yDiff < touchSlop:$touchSlop && !isRetrieving:$isRetrieving) || beingDragged:$beingDragged [canRefresh:$canRefresh, isProgressEnabled:$isProgressEnabled]")
                }
            }
            ACTION_UP, ACTION_CANCEL -> {
                beingDragged = false
                activePointerId = INVALID_POINTER_ID
                Log.d(TAG, "onInterceptTouchEvent => $beingDragged: ACTION_UP, ACTION_CANCEL: beingDragged=$beingDragged, activePointerId=INVALID_POINTER_ID:$INVALID_POINTER_ID")
            }
            ACTION_POINTER_UP -> {
                Log.d(TAG, "onInterceptTouchEvent => $beingDragged: ACTION_POINTER_UP")
                onSecondaryPointerUp(ev)
            }
            else -> {
                Log.d(TAG, "onInterceptTouchEvent => $beingDragged: else: ${ev.actionMasked}")
            }
        }

        //Return true to steal motion events from the children and have
        //them dispatched to this ViewGroup through onTouchEvent()
        return beingDragged
    }

    private fun onSecondaryPointerUp(motionEvent: MotionEvent) {
        val pointerIndex = motionEvent.actionIndex
        val pointerId = motionEvent.getPointerId(pointerIndex)
        if (pointerId == activePointerId) {
            activePointerId = motionEvent.getPointerId(if (pointerIndex == 0) 1 else 0)
            Log.d(TAG, "onSecondaryPointerUp: activePointerId=$activePointerId")
        }
    }

    private fun getMotionEventY(motionEvent: MotionEvent): Float {
        val index = motionEvent.findPointerIndex(activePointerId)
        if (index < 0) {
            return -1f
        }
        return motionEvent.getY(index)
    }

    /**
     * Checks if the list can scroll up vertically.
     */
    private fun canChildScrollUp(): Boolean {
        var ret: Boolean
        canStillScrollUp?.let {
            ret = it(this, target)
            Log.v(TAG, "canChildScrollUp: canStillScrollUp = $ret")
            return ret
        }
        ret = target.canScrollVertically(-1)
        Log.v(TAG, "canChildScrollUp: canScrollVertically = $ret")
        return ret
    }

    private fun setTargetOffsetTop(offset: Int) {
        Log.v(TAG, "setTargetOffsetTop: offset:$offset")
        target.offsetTopAndBottom(offset)
        refreshAnimation.offsetTopAndBottom(offset)
    }

    private fun targetLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        target.let {
            val height = measuredHeight
            val width = measuredWidth
            val left = paddingLeft
            val top = paddingTop
            val right = paddingRight
            val bottom = paddingBottom
            Log.d(TAG, "targetLayout($changed, $l, $t, $r, $b), parent:{h:$height, w:$width, l:$left, t:$top, r:$right, b:$bottom}")

            it.layout(left, top + it.top, left + width - right, top + height - bottom + it.top)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        Log.d(TAG, "onLayout($changed, $l, $t, $r, $b), beingDragged: $beingDragged, target.top: ${target.top}")
        targetLayout(changed, l, t, r, b)
        refreshAnimation.onPullDownLayout(this, target, changed, l, t, r, b)
        showProgress()
    }

    private val animatorListeners: ArrayList<Animator.AnimatorListener> by lazy {
        ArrayList<Animator.AnimatorListener>()
    }

    override fun addAnimatorListener(listener: Animator.AnimatorListener) {
        animatorListeners.add(listener)
    }

    override fun removeAnimatorListener(listener: Animator.AnimatorListener) {
        animatorListeners.remove(listener)
    }

    private fun notifyAnimatorListeners(event: String, animator: Animator?) {
        Log.i(TAG, event)
        animatorListeners.onEach { listener -> listener.run {
            when (event) {
                "onAnimationStart" -> onAnimationStart(animator)
                "onAnimationCancel" -> onAnimationCancel(animator)
                "onAnimationRepeat" -> onAnimationRepeat(animator)
                "onAnimationEnd" -> onAnimationEnd(animator)
            }
        }}
    }

    override fun onAnimationStart(animation: Animator?) {
        isAnimating = true
        isProgressEnabled = false
        Log.d(TAG, "onAnimationStart: isAnimating = $isAnimating, isProgressEnabled = $isProgressEnabled [beingDragged:$beingDragged]")
        notifyAnimatorListeners("onAnimationStart", animation)
    }

    override fun onAnimationCancel(animation: Animator?) {
        notifyAnimatorListeners("onAnimationCancel", animation)
    }

    override fun onAnimationRepeat(animation: Animator?) {
        if (!loopAnimation) {
            refreshAnimation.cancelAnimation()
        } else {
            notifyAnimatorListeners("onAnimationRepeat", animation)
        }
    }

    override fun onAnimationEnd(animation: Animator?) {
        isAnimating = false
        isProgressEnabled = !beingDragged && !isRetrieving
        Log.d(TAG, "onAnimationEnd: isAnimating = $isAnimating, isProgressEnabled = $isProgressEnabled [beingDragged:$beingDragged, isRetrieving:$isRetrieving, progress:${refreshAnimation.progress}]")
        notifyAnimatorListeners("onAnimationEnd", animation)
    }

    override fun onRefreshStarted() {
        Log.i(TAG, "onRefreshStarted")
        onRefreshListener?.invoke()
        onRefreshContinued()
    }

    override fun onRefreshContinued() {
        if (!isRefreshing) {
            isRefreshing = true
            Log.i(TAG, "onRefreshContinued: isRefreshing = $isRefreshing")
            startAnimation()
            retrieveAndContinueAnimation()
        }
    }

    // must be called onUiThread or on Looper thread
    override fun onRefreshFinished() {
        isRefreshing = false
        Log.i(TAG, "onRefreshFinished: isRefreshing = $isRefreshing")
        retrieveAndStopAnimation()
    }
    //</editor-fold>

}
