package com.yachiha.needleandroid;

final class NeedleNative {
    static {
        System.loadLibrary("needle_jni");
    }

    private NeedleNative() {}

    static native String initialize(byte[] model);
    static native String complete(String prompt);
    static native void reset();
    static native boolean isReady();
}
