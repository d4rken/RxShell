package eu.darken.rxshell.extra;

import android.os.Build;

public class ApiWrap {
    static int SDK_INT = Build.VERSION.SDK_INT;

    public static int getCurrentSDKInt() {
        return SDK_INT;
    }

    public static void setSDKInt(int sdkInt) {
        SDK_INT = sdkInt;
    }

    /**
     * @return if >=19
     */
    public static boolean hasKitKat() {
        return getCurrentSDKInt() >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * @return if >=18
     */
    public static boolean hasJellyBeanMR2() {
        return getCurrentSDKInt() >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    }

    /**
     * @return if >=26
     */
    public static boolean hasOreo() {
        return getCurrentSDKInt() >= Build.VERSION_CODES.O;
    }
}
