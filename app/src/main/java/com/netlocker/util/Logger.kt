package com.netlocker.util

import android.util.Log
import com.netlocker.BuildConfig

/**
 * Thin wrapper so debug-level logging can be stripped from release builds without
 * scattering `if (BuildConfig.DEBUG)` checks everywhere (spec §19: verbose logs in
 * dev, none of that in release). [Logger.d]/[Logger.v] calls are additionally
 * configured as no-ops for R8 in proguard-rules.pro so release builds don't even
 * pay for the string-building work.
 */
object Logger {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d("NetLocker/$tag", message)
    }

    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.v("NetLocker/$tag", message)
    }

    /** Warnings/errors are kept in release builds too — they matter for diagnosing a
     *  real user's "why didn't this block?" report, not just for development. */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("NetLocker/$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("NetLocker/$tag", message, throwable)
    }
}
