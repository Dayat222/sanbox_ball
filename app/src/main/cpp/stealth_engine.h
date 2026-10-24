#ifndef STEALTH_ENGINE_H
#define STEALTH_ENGINE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeInitEngine(
        JNIEnv* env, jobject thiz,
        jstring virtualRootDir, jstring targetPkg,
        jstring fakeMac, jstring fakeSerial);

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetVfsEnabled(
        JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetAntiDebugEnabled(
        JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetMapsFilterEnabled(
        JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeAddPathMapping(
        JNIEnv* env, jobject thiz, jstring virtualPath, jstring realPath);

JNIEXPORT void JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeAddPrefixMapping(
        JNIEnv* env, jobject thiz, jstring virtualPrefix, jstring realPrefix);

JNIEXPORT jstring JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeGetEngineStatus(
        JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeApplyPltHooks(
        JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL
Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetBuildProp(
        JNIEnv* env, jobject thiz, jstring fieldName, jstring fieldValue);

#ifdef __cplusplus
}
#endif

#endif // STEALTH_ENGINE_H
