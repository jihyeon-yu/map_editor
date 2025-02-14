package com.forsk.ondevice

import android.util.Log

object CommonUtil {
    const val TAG_APP = ""

    fun warnLog(tag: String? = null, message: String) =
        Log.w(TAG_APP + if (tag != null) "/$tag" else "", message)

    fun debugLog(tag: String? = null, message: String) =
        Log.d(TAG_APP + if (tag != null) "/$tag" else "", message)

    fun errorLog(tag: String? = null, message: String) =
        Log.e(TAG_APP + if (tag != null) "/$tag" else "", message)

}
