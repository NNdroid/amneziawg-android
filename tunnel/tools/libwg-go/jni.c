/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright © 2017-2021 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>

struct go_string { const char *str; long n; };
extern int awgTurnOn(struct go_string ifname, int tun_fd, struct go_string settings);
extern void awgTurnOff(int handle);
extern int awgGetSocketV4(int handle);
extern int awgGetSocketV6(int handle);
extern char *awgGetConfig(int handle);
extern char *awgVersion();
extern void awgSetSocketProtect(int handle);

/*
 * Socket protection for IP4P bootstrap DNS.
 *
 * The Go backend creates short-lived sockets to query the bootstrap DNS
 * resolvers while the tunnel is up. Without protection those queries would be
 * routed back through the VPN itself (a loop) or blocked, so IP4P resolution
 * fails. We keep a global reference to the active VpnService so the Go side can
 * call VpnService.protect(fd) on each DNS socket before dialing.
 */
static JavaVM *g_vm = NULL;
static jobject g_vpn_service = NULL;

int awgProtectSocket(int fd)
{
	JNIEnv *env;
	jclass cls;
	jmethodID mid;
	jboolean ok;
	int attach;

	if (!g_vm || !g_vpn_service)
		return -1;

	attach = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
	if (attach == JNI_EDETACHED) {
		if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) != JNI_OK)
			return -1;
	} else if (attach != JNI_OK) {
		return -1;
	}

	cls = (*env)->GetObjectClass(env, g_vpn_service);
	if (!cls)
		goto out;
	mid = (*env)->GetMethodID(env, cls, "protect", "(I)Z");
	if (!mid)
		goto out;
	ok = (*env)->CallBooleanMethod(env, g_vpn_service, mid, fd);

out:
	if (attach == JNI_EDETACHED)
		(*g_vm)->DetachCurrentThread(g_vm);
	return ok ? 0 : -1;
}

void awgEnableSocketProtect(int handle)
{
	/* Reserved for future per-tunnel diagnostics; currently a no-op. */
	(void)handle;
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgTurnOn(JNIEnv *env, jclass c, jstring ifname, jint tun_fd, jstring settings)
{
	const char *ifname_str = (*env)->GetStringUTFChars(env, ifname, 0);
	size_t ifname_len = (*env)->GetStringUTFLength(env, ifname);
	const char *settings_str = (*env)->GetStringUTFChars(env, settings, 0);
	size_t settings_len = (*env)->GetStringUTFLength(env, settings);
	int ret = awgTurnOn((struct go_string){
		.str = ifname_str,
		.n = ifname_len
	}, tun_fd, (struct go_string){
		.str = settings_str,
		.n = settings_len
	});
	(*env)->ReleaseStringUTFChars(env, ifname, ifname_str);
	(*env)->ReleaseStringUTFChars(env, settings, settings_str);
	return ret;
}

JNIEXPORT void JNICALL Java_org_amnezia_awg_GoBackend_awgTurnOff(JNIEnv *env, jclass c, jint handle)
{
	awgTurnOff(handle);
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgGetSocketV4(JNIEnv *env, jclass c, jint handle)
{
	return awgGetSocketV4(handle);
}

JNIEXPORT jint JNICALL Java_org_amnezia_awg_GoBackend_awgGetSocketV6(JNIEnv *env, jclass c, jint handle)
{
	return awgGetSocketV6(handle);
}

/*
 * Register the active VpnService so the Go backend can protect bootstrap DNS
 * sockets, then enable protection on the tunnel.
 */
JNIEXPORT void JNICALL Java_org_amnezia_awg_GoBackend_awgSetSocketProtect(JNIEnv *env, jclass c, jint handle, jobject vpnService)
{
	(*env)->GetJavaVM(env, &g_vm);
	if (g_vpn_service)
		(*env)->DeleteGlobalRef(env, g_vpn_service);
	g_vpn_service = (*env)->NewGlobalRef(env, vpnService);
	awgSetSocketProtect(handle);
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_GoBackend_awgGetConfig(JNIEnv *env, jclass c, jint handle)
{
	jstring ret;
	char *config = awgGetConfig(handle);
	if (!config)
		return NULL;
	ret = (*env)->NewStringUTF(env, config);
	free(config);
	return ret;
}

JNIEXPORT jstring JNICALL Java_org_amnezia_awg_GoBackend_awgVersion(JNIEnv *env, jclass c)
{
	jstring ret;
	char *version = awgVersion();
	if (!version)
		return NULL;
	ret = (*env)->NewStringUTF(env, version);
	free(version);
	return ret;
}
