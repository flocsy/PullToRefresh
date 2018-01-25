package com.fletech.android.pulltorefresh.demo.utils

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import com.fletech.android.pulltorefresh.demo.R
import java.lang.Thread.sleep
import kotlin.concurrent.thread

/**
 * Created by Admin on 10/19/17.
 */
class SyncPopUpHelper {

    companion object {

        fun showPopUp(context: Context, view: View){

            val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            val inflatedView = layoutInflater.inflate(R.layout.sync_popup,null,false)
            val activity = context as Activity
            val display = activity.windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)

            val popUpWindow = PopupWindow(inflatedView, (size.x / 1.5).toInt() , (size.y / 2.5).toInt(), true)
            popUpWindow.setBackgroundDrawable(context.resources.getDrawable(R.drawable.popup_bg, null))
            popUpWindow.isFocusable = true
            popUpWindow.isOutsideTouchable = true
            popUpWindow.animationStyle = R.style.FadeAnimation
            popUpWindow.showAtLocation(view, Gravity.CENTER, 0,0)

            thread {
                sleep(2300)
                context.runOnUiThread {
                    popUpWindow.dismiss()
                }
            }



        }
    }
}
