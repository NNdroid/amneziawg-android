package org.amnezia.awg;

import android.net.VpnService;

import androidx.annotation.Nullable;

public class GoBackend {
    @Nullable
    public static native String awgGetConfig(int handle);

    public static native int awgGetSocketV4(int handle);

    public static native int awgGetSocketV6(int handle);

    public static native void awgTurnOff(int handle);

    public static native int awgTurnOn(String ifName, int tunFd, String settings);

    /**
     * Registers the active {@link android.net.VpnService} with the Go backend so
     * that IP4P bootstrap DNS sockets are protected (routed outside the tunnel).
     *
     * @param handle      the tunnel handle returned by {@link #awgTurnOn}
     * @param vpnService  the active VpnService instance
     */
    public static native void awgSetSocketProtect(int handle, VpnService vpnService);

    public static native String awgVersion();
}
