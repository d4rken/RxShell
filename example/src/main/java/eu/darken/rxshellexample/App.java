package eu.darken.rxshellexample;

import android.app.Application;

import eu.darken.rxshell.extra.RXSDebug;
import timber.log.Timber;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        RXSDebug.addCallback(new RXSDebug.ProcessCallback() {
            @Override
            public void onProcessStart(Process process) {
                Timber.i("Process started: %s", process);
            }

            @Override
            public void onProcessEnd(Process process) {
                Timber.i("Process ended: %s", process);
            }
        });
    }
}
