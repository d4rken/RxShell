package eu.darken.rxshell.extra;


import android.support.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import eu.darken.rxshell.BuildConfig;
import timber.log.Timber;

public class RXSDebug {
    private static final String TAG = "RXS:Debug";
    private static boolean DEBUG = BuildConfig.DEBUG;

    public static void setDebug(boolean debug) {
        Timber.tag(TAG).i("setDebug(debug=%b)", debug);
        DEBUG = debug;
    }

    public static boolean isDebug() {
        return DEBUG;
    }

    @VisibleForTesting final static Set<Callback> CALLBACKS = Collections.synchronizedSet(new HashSet<>());

    private static Set<ProcessCallback> getProcessCallbacks() {
        if (CALLBACKS.isEmpty()) return Collections.emptySet();
        Set<ProcessCallback> callbacks;
        synchronized (CALLBACKS) {
            callbacks = new HashSet<>();
            for (Callback callback : CALLBACKS) {
                if (callback instanceof ProcessCallback) {
                    callbacks.add((ProcessCallback) callback);
                }
            }
        }
        return callbacks;
    }

    public static void notifyOnProcessStart(Process process) {
        for (ProcessCallback c : getProcessCallbacks()) {
            c.onProcessStart(process);
        }
    }

    public static void notifyOnProcessEnd(Process process) {
        for (ProcessCallback c : getProcessCallbacks()) {
            c.onProcessEnd(process);
        }
    }

    public interface Callback {

    }

    public interface ProcessCallback extends Callback {
        void onProcessStart(Process process);

        void onProcessEnd(Process process);
    }

    public static void addCallback(Callback callback) {
        CALLBACKS.add(callback);
    }

    public static void removeCallback(Callback callback) {
        CALLBACKS.remove(callback);
    }
}
