package com.avandel.pulltorefresh.activities

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import com.avandel.animationrefresh.PullDownAnimationLayout
import com.avandel.pulltorefresh.R
import com.avandel.pulltorefresh.adapters.SimpleAdapter
import com.avandel.pulltorefresh.utils.SyncPopUpHelper
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import android.support.v7.widget.LinearSmoothScroller



class MainActivity : AppCompatActivity() {

    private val mContext = this@MainActivity

    var startBound = 1
    var endBound = 41
    var dataSource = (startBound..endBound).map { "This is the list item # $it" }
    val layoutManager = LinearLayoutManager(mContext)

    private val simpleAdapter = SimpleAdapter()
    private val SIMULATED_NETWORK_DELAY = 1 * 1000L //ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LayoutInflater.from(this).inflate(R.layout.activity_main, null)
        setContentView(layout)
        layoutManager.reverseLayout = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = simpleAdapter

        val smoothScroller = object: LinearSmoothScroller(mContext){

        }
        smoothScroller.targetPosition = endBound

        (findViewById(R.id.swipe_refresh) as? PullDownAnimationLayout)?.let {
            it.onRefreshListener = {
                thread {
                    //startBound += 20
                    endBound += 20
                    dataSource = (startBound..endBound).map { "This is the list item # $it" }
                    smoothScroller.targetPosition = endBound
                    sleep(SIMULATED_NETWORK_DELAY * 5)
                    runOnUiThread {
                        simpleAdapter.dataSource = dataSource
                        simpleAdapter.notifyDataSetChanged()
                        it.refreshTrigger(false, false)
                        SyncPopUpHelper.showPopUp(mContext, swipe_refresh)
                        layoutManager.startSmoothScroll(smoothScroller)
                    }
                }
            }
        }

        Handler().postDelayed({
            simpleAdapter.dataSource = dataSource
            simpleAdapter.notifyDataSetChanged()
            layoutManager.startSmoothScroll(smoothScroller)
        }, SIMULATED_NETWORK_DELAY)
    }
}
