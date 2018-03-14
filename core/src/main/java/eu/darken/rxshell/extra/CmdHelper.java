package eu.darken.rxshell.extra;


import android.support.annotation.Nullable;

public class CmdHelper {
    /**
     * Sanitize command line input
     */
    @Nullable
    public static String san(@Nullable String input) {
        if (input == null) return null;
        return "'" + input.replace("'", "'\\''") + "'";
    }
}
