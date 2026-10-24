package com.sandbox.ball.core.container

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method

class ContainerInstrumentation(private val base: Instrumentation?) : Instrumentation() {

    companion object {
        private const val TAG = "ContainerInstrumentation"
        const val EXTRA_REAL_ACTIVITY = "_stealth_real_activity"

        @Volatile
        private var instance: ContainerInstrumentation? = null

        var activeTargetPackage: String? = null
        var activeContainerContext: ContainerContext? = null
        var activeClassLoader: ClassLoader? = null

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        fun inject(): ContainerInstrumentation? {
            if (instance != null) return instance
            return try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
                currentActivityThreadMethod.isAccessible = true
                val currentActivityThread = currentActivityThreadMethod.invoke(null) ?: return null

                val mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation")
                mInstrumentationField.isAccessible = true
                val baseInst = mInstrumentationField.get(currentActivityThread) as? Instrumentation

                if (baseInst !is ContainerInstrumentation) {
                    val custom = ContainerInstrumentation(baseInst)
                    mInstrumentationField.set(currentActivityThread, custom)
                    instance = custom
                    Log.i(TAG, "ContainerInstrumentation successfully injected into ActivityThread")
                    custom
                } else {
                    instance = baseInst
                    baseInst
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not inject ContainerInstrumentation: ${e.message}")
                null
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
        val targetPkg = activeTargetPackage
        val component = intent?.component
        val intentPkg = component?.packageName

        // If launching target app Activity, wrap it back into container host (FIX-04)
        if (targetPkg != null && intentPkg == targetPkg && component != null) {
            val realClassName = component.className
            intent.putExtra(EXTRA_REAL_ACTIVITY, realClassName)
            intent.putExtra(StubActivity.EXTRA_PACKAGE_NAME, targetPkg)
            intent.setClassName("com.sandbox.ball", StubActivity::class.java.name)
            Log.i(TAG, "execStartActivity wrapped: $realClassName into :container process com.sandbox.ball")
        }

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
            Log.e(TAG, "execStartActivity forward notice: ${e.message}")
            null
        }
    }

    override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
        val realActivity = intent?.getStringExtra(EXTRA_REAL_ACTIVITY)
        if (!realActivity.isNullOrEmpty() && activeClassLoader != null) {
            try {
                val actClass = activeClassLoader!!.loadClass(realActivity)
                val act = actClass.getDeclaredConstructor().newInstance() as Activity
                Log.i(TAG, "newActivity loaded target activity: $realActivity via container classloader")
                return act
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading $realActivity via container classloader: ${e.message}")
            }
        }
        return try {
            base?.newActivity(cl, className, intent) ?: super.newActivity(cl, className, intent)
        } catch (_: Exception) {
            super.newActivity(cl, className, intent)
        }
    }

    override fun callActivityOnCreate(activity: Activity?, icicle: Bundle?) {
        if (activity != null && activeContainerContext != null && activity !is StubActivity) {
            try {
                // Inject ContainerContext as mBase on ContextWrapper
                val baseField = ContextWrapper::class.java.getDeclaredField("mBase")
                baseField.isAccessible = true
                baseField.set(activity, activeContainerContext)

                // Inject Resources on ContextThemeWrapper if present
                try {
                    val themeWrapperClass = Class.forName("android.view.ContextThemeWrapper")
                    val resField = themeWrapperClass.getDeclaredField("mResources")
                    resField.isAccessible = true
                    resField.set(activity, activeContainerContext!!.resources)
                } catch (_: Exception) {}

                Log.d(TAG, "ContainerContext injected into ${activity.javaClass.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not inject ContainerContext into activity: ${e.message}")
            }
        }
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
