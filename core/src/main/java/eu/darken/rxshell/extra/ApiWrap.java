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
     * @return if &gt;=17
     */
    public static boolean hasJellyBeanMR1() {
        return getCurrentSDKInt() >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    /**
     * @return if &gt;=18
     */
    public static boolean hasJellyBeanMR2() {
        return getCurrentSDKInt() >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    }

    /**
     * @return if &gt;=19
     */
    public static boolean hasKitKat() {
        return getCurrentSDKInt() >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * @return if &gt;=26
     */
    public static boolean hasOreo() {
        return getCurrentSDKInt() >= Build.VERSION_CODES.O;
    }
}
