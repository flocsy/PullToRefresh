[![Build Status](https://travis-ci.org/flocsy/PullToRefresh.svg?branch=master)](https://travis-ci.org/flocsy/PullToRefresh)

# com.fletech.android.pulltorefresh ♻

## Include the library in build.gradle
Add to the `dependencies {}` section:

in gradle 3 and less:

```compile "com.fletech.android:pulltorefresh:0.0.10"```

in gradle 4+:

```api "com.fletech.android:pulltorefresh:0.0.10"```


## XML Examples

Minimal usage in layout/pulltorefresh_activity.xml:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.fletech.android.pulltorefresh.PullDownAnimationLayout
        android:id="@id/pull_to_refresh"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        >

        <android.support.v7.widget.RecyclerView
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/recyclerView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            app:layoutManager="android.support.v7.widget.LinearLayoutManager"/>

    </com.fletech.android.pulltorefresh.PullDownAnimationLayout>
</LinearLayout>
```

The default attributes are:
```xml
<com.fletech.android.pulltorefresh.PullDownAnimationLayout
    android:id="@id/pull_to_refresh"
    android:layout_width="match_parent"
    android:layout_height="match_parent"

    app:ptrLottieAnimationAsset="pulse_loader.json"
    app:ptrAutoTriggerRefresh="false"
    app:ptrContinueAnimationUntilOver="false"
    app:ptrDragRate="0.5"
    app:ptrEnablePullWhenRefreshing="true"
    app:ptrMaxPullHeight="200"
    app:ptrMaxRetrieveAnimationDuration="400"
    app:ptrRefreshTriggerHeight="100"
    app:ptrRetrieveWhenRefreshTriggered="false"
    app:ptrRetrieveWhenReleased="false"
    >
```

Using an external, static lottie animation (StaticRefreshAnimationView extends LottieAnimationView):
```xml
<com.fletech.android.pulltorefresh.StaticRefreshAnimationView
    android:id="@+id/animation_view"
    android:layout_width="match_parent"
    android:layout_height="3dp"
    android:scaleType="centerCrop"
    app:lottie_fileName="gears.json"
    app:lottie_autoPlay="false"
    app:lottie_loop="true" />
<com.fletech.android.pulltorefresh.PullDownAnimationLayout
    android:id="@id/pull_to_refresh"
    android:layout_width="match_parent"
    android:layout_height="match_parent"

    app:ptrLottieAnimationId="@id/animation_view"
    >
```

## Interacting with the PullDownAnimationLayout programmatically
PullDownAnimationLayout extends PullDownAnimation and Animator.AnimatorListener

```kotlin
interface PullDownAnimation {
    val MAX_PULL_HEIGHT_PX: Int
    val REFRESH_TRIGGER_HEIGHT_PX: Int
    val ANIMATION_ASSET_NAME: String
    var onRefreshListener: (() -> Unit)?
    fun onRefreshStarted()
    fun onRefreshContinued()
    fun onRefreshFinished()
    fun addAnimatorListener(listener: Animator.AnimatorListener)
    fun removeAnimatorListener(listener: Animator.AnimatorListener)
}
```

