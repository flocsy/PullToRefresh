package com.fletech.android.pulltorefresh

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.ViewCompat
import android.support.v7.widget.TintTypedArray
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import java.lang.Math.*

/**
 *
 * @author Andre Breton
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
 * - The height of the View "DEFAULT_REFRESH_VIEW_HEIGHT"
 * - The JSON animation "ANIMATION_ASSET_NAME"
 * - The maximum duration of the resetting animation "DEFAULT_OFFSET_ANIMATION_DURATION"
 *
 */

class PullDownAnimationLayout @JvmOverloads
        constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) :
            FrameLayout(context, attrs, defStyleAttr, defStyleRes), Animator.AnimatorListener, PullDownAnimation {
    private val TAG = javaClass.simpleName

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
    private val DEFAULT_MAX_PULL_HEIGHT_DP = 200 // dp
    private val DEFAULT_MAX_OFFSET_ANIMATION_DURATION_MS = 400 // ms
    private val DEFAULT_REFRESH_TRIGGER_HEIGHT_DP = 100 // dp
    private val DEFAULT_RETRIEVE_WHEN_REFRESH_TRIGGERED = false
    private val DEFAULT_RETRIEVE_WHEN_RELEASED = false

    override var ANIMATION_ASSET_NAME = DEFAULT_ANIMATION_ASSET_NAME
    private var ANIMATION_RESOURCE_ID = DEFAULT_ANIMATION_RESOURCE_ID
    private var AUTO_TRIGGER_REFRESH = DEFAULT_AUTO_TRIGGER_REFRESH
    private var CONTINUE_ANIMATION_UNTIL_OVER = DEFAULT_CONTINUE_ANIMATION_UNTIL_OVER
    private var DRAG_RATE = DEFAULT_DRAG_RATE
    private var ENABLE_PULL_WHEN_REFRESHING = DEFAULT_ENABLE_PULL_WHEN_REFRESHING
    private var MAX_PULL_HEIGHT_DP = DEFAULT_MAX_PULL_HEIGHT_DP
    private var MAX_OFFSET_ANIMATION_DURATION_MS = DEFAULT_MAX_OFFSET_ANIMATION_DURATION_MS
    private var REFRESH_TRIGGER_HEIGHT_DP = DEFAULT_REFRESH_TRIGGER_HEIGHT_DP
    private var RETRIEVE_WHEN_REFRESH_TRIGGERED = DEFAULT_RETRIEVE_WHEN_REFRESH_TRIGGERED
    private var RETRIEVE_WHEN_RELEASED = DEFAULT_RETRIEVE_WHEN_RELEASED

    private val ENABLE_DYNAMIC_DRAG_RATE = false
    //</editor-fold>

    override val MAX_PULL_HEIGHT_PX: Int
    override val REFRESH_TRIGGER_HEIGHT_PX: Int

    private var targetPaddingBottom: Int = 0
    private var targetPaddingLeft: Int = 0
    private var targetPaddingRight: Int = 0
    private var targetPaddingTop: Int = 0

    private var initialMotionY: Float = 0f
    private var activePointerId: Int = 0

    private val currentOffsetTop: Int
        inline get() = target.top

    private var currentDragPercent: Float = 0f

    //<editor-fold desc="Fields & State Keeping">
    val canStillScrollUp: ((PullDownAnimationLayout, View?) -> Boolean)? = null
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

//    override var onRefreshListener: (() -> Unit)? = null
    override var onRefreshListener: OnRefreshListener? = null

    private var beingDragged: Boolean = false
    private var canRefresh: Boolean = true
    private var isRefreshing: Boolean = false
    private var isAnimating: Boolean = false
    private var stopAnimationWhenRetrieved = false
    private var loopAnimation = false

    init {
        initializeStyledAttributes(attrs)
        MAX_PULL_HEIGHT_PX = dpToPx(MAX_PULL_HEIGHT_DP)
        REFRESH_TRIGGER_HEIGHT_PX = dpToPx(REFRESH_TRIGGER_HEIGHT_DP)

        post {
            refreshAnimation.addViewToParent(this)
        }

        //This ViewGroup does not draw things on the canvas
        setWillNotDraw(false)
    }

    //Think RecyclerView
    private val target by lazy {
        var localView: View = getChildAt(0)
        for (i in 0..childCount - 1) {
            val child = getChildAt(i)
            if (child !== refreshAnimation) {
                localView = child
                targetPaddingBottom = localView.paddingBottom
                targetPaddingLeft = localView.paddingLeft
                targetPaddingRight = localView.paddingRight
                targetPaddingTop = localView.paddingTop
            }
        }
        localView
    }

    private val refreshAnimation: LottieRefreshAnimationView by lazy {
        (if (ANIMATION_RESOURCE_ID != DEFAULT_ANIMATION_RESOURCE_ID)
            rootView.findViewById<StaticRefreshAnimationView>(ANIMATION_RESOURCE_ID)
        else
            PullDownRefreshAnimationView(context)
        ).apply {
            addAnimatorListener(this@PullDownAnimationLayout)
            loop(true)
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

    @SuppressLint("RestrictedApi")
    private fun initializeStyledAttributes(attrs: AttributeSet?) {
        val styledAttributes = TintTypedArray.obtainStyledAttributes(context, attrs, R.styleable.PullDownAnimationLayout, 0, 0)
        initializeAnimation(styledAttributes)
        initializeAutoTriggerRefresh(styledAttributes)
        initializeDragRate(styledAttributes)
        initializeEnablePullWhenRefreshing(styledAttributes)
        initializeMaxPullHeight(styledAttributes)
        initializeMaxRetrieveAnimationDuration(styledAttributes)
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
        val heightStyleableInt = R.styleable.PullDownAnimationLayout_ptrMaxPullHeight
        if (styledAttributes.hasValue(heightStyleableInt)) {
            MAX_PULL_HEIGHT_DP = styledAttributes.getInteger(heightStyleableInt, DEFAULT_MAX_PULL_HEIGHT_DP)
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
    private fun initializeRefreshTriggerHeight(styledAttributes: TintTypedArray) {
        val heightStyleableInt = R.styleable.PullDownAnimationLayout_ptrRefreshTriggerHeight
        if (styledAttributes.hasValue(heightStyleableInt)) {
            REFRESH_TRIGGER_HEIGHT_DP = styledAttributes.getInteger(heightStyleableInt, DEFAULT_REFRESH_TRIGGER_HEIGHT_DP)
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

    private fun setTargetOffsetTop(offset: Int) {
        target.offsetTopAndBottom(offset)
        refreshAnimation.offsetTopAndBottom(offset)
    }

    private val retrieveAnimatorListenerAdapter: AnimatorListenerAdapter by lazy {
        object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                Log.d(TAG, "retrieveAnimatorListenerAdapter.onAnimationEnd: stopAnimationWhenRetrieved:" + stopAnimationWhenRetrieved)
                if (stopAnimationWhenRetrieved) {
                    loopAnimation = false
                    if (!CONTINUE_ANIMATION_UNTIL_OVER) {
                        Log.d(TAG, "retrieveAnimatorListenerAdapter.onAnimationEnd: !CONTINUE_ANIMATION_UNTIL_OVER")
                        refreshAnimation.pauseAnimation()
                    }
                }
            }

            override fun onAnimationCancel(animation: Animator?) {
                Log.d(TAG, "retrieveAnimatorListenerAdapter.onAnimationCancel")
            }
        }
    }

    private fun retrieve(animateProgress: Boolean, animateToStart: Boolean = true) {
        if (beingDragged) {
            Log.d(TAG, "retrieve: beingDragged")
            retrieveAnimatorListenerAdapter.onAnimationEnd(null)
            return
        }
        if (target.top > 0) {
            //calculated value to decide how long the reset animation should take
            val animationDuration = abs((MAX_OFFSET_ANIMATION_DURATION_MS * currentDragPercent).toLong())
//            val animationDuration = abs((MAX_OFFSET_ANIMATION_DURATION_MS * (target.top.toFloat() / MAX_PULL_HEIGHT_PX)).toLong())
            Log.d(TAG, "retrieve: animateProgress:${animateProgress}, animateToStart:${animateToStart}, stopAnimationWhenRetrieved:${stopAnimationWhenRetrieved}, currentDragPercent:${currentDragPercent}, myPercent:${target.top.toFloat() / MAX_PULL_HEIGHT_PX}, target.top:${target.top}, animationDuration:${animationDuration}")
            val animators = mutableListOf<Animator>()
            val targetRetrieveAnimation = ObjectAnimator.ofInt(target, "top", if (animateToStart) 0 else REFRESH_TRIGGER_HEIGHT_PX)
            val retrieveAnimation = if (animateToStart) refreshAnimation.animateToStartPosition() else refreshAnimation.animateToRefreshPosition()
            animators += (targetRetrieveAnimation)
            if (retrieveAnimation != null) {
                animators += retrieveAnimation
            }
            if (animateProgress) {
                animators += ObjectAnimator.ofFloat(refreshAnimation, "progress", 0f)
            }
            AnimatorSet().apply {
                addListener(retrieveAnimatorListenerAdapter)
                playTogether(animators)
                duration = animationDuration
            }.start()
        } else {
            Log.d(TAG, "retrieve: target.top <= 0")
            retrieveAnimatorListenerAdapter.onAnimationEnd(null)
        }
    }

    private fun retrieveWithAnimationAndContinueAnimation() {
        Log.d(TAG, "retrieveWithAnimationAndContinueAnimation")
        stopAnimationWhenRetrieved = false
        retrieve(false)
        refreshAnimation.resumeAnimation()
    }

    private fun retrieveWithAnimationAndStopAnimation() {
        Log.d(TAG, "retrieveWithAnimationAndStopAnimation")
        stopAnimationWhenRetrieved = true
        retrieve(false)
        refreshAnimation.resumeAnimation()
    }

    private fun retrieveWithProgressAndStopAnimation() {
        Log.d(TAG, "retrieveWithProgressAndStopAnimation")
        stopAnimationWhenRetrieved = true
        retrieve(true)
    }

    private fun retrieveToRefreshHeightWithAnimationAndContinueAnimation() {
        Log.d(TAG, "retrieveToRefreshHeightWithAnimationAndContinueAnimation")
        stopAnimationWhenRetrieved = false
        retrieve(false, false)
        refreshAnimation.resumeAnimation()
    }

    private fun dpToPx(dp: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

//    private fun pxToDp(px: Int) = px / resources.displayMetrics.density

    //</editor-fold>

    //<editor-fold desc="Layout Rendering">

    private fun calculateScrollTop(pullY: Float): Float {
        val scrollTop = if (!ENABLE_DYNAMIC_DRAG_RATE)
            pullY * DRAG_RATE
        else
            MAX_PULL_HEIGHT_PX-(MAX_PULL_HEIGHT_PX.toFloat())/(MAX_PULL_HEIGHT_PX*MAX_PULL_HEIGHT_PX) *
                (pullY-MAX_PULL_HEIGHT_PX)*(pullY-MAX_PULL_HEIGHT_PX)
        return min(scrollTop, MAX_PULL_HEIGHT_PX.toFloat())
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        //If the user is still moving the pointer don't respond yet
        if (!beingDragged) {
            Log.d(TAG, "onTouchEvent: !beingDragged")
            return super.onTouchEvent(motionEvent)
        }
        //Prevent the list from scrolling while a pull to refresh animation is ongoing
        if (isRefreshing && !ENABLE_PULL_WHEN_REFRESHING) {
            Log.d(TAG, "onTouchEvent: isRefreshing")
            return true
        }

        when (MotionEventCompat.getActionMasked(motionEvent)) {
            ACTION_MOVE -> {
                if (isAnimating && !ENABLE_PULL_WHEN_REFRESHING) {
                    Log.d(TAG, "onTouchEvent: ACTION_MOVE: isAnimating")
                    return false
                }
                val pointerIndex = motionEvent.findPointerIndex(activePointerId)
                if (pointerIndex != 0) {
                    Log.d(TAG, "onTouchEvent: ACTION_MOVE: pointerIndex != 0")
                    return false
                }

                val y = motionEvent.getY(pointerIndex)

                val yDiff = y - initialMotionY
                val scrollTop = calculateScrollTop(yDiff)
                currentDragPercent = scrollTop / MAX_PULL_HEIGHT_PX.toFloat()
//                currentDragPercent = scrollTop / REFRESH_TRIGGER_HEIGHT_PX.toFloat()
                Log.d(TAG, "onTouchEvent: ACTION_MOVE: yDiff: $yDiff [$REFRESH_TRIGGER_HEIGHT_PX, $MAX_PULL_HEIGHT_PX], scrollTop1: ${yDiff * DRAG_RATE}, scrollTop2: $scrollTop, currentDragPercent: $currentDragPercent")
                if (currentDragPercent < 0) {
                    Log.d(TAG, "onTouchEvent: ACTION_MOVE: currentDragPercent < 0")
                    return false
                }
                val boundedDragPercent = min(1f, abs(currentDragPercent))
//                val boundedDragPercent = min(1f, abs(scrollTop / REFRESH_TRIGGER_HEIGHT_PX.toFloat()))
                val targetY = (MAX_PULL_HEIGHT_PX * boundedDragPercent).toInt()
                setTargetOffsetTop(targetY - currentOffsetTop)

                if (!isAnimating) {
                    refreshAnimation.progress = boundedDragPercent
                }

//                Log.d(TAG, "onTouchEvent: ACTION_MOVE: boundedDragPercent:" + boundedDragPercent)
//                if (!isRefreshing && AUTO_TRIGGER_REFRESH && boundedDragPercent == 1f) {
//                    Log.d(TAG, "onTouchEvent: ACTION_MOVE: AUTO_TRIGGER_REFRESH && boundedDragPercent == 1f")
                if (!isRefreshing && canRefresh && AUTO_TRIGGER_REFRESH && scrollTop >= REFRESH_TRIGGER_HEIGHT_PX) {
                    Log.d(TAG, "onTouchEvent: ACTION_MOVE: AUTO_TRIGGER_REFRESH && scrollTop >= REFRESH_TRIGGER_HEIGHT_PX")
                    canRefresh = false
                    onRefreshStarted()
                }
//                Log.d(TAG, "onTouchEvent: ACTION_MOVE: ended")
            }
            ACTION_POINTER_DOWN -> {
                Log.d(TAG, "onTouchEvent: ACTION_POINTER_DOWN")
                activePointerId = motionEvent.getPointerId(MotionEventCompat.getActionIndex(motionEvent))
            }
            ACTION_POINTER_UP -> {
                Log.d(TAG, "onTouchEvent: ACTION_POINTER_UP")
                onSecondaryPointerUp(motionEvent)
            }
            ACTION_UP, ACTION_CANCEL -> {
                if (activePointerId == INVALID_POINTER_ID) {
                    Log.d(TAG, "onTouchEvent: ACTION_UP, ACTION_CANCEL: activePointerId == INVALID_POINTER_ID")
                    return false
                }
                val y = motionEvent.getY(motionEvent.findPointerIndex(activePointerId))
                val overScrollTop = calculateScrollTop(y - initialMotionY)
//                Log.d(TAG, "onTouchEvent: ACTION_UP, ACTION_CANCEL: yDiff: ${y - initialMotionY} [$REFRESH_TRIGGER_HEIGHT_PX, $MAX_PULL_HEIGHT_PX], dragRate: ${dragRate(y - initialMotionY)}, scrollTop1: ${(y - initialMotionY) * DRAG_RATE}, scrollTop2: $overScrollTop")
                beingDragged = false
                if (overScrollTop >= REFRESH_TRIGGER_HEIGHT_PX && !isRefreshing && canRefresh) {
                    Log.d(TAG, "onTouchEvent: ACTION_UP, ACTION_CANCEL: overScrollTop=$overScrollTop >= REFRESH_TRIGGER_HEIGHT_PX=$REFRESH_TRIGGER_HEIGHT_PX && !isRefreshing && canRefresh")
                    if (RETRIEVE_WHEN_RELEASED) {
                        retrieveWithAnimationAndContinueAnimation()
                    }
                    onRefreshStarted()
                } else {
                    Log.d(TAG, "onTouchEvent: ACTION_UP, ACTION_CANCEL: overScrollTop=$overScrollTop < REFRESH_TRIGGER_HEIGHT_PX=$REFRESH_TRIGGER_HEIGHT_PX || isRefreshing=$isRefreshing || !(canRefresh=$canRefresh); isAnimating:$isAnimating")
                    if (isAnimating) {
                        retrieveWithAnimationAndContinueAnimation()
                    } else {
                        retrieveWithProgressAndStopAnimation()
                    }
                }
                activePointerId = INVALID_POINTER_ID
            }
        }

        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        //Prevent the list from scrolling while a pull to refresh animation is ongoing
        if (isRefreshing && !ENABLE_PULL_WHEN_REFRESHING) {
            Log.d(TAG, "onInterceptTouchEvent: isRefreshing")
            return true
        }

        //Ignore scroll touch events when the user is not on the top of the list
        if (!isEnabled || canChildScrollUp()) {
            Log.d(TAG, "onInterceptTouchEvent: !(isEnabled=$isEnabled) || canChildScrollUp=${if (isEnabled) canChildScrollUp() else '?'}")
            return false
        }

        when (MotionEventCompat.getActionMasked(ev)) {
            ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                beingDragged = false
                val motionY = getMotionEventY(ev)
                if (motionY == -1f) {
                    Log.d(TAG, "onInterceptTouchEvent: ACTION_DOWN: motionY == -1f")
                    return false
                }
                initialMotionY = motionY
                Log.d(TAG, "onInterceptTouchEvent: ACTION_DOWN ended: initialMotionY: ${initialMotionY}")
            }
            ACTION_MOVE -> {
                if (activePointerId == INVALID_POINTER_ID) {
                    Log.d(TAG, "onInterceptTouchEvent: ACTION_MOVE: activePointerId == INVALID_POINTER_ID")
                    return false
                }

                val y = getMotionEventY(ev)
                if (y == -1f) {
                    Log.d(TAG, "onInterceptTouchEvent: ACTION_MOVE: y == -1f")
                    return false
                }

                val yDiff = y - initialMotionY
                if (yDiff > touchSlop && !beingDragged) {
                    beingDragged = true
                    canRefresh = true
                    Log.d(TAG, "onInterceptTouchEvent: ACTION_MOVE: beingDragged = true (start dragging)")
                }
//                Log.d(TAG, "onInterceptTouchEvent: ACTION_MOVE ended")
            }
            ACTION_UP, ACTION_CANCEL -> {
                Log.d(TAG, "onInterceptTouchEvent: ACTION_UP, ACTION_CANCEL")
                beingDragged = false
                activePointerId = INVALID_POINTER_ID
            }
            ACTION_POINTER_UP -> {
                Log.d(TAG, "onInterceptTouchEvent: ACTION_POINTER_UP")
                onSecondaryPointerUp(ev)
            }
        }

        //Return true to steal motion events from the children and have
        //them dispatched to this ViewGroup through onTouchEvent()
        return beingDragged
    }

    private fun onSecondaryPointerUp(motionEvent: MotionEvent) {
        val pointerIndex = MotionEventCompat.getActionIndex(motionEvent)
        val pointerId = motionEvent.getPointerId(pointerIndex)
        if (pointerId == activePointerId) {
            activePointerId = motionEvent.getPointerId(if (pointerIndex == 0) 1 else 0)
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
        canStillScrollUp?.let {
            return it(this, target)
        }
        return ViewCompat.canScrollVertically(target, -1)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        refreshAnimation.onPullDownLayout(this, target, changed, l, t, r, b)
    }

    val animatorListeners: ArrayList<Animator.AnimatorListener> by lazy {
        ArrayList<Animator.AnimatorListener>()
    }

    override fun addAnimatorListener(listener: Animator.AnimatorListener) {
        animatorListeners.add(listener)
    }

    override fun removeAnimatorListener(listener: Animator.AnimatorListener) {
        animatorListeners.remove(listener)
    }

    private fun notifyAnimatorListeners(event: String, animator: Animator?) {
        Log.d(TAG, event)
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
        notifyAnimatorListeners("onAnimationEnd", animation)
    }

    override fun onRefreshStarted() {
        Log.d(TAG, "onRefreshStarted")
//        onRefreshListener?.invoke()
        onRefreshListener?.onRefresh()
        onRefreshContinued()
    }

    override fun onRefreshContinued() {
        if (!isRefreshing) {
            isRefreshing = true
            if (RETRIEVE_WHEN_REFRESH_TRIGGERED) {
                Log.d(TAG, "onRefreshContinued: RETRIEVE_WHEN_REFRESH_TRIGGERED")
                retrieveWithAnimationAndContinueAnimation()
            }
            else {
                Log.d(TAG, "onRefreshContinued: !RETRIEVE_WHEN_REFRESH_TRIGGERED")
                retrieveToRefreshHeightWithAnimationAndContinueAnimation()
            }
            loopAnimation = true
            target.setPadding(targetPaddingLeft, targetPaddingTop, targetPaddingRight, targetPaddingBottom)
        }
    }

    // must be called onUiThread or on Looper thread
    override fun onRefreshFinished() {
        Log.d(TAG, "onRefreshFinished")
        isRefreshing = false
        retrieveWithAnimationAndStopAnimation()
    }
    //</editor-fold>

}
