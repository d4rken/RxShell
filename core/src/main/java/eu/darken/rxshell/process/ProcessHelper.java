package eu.darken.rxshell.process;

import android.annotation.SuppressLint;

import eu.darken.rxshell.extra.ApiWrap;


public class ProcessHelper {
    @SuppressLint("NewApi")
    public static boolean isAlive(Process process) {
        if (ApiWrap.hasOreo()) {
            return process.isAlive();
        } else {
            try {
                process.exitValue();
                return false;
            } catch (IllegalThreadStateException e) {
                return true;
            }
        }
    }
}
