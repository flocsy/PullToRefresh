package com.fletech.android.pulltorefresh.demo.activities

import android.animation.Animator
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.LinearSmoothScroller
import android.util.Log
import android.view.LayoutInflater
import com.fletech.android.pulltorefresh.OnRefreshListener
import com.fletech.android.pulltorefresh.PullDownAnimationLayout
import com.fletech.android.pulltorefresh.demo.R
import com.fletech.android.pulltorefresh.demo.adapters.SimpleAdapter
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Thread.sleep
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val TAG = javaClass.simpleName
    private val EXTRA_END_BOUND = "END_BOUND"
    private val EXTRA_IS_REFRESHING = "IS_REFRESHING"

    private val mContext = this@MainActivity

    var startBound = 1
    @Volatile var endBound = 41
    var lastEndBound = endBound
    @Volatile var dataSource = (startBound..endBound).map { "This is the list item # $it" }
    val layoutManager = LinearLayoutManager(mContext)

    private val simpleAdapter = SimpleAdapter()
    private val SIMULATED_NETWORK_DELAY = 1 * 1000L //ms
    private var isRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRefreshing = savedInstanceState?.getBoolean(EXTRA_IS_REFRESHING, false) ?: false
        val layout = LayoutInflater.from(this).inflate(R.layout.activity_main, null)
        setContentView(layout)
        layoutManager.reverseLayout = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = simpleAdapter

        val smoothScroller = object: LinearSmoothScroller(mContext) {
        }
        smoothScroller.targetPosition = endBound

        (findViewById(R.id.pull_to_refresh) as? PullDownAnimationLayout)?.let {
//            it.onRefreshListener = {
            it.onRefreshListener = object: OnRefreshListener {
                override fun onRefresh(): Void? {
                    Log.d(TAG, "onRefresh")
                    isRefreshing = true
                    thread {
                        sleep(SIMULATED_NETWORK_DELAY * 5)
                        endBound += 20
                        dataSource = (startBound..endBound).map { "This is the list item # $it" }
                        simpleAdapter.dataSource = dataSource
                        runOnUiThread {
                            it.onRefreshFinished()
                        }
                    }
                    return null
                }
            }
            it.addAnimatorListener(object: Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator?) {
                    Log.d(TAG, "onAnimationEnd")
                    runOnUiThread {
                        Log.d(TAG, "onAnimationEnd.runOnUiThread")
                        simpleAdapter.notifyItemRangeInserted(lastEndBound, endBound - lastEndBound)
                        lastEndBound = endBound
//                    SyncPopUpHelper.showPopUp(mContext, swipe_refresh)
                        smoothScroller.targetPosition = endBound
                        layoutManager.startSmoothScroll(smoothScroller)
                        isRefreshing = false
                    }
                }
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
                override fun onAnimationRepeat(animation: Animator?) {}
            })
            if (isRefreshing) {
//                it.onRefreshListener?.invoke()
                it.onRefreshListener?.onRefresh()
            }
        }

        Handler().postDelayed({
            simpleAdapter.dataSource = dataSource
            simpleAdapter.notifyDataSetChanged()
            layoutManager.startSmoothScroll(smoothScroller)
        }, SIMULATED_NETWORK_DELAY)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putInt(EXTRA_END_BOUND, endBound)
        outState?.putBoolean(EXTRA_IS_REFRESHING, isRefreshing)
        super.onSaveInstanceState(outState)
    }
}
