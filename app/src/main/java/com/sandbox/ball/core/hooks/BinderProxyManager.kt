package com.sandbox.ball.core.hooks

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.sandbox.ball.core.spoofer.HardwareProfile
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object BinderProxyManager {
    private const val TAG = "BinderProxyManager"
    private var isHooked = false

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun install(profile: HardwareProfile, targetPkg: String? = null) {
        if (isHooked) return

        try {
            hookPackageManager(profile, targetPkg)
            hookServiceManager(profile)
            isHooked = true
            Log.i(TAG, "BinderProxyManager: All IPC binder proxies successfully installed")
        } catch (e: Exception) {
            Log.e(TAG, "BinderProxyManager installation failed: ${e.message}", e)
        }
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun hookPackageManager(profile: HardwareProfile, targetPkg: String?) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val getPackageManagerMethod = activityThreadClass.getDeclaredMethod("getPackageManager")
            getPackageManagerMethod.isAccessible = true
            val realPm = getPackageManagerMethod.invoke(null) ?: return

            val iPmInterface = Class.forName("android.content.pm.IPackageManager")
            val proxyPm = Proxy.newProxyInstance(
                iPmInterface.classLoader,
                arrayOf(iPmInterface),
                PackageManagerInvocationHandler(realPm)
            )

            // Override ActivityThread.sPackageManager
            val sPmField = activityThreadClass.getDeclaredField("sPackageManager")
            sPmField.isAccessible = true
            sPmField.set(null, proxyPm)

            Log.i(TAG, "ActivityThread.sPackageManager successfully replaced with dynamic proxy")
        } catch (e: Exception) {
            Log.w(TAG, "Could not hook ActivityThread.sPackageManager: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun hookServiceManager(profile: HardwareProfile) {
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val sCacheField = smClass.getDeclaredField("sCache")
            sCacheField.isAccessible = true
            val sCache = sCacheField.get(null) as? MutableMap<String, IBinder> ?: return

            // Hook telephony service
            hookServiceBinder(smClass, sCache, "phone") { realBinder ->
                createTelephonyProxyBinder(realBinder, profile)
            }

            // Hook iphonesubinfo service
            hookServiceBinder(smClass, sCache, "iphonesubinfo") { realBinder ->
                createPhoneSubInfoProxyBinder(realBinder, profile)
            }

            // Hook package manager service
            hookServiceBinder(smClass, sCache, "package") { realBinder ->
                createPackageManagerProxyBinder(realBinder)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Could not hook ServiceManager.sCache: ${e.message}")
        }
    }

    private fun hookServiceBinder(
        smClass: Class<*>,
        sCache: MutableMap<String, IBinder>,
        serviceName: String,
        proxyFactory: (IBinder) -> IBinder
    ) {
        try {
            val getServiceMethod = smClass.getDeclaredMethod("getService", String::class.java)
            val realBinder = getServiceMethod.invoke(null, serviceName) as? IBinder ?: return
            val proxyBinder = proxyFactory(realBinder)
            sCache[serviceName] = proxyBinder
            Log.i(TAG, "ServiceManager service '$serviceName' hooked in sCache")
        } catch (e: Exception) {
            Log.w(TAG, "Failed hooking service '$serviceName': ${e.message}")
        }
    }

    private fun createPackageManagerProxyBinder(realBinder: IBinder): IBinder {
        return Proxy.newProxyInstance(
            realBinder.javaClass.classLoader,
            arrayOf(IBinder::class.java),
            InvocationHandler { _, method, args ->
                if (method.name == "queryLocalInterface") {
                    val descriptor = args?.getOrNull(0) as? String
                    if (descriptor == "android.content.pm.IPackageManager") {
                        val realInterface = realBinder.queryLocalInterface(descriptor)
                        val iPmInterface = Class.forName("android.content.pm.IPackageManager")
                        return@InvocationHandler Proxy.newProxyInstance(
                            iPmInterface.classLoader,
                            arrayOf(iPmInterface),
                            PackageManagerInvocationHandler(realInterface ?: realBinder)
                        )
                    }
                }
                invokeReal(realBinder, method, args)
            }
        ) as IBinder
    }

    private fun createTelephonyProxyBinder(realBinder: IBinder, profile: HardwareProfile): IBinder {
        return Proxy.newProxyInstance(
            realBinder.javaClass.classLoader,
            arrayOf(IBinder::class.java),
            InvocationHandler { _, method, args ->
                if (method.name == "queryLocalInterface") {
                    val descriptor = args?.getOrNull(0) as? String
                    if (descriptor?.contains("ITelephony") == true) {
                        return@InvocationHandler createTelephonyInterfaceProxy(realBinder, descriptor, profile)
                    }
                }
                invokeReal(realBinder, method, args)
            }
        ) as IBinder
    }

    private fun createPhoneSubInfoProxyBinder(realBinder: IBinder, profile: HardwareProfile): IBinder {
        return Proxy.newProxyInstance(
            realBinder.javaClass.classLoader,
            arrayOf(IBinder::class.java),
            InvocationHandler { _, method, args ->
                if (method.name == "queryLocalInterface") {
                    val descriptor = args?.getOrNull(0) as? String
                    if (descriptor?.contains("IPhoneSubInfo") == true) {
                        return@InvocationHandler createPhoneSubInfoInterfaceProxy(realBinder, descriptor, profile)
                    }
                }
                invokeReal(realBinder, method, args)
            }
        ) as IBinder
    }

    private fun createTelephonyInterfaceProxy(
        realBinder: IBinder,
        descriptor: String,
        profile: HardwareProfile
    ): Any {
        val targetInterface = try {
            Class.forName(descriptor)
        } catch (_: Exception) {
            Class.forName("com.android.internal.telephony.ITelephony")
        }
        val realInterface = realBinder.queryLocalInterface(descriptor)

        return Proxy.newProxyInstance(
            targetInterface.classLoader,
            arrayOf(targetInterface, IInterface::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "getDeviceId", "getDeviceIdWithFeature", "getImeiForSlot" -> profile.imei
                    "getMeid", "getMeidForSlot" -> profile.meid
                    "getNetworkOperator", "getSimOperator" -> profile.networkOperator
                    "getNetworkOperatorName", "getSimOperatorName" -> profile.networkOperatorName
                    "getLine1Number", "getLine1NumberForSubscriber" -> profile.phoneNumber
                    "getSubscriberId", "getSubscriberIdForSubscriber" -> profile.imsi
                    "getSimSerialNumber", "getSimSerialNumberForSubscriber" -> profile.simSerialNumber
                    "asBinder" -> realBinder
                    else -> invokeReal(realInterface ?: realBinder, method, args)
                }
            }
        )
    }

    private fun createPhoneSubInfoInterfaceProxy(
        realBinder: IBinder,
        descriptor: String,
        profile: HardwareProfile
    ): Any {
        val targetInterface = try {
            Class.forName(descriptor)
        } catch (_: Exception) {
            Class.forName("com.android.internal.telephony.IPhoneSubInfo")
        }
        val realInterface = realBinder.queryLocalInterface(descriptor)

        return Proxy.newProxyInstance(
            targetInterface.classLoader,
            arrayOf(targetInterface, IInterface::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "getDeviceId", "getDeviceIdForPhone" -> profile.imei
                    "getImeiForSubscriber" -> profile.imei
                    "getSubscriberId", "getSubscriberIdForSubscriber" -> profile.imsi
                    "getIccSerialNumber", "getIccSerialNumberForSubscriber" -> profile.simSerialNumber
                    "getLine1Number", "getLine1NumberForSubscriber" -> profile.phoneNumber
                    "asBinder" -> realBinder
                    else -> invokeReal(realInterface ?: realBinder, method, args)
                }
            }
        )
    }

    private class PackageManagerInvocationHandler(private val realObject: Any) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            val methodName = method.name

            // Intercept Play Store installer source (Anti-Shadowban)
            if (methodName == "getInstallerPackageName") {
                val pkg = args?.getOrNull(0) as? String ?: ""
                return PackageManagerHook.getInstallerPackageName(pkg)
            }

            // Filter package visibility
            if (methodName == "getPackageInfo") {
                val pkgName = args?.getOrNull(0) as? String
                if (pkgName != null && !PackageManagerHook.isPackageVisible(pkgName)) {
                    throw android.content.pm.PackageManager.NameNotFoundException("Package hidden by stealth container: $pkgName")
                }
            }

            return invokeReal(realObject, method, args)
        }
    }

    private fun invokeReal(target: Any, method: Method, args: Array<out Any>?): Any? {
        return try {
            if (args == null) {
                method.invoke(target)
            } else {
                method.invoke(target, *args)
            }
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}
