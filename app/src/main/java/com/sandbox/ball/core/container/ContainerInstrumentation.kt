package com.sandbox.ball.core.container

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method

class ContainerInstrumentation(private val base: Instrumentation?) : Instrumentation() {

    companion object {
        private const val TAG = "ContainerInstrumentation"
        private var isInjected = false

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        fun inject() {
            if (isInjected) return
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
                currentActivityThreadMethod.isAccessible = true
                val currentActivityThread = currentActivityThreadMethod.invoke(null) ?: return

                val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
                mInstrumentationField.isAccessible = true
                val baseInst = mInstrumentationField.get(currentActivityThread) as? Instrumentation

                if (baseInst !is ContainerInstrumentation) {
                    val custom = ContainerInstrumentation(baseInst)
                    mInstrumentationField.set(currentActivityThread, custom)
                    isInjected = true
                    Log.i(TAG, "ContainerInstrumentation successfully injected into ActivityThread")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not inject ContainerInstrumentation: ${e.message}")
            }
        }
    }

    // Intercept execStartActivity (API 21 - 34 signatures)
    @SuppressLint("DiscouragedPrivateApi")
    fun execStartActivity(
        who: Context?,
        contextThread: IBinder?,
        token: IBinder?,
        target: Activity?,
        intent: Intent?,
        requestCode: Int,
        options: Bundle?
    ): ActivityResult? {
        Log.d(TAG, "execStartActivity intercepted for intent: ${intent?.action} target: ${intent?.component}")

        // Redirect internal activities if needed or forward to base
        return try {
            val execMethod: Method? = Instrumentation::class.java.getDeclaredMethod(
                "execStartActivity",
                Context::class.java,
                IBinder::class.java,
                IBinder::class.java,
                Activity::class.java,
                Intent::class.java,
                Int::class.javaPrimitiveType,
                Bundle::class.java
            )
            execMethod?.isAccessible = true
            execMethod?.invoke(base ?: this, who, contextThread, token, target, intent, requestCode, options) as? ActivityResult
        } catch (e: Exception) {
            Log.e(TAG, "execStartActivity forward failed: ${e.message}")
            null
        }
    }

    override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
        return try {
            base?.newActivity(cl, className, intent) ?: super.newActivity(cl, className, intent)
        } catch (_: Exception) {
            super.newActivity(cl, className, intent)
        }
    }

    override fun callActivityOnCreate(activity: Activity?, icicle: Bundle?) {
        base?.callActivityOnCreate(activity, icicle) ?: super.callActivityOnCreate(activity, icicle)
    }

    override fun callActivityOnResume(activity: Activity?) {
        base?.callActivityOnResume(activity) ?: super.callActivityOnResume(activity)
    }

    override fun callActivityOnPause(activity: Activity?) {
        base?.callActivityOnPause(activity) ?: super.callActivityOnPause(activity)
    }

    override fun callActivityOnDestroy(activity: Activity?) {
        base?.callActivityOnDestroy(activity) ?: super.callActivityOnDestroy(activity)
    }
}
