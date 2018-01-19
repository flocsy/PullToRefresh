package com.fletech.android.pulltorefresh

/**
 * Created by flocsy on 15/01/2018.
 */
@FunctionalInterface
interface OnRefreshListener {
    fun onRefresh(): Void?
}
