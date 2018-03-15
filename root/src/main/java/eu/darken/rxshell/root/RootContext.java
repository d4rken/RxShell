package eu.darken.rxshell.root;

import android.content.Context;
import android.support.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.Locale;

import eu.darken.rxshell.cmd.RxCmdShell;
import eu.darken.rxshell.extra.RxCmdShellHelper;
import io.reactivex.Single;
import timber.log.Timber;


public class RootContext {

    public static final RootContext EMPTY = new RootContext(
            new Root(Root.State.UNAVAILABLE),
            new SuBinary(SuBinary.Type.NONE, null, null, null),
            null,
            new SELinux(SELinux.State.ENFORCING),
            (context, command) -> command
    );

    public interface ContextSwitch {
        String switchContext(String context, String command);
    }

    private final SuBinary suBinary;
    private final ContextSwitch contextSwitch;
    private final SELinux seLinux;
    private final SuApp suApp;
    private Root root;

    RootContext(Root root, SuBinary suBinary, SuApp suApp, SELinux seLinux, ContextSwitch contextSwitch) {
        this.root = root;
        this.suBinary = suBinary;
        this.seLinux = seLinux;
        this.contextSwitch = contextSwitch;
        this.suApp = suApp;
    }

    public boolean isRooted() {
        return root.getState() == Root.State.ROOTED;
    }

    public Root getRoot() {
        return root;
    }

    public SuBinary getSuBinary() {
        return suBinary;
    }

    public SuApp getSuApp() {
        return suApp;
    }

    public SELinux getSELinux() {
        return seLinux;
    }

    public ContextSwitch getContextSwitch() {
        return contextSwitch;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "RootContext(rootState=%s", root);
    }

    public static class Builder {
        static final String TAG = "RXS:Root:RootContext";
        private final Context context;
        private final RxCmdShell.Builder builder;

        public Builder(Context context) {
            this(context, new RxCmdShell.Builder());
        }

        @VisibleForTesting
        Builder(Context context, RxCmdShell.Builder builder) {
            this.context = context;
            this.builder = builder;
        }

        public Single<RootContext> build() {
            return Single.create(emitter -> {
                Timber.tag(TAG).d("Building RootContext...");
                RxCmdShell.Session shell = null;
                try {
                    shell = RxCmdShellHelper.blockingOpen(builder);

                    SELinux seLinux = new SELinux.Builder(shell).build().blockingGet();
                    Timber.tag(TAG).d("SeLinux: %s", seLinux);

                    SuBinary suBinary = new SuBinary.Builder(shell).build().blockingGet();
                    Timber.tag(TAG).d("SuBinary: %s", suBinary);

                    SuApp suApp = new SuApp.Builder(context.getPackageManager(), suBinary).build().blockingGet();
                    Timber.tag(TAG).d("SuApp: %s", suApp);

                    Root rootState = new Root.Builder(builder, suBinary).build().blockingGet();
                    Timber.tag(TAG).d("RootState: %s", rootState);

                    ContextSwitch contextSwitch;
                    if (suBinary.getType() == SuBinary.Type.CHAINFIRE_SUPERSU) {
                        contextSwitch = (context, command) -> "su --context " + context + " -c " + command + " < /dev/null";
                    } else {
                        contextSwitch = (context, command) -> command;
                    }
                    emitter.onSuccess(new RootContext(rootState, suBinary, suApp, seLinux, contextSwitch));
                } catch (IOException e) {
                    emitter.onError(e);
                } finally {
                    try {
                        RxCmdShellHelper.blockingClose(shell);
                    } catch (IOException e) {
                        Timber.tag(TAG).w(e);
                    }
                }
            });
        }
    }
}
