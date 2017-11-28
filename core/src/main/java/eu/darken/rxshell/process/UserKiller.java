package eu.darken.rxshell.process;

import android.annotation.SuppressLint;

import eu.darken.rxshell.extra.ApiWrap;
import eu.darken.rxshell.extra.RXSDebug;
import timber.log.Timber;


public class UserKiller implements ProcessKiller {
    private static final String TAG = "RXS:UserKiller";

    @SuppressLint("NewApi")
    @Override
    public boolean kill(Process process) {
        if (RXSDebug.isDebug()) Timber.tag(TAG).d("kill(%s)", process);
        if (!ProcessHelper.isAlive(process)) {
            if (RXSDebug.isDebug()) Timber.tag(TAG).d("Process is no longer alive, skipping kill.");
            return true;
        }

        if (ApiWrap.hasOreo()) process.destroyForcibly();
        else process.destroy();
        return true;
    }
}
