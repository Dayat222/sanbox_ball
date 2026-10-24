#include "stealth_engine.h"
#include "vfs/vfs_core.h"
#include "hook/syscall_hook.h"
#include "utils/log.h"

#include <string>
#include <sstream>

using stealth::vfs::VfsCore;

static std::string jstringToStdString(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    std::string result(utf);
    env->ReleaseStringUTFChars(jstr, utf);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeInitEngine(
        JNIEnv* env, jobject /* thiz */,
        jstring virtualRootDir, jstring targetPkg,
        jstring fakeMac, jstring fakeSerial) {
    std::string rootDir = jstringToStdString(env, virtualRootDir);
    std::string pkg = jstringToStdString(env, targetPkg);
    std::string mac = jstringToStdString(env, fakeMac);
    std::string serial = jstringToStdString(env, fakeSerial);

    VfsCore::getInstance().init(rootDir, pkg, mac, serial);
    stealth_install_hooks();
    stealth_set_antidebug_enabled(1);

    ALOGI("Stealth Engine initialized successfully for: %s", pkg.c_str());
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetVfsEnabled(
        JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    VfsCore::getInstance().setEnabled(enabled == JNI_TRUE);
    ALOGI("VFS enabled state set to: %d", enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetAntiDebugEnabled(
        JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    stealth_set_antidebug_enabled(enabled == JNI_TRUE ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetMapsFilterEnabled(
        JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    VfsCore::getInstance().setMapsFilterEnabled(enabled == JNI_TRUE);
    ALOGI("Maps filter state set to: %d", enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeAddPathMapping(
        JNIEnv* env, jobject /* thiz */, jstring virtualPath, jstring realPath) {
    std::string vPath = jstringToStdString(env, virtualPath);
    std::string rPath = jstringToStdString(env, realPath);
    VfsCore::getInstance().addPathMapping(vPath, rPath);
}

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeAddPrefixMapping(
        JNIEnv* env, jobject /* thiz */, jstring virtualPrefix, jstring realPrefix) {
    std::string vPrefix = jstringToStdString(env, virtualPrefix);
    std::string rPrefix = jstringToStdString(env, realPrefix);
    VfsCore::getInstance().addPrefixMapping(vPrefix, rPrefix);
}

JNIEXPORT jstring JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeGetEngineStatus(
        JNIEnv* env, jobject /* thiz */) {
    bool vfsActive = VfsCore::getInstance().isEnabled();
    bool mapsActive = VfsCore::getInstance().isMapsFilterEnabled();
    int antiDebug = stealth_is_antidebug_enabled();
    std::string pkg = VfsCore::getInstance().getTargetPackage();

    std::ostringstream json;
    json << "{"
         << "\"vfs_enabled\":" << (vfsActive ? "true" : "false") << ","
         << "\"maps_filter_enabled\":" << (mapsActive ? "true" : "false") << ","
         << "\"antidebug_bypass\":" << (antiDebug ? "true" : "false") << ","
         << "\"target_package\":\"" << pkg << "\""
         << "}";

    return env->NewStringUTF(json.str().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeApplyPltHooks(
        JNIEnv* /* env */, jobject /* thiz */) {
    return (stealth_apply_plt_hooks() == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetBuildProp(
        JNIEnv* env, jobject /* thiz */, jstring fieldName, jstring fieldValue) {
    if (!fieldName || !fieldValue) return JNI_FALSE;
    jclass buildClass = env->FindClass("android/os/Build");
    if (!buildClass) return JNI_FALSE;

    const char* fName = env->GetStringUTFChars(fieldName, nullptr);
    jfieldID fid = env->GetStaticFieldID(buildClass, fName, "Ljava/lang/String;");
    env->ReleaseStringUTFChars(fieldName, fName);

    if (!fid) {
        env->ExceptionClear();
        return JNI_FALSE;
    }

    env->SetStaticObjectField(buildClass, fid, fieldValue);
    return JNI_TRUE;
}
