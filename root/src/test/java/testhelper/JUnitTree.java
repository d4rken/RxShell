package testhelper;


import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class JUnitTree extends Timber.DebugTree {
    private final int minlogLevel;
    private final List<String> lines = new ArrayList<>();

    public JUnitTree() {
        minlogLevel = Log.VERBOSE;
    }

    public JUnitTree(int minlogLevel) {
        this.minlogLevel = minlogLevel;
    }

    private static String priorityToString(int priority) {
        switch (priority) {
            case Log.ERROR:
                return "E";
            case Log.WARN:
                return "W";
            case Log.INFO:
                return "I";
            case Log.DEBUG:
                return "D";
            case Log.VERBOSE:
                return "V";
            default:
                return String.valueOf(priority);
        }
    }

    @Override
    protected void log(int priority, String tag, @NonNull String message, Throwable t) {
        if (priority < minlogLevel) return;
        final String line = System.currentTimeMillis() + " " + priorityToString(priority) + "/" + tag + ": " + message;
        lines.add(line);
        System.out.println(line);
    }

    public List<String> getLines() {
        return lines;
    }
}