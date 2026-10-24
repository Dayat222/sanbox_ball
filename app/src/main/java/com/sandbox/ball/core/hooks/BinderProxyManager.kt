package com.sandbox.ball.core.hooks

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.wifi.WifiInfo
import android.os.Build
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

            // Hook wifi service (FIX-05)
            hookServiceBinder(smClass, sCache, "wifi") { realBinder ->
                createWifiProxyBinder(realBinder, profile)
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

    private fun createWifiProxyBinder(realBinder: IBinder, profile: HardwareProfile): IBinder {
        return Proxy.newProxyInstance(
            realBinder.javaClass.classLoader,
            arrayOf(IBinder::class.java),
            InvocationHandler { _, method, args ->
                if (method.name == "queryLocalInterface") {
                    val descriptor = args?.getOrNull(0) as? String
                    if (descriptor?.contains("IWifiManager") == true) {
                        return@InvocationHandler createWifiInterfaceProxy(realBinder, descriptor, profile)
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

    private fun createWifiInterfaceProxy(
        realBinder: IBinder,
        descriptor: String,
        profile: HardwareProfile
    ): Any {
        val targetInterface = try {
            Class.forName(descriptor)
        } catch (_: Exception) {
            Class.forName("android.net.wifi.IWifiManager")
        }
        val realInterface = realBinder.queryLocalInterface(descriptor)

        return Proxy.newProxyInstance(
            targetInterface.classLoader,
            arrayOf(targetInterface, IInterface::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "getConnectionInfo" -> {
                        val realInfo = invokeReal(realInterface ?: realBinder, method, args)
                        if (realInfo is WifiInfo) {
                            spoofWifiInfo(realInfo, profile)
                        }
                        realInfo
                    }
                    "getFactoryMacAddresses" -> arrayOf(profile.macAddress)
                    "asBinder" -> realBinder
                    else -> invokeReal(realInterface ?: realBinder, method, args)
                }
            }
        )
    }

    private fun spoofWifiInfo(wifiInfo: WifiInfo, profile: HardwareProfile) {
        try {
            val clazz = wifiInfo.javaClass
            setFieldValue(clazz, wifiInfo, "mMacAddress", profile.macAddress)
            setFieldValue(clazz, wifiInfo, "mBSSID", profile.wifiBssid)
            setFieldValue(clazz, wifiInfo, "mIpAddress", WifiHook.getIpAddressInt())
        } catch (_: Exception) {}
    }

    private fun setFieldValue(clazz: Class<*>, target: Any, fieldName: String, value: Any) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
        } catch (_: Exception) {}
    }

    private class PackageManagerInvocationHandler(private val realObject: Any) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            val methodName = method.name

            // Intercept Play Store installer source (Anti-Shadowban)
            if (methodName == "getInstallerPackageName") {
                val pkg = args?.getOrNull(0) as? String ?: ""
                return PackageManagerHook.getInstallerPackageName(pkg)
            }

            if (methodName == "getInstallSourceInfo") {
                return createFakeInstallSourceInfo()
            }

            // Filter package visibility
            if (methodName == "getPackageInfo") {
                val pkgName = args?.getOrNull(0) as? String
                if (pkgName != null && !PackageManagerHook.isPackageVisible(pkgName)) {
                    throw android.content.pm.PackageManager.NameNotFoundException("Package hidden by stealth container: $pkgName")
                }
            }

            // Filter package lists (FIX-05)
            if (methodName == "getInstalledPackages") {
                val raw = invokeReal(realObject, method, args) ?: return null
                return filterParceledOrList(raw) { list ->
                    @Suppress("UNCHECKED_CAST")
                    PackageManagerHook.filterPackageInfoList(list as? List<PackageInfo>)
                }
            }

            if (methodName == "getInstalledApplications") {
                val raw = invokeReal(realObject, method, args) ?: return null
                return filterParceledOrList(raw) { list ->
                    @Suppress("UNCHECKED_CAST")
                    PackageManagerHook.filterApplicationInfoList(list as? List<ApplicationInfo>)
                }
            }

            if (methodName == "queryIntentActivities") {
                val raw = invokeReal(realObject, method, args) ?: return null
                return filterParceledOrList(raw) { list ->
                    @Suppress("UNCHECKED_CAST")
                    PackageManagerHook.filterResolveInfoList(list as? List<ResolveInfo>)
                }
            }

            return invokeReal(realObject, method, args)
        }

        private fun filterParceledOrList(raw: Any, filterAction: (List<*>) -> List<*>): Any {
            return try {
                val clazz = raw.javaClass
                if (clazz.name.contains("ParceledListSlice")) {
                    val getListMethod = clazz.getMethod("getList")
                    val originalList = getListMethod.invoke(raw) as? List<*> ?: emptyList<Any>()
                    val filtered = filterAction(originalList)
                    val constructor = clazz.getConstructor(List::class.java)
                    constructor.newInstance(filtered)
                } else if (raw is List<*>) {
                    filterAction(raw)
                } else {
                    raw
                }
            } catch (_: Exception) {
                raw
            }
        }

        private fun createFakeInstallSourceInfo(): Any? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return try {
                    val clazz = Class.forName("android.content.pm.InstallSourceInfo")
                    for (ctor in clazz.declaredConstructors) {
                        ctor.isAccessible = true
                        val paramTypes = ctor.parameterTypes
                        val args = arrayOfNulls<Any>(paramTypes.size)
                        for (i in paramTypes.indices) {
                            if (paramTypes[i] == String::class.java) {
                                args[i] = "com.android.vending"
                            }
                        }
                        return ctor.newInstance(*args)
                    }
                    null
                } catch (_: Exception) {
                    null
                }
            }
            return null
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
