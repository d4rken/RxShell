package eu.darken.rxshell.root;

import android.content.Context;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.Locale;

import eu.darken.rxshell.cmd.RxCmdShell;
import eu.darken.rxshell.extra.CmdHelper;
import eu.darken.rxshell.extra.RxCmdShellHelper;
import io.reactivex.Single;
import timber.log.Timber;


public class RootContext {

    public static final RootContext EMPTY = new RootContext(
            new Root(Root.State.UNAVAILABLE),
            new SuBinary(SuBinary.Type.NONE, null, null, null, null),
            new SuApp(SuBinary.Type.NONE, null, null, null, null),
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
    private final Root root;

    public RootContext(Root root, SuBinary suBinary, SuApp suApp, SELinux seLinux, ContextSwitch contextSwitch) {
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
        @Nullable private RxCmdShell.Builder shellBuilder;
        @Nullable private SELinux.Builder seLinuxBuilder;
        @Nullable private SuBinary.Builder suBinaryBuilder;
        @Nullable private SuApp.Builder suAppBuilder;
        @Nullable private Root.Builder rootBuilder;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder shellBuilder(@Nullable RxCmdShell.Builder builder) {
            this.shellBuilder = builder;
            return this;
        }

        public Builder rootBuilder(@Nullable Root.Builder rootBuilder) {
            this.rootBuilder = rootBuilder;
            return this;
        }

        public Builder suAppBuilder(@Nullable SuApp.Builder suAppBuilder) {
            this.suAppBuilder = suAppBuilder;
            return this;
        }

        public Builder suBinaryBuilder(@Nullable SuBinary.Builder suBinaryBuilder) {
            this.suBinaryBuilder = suBinaryBuilder;
            return this;
        }

        public Builder seLinuxBuilder(@Nullable SELinux.Builder seLinuxBuilder) {
            this.seLinuxBuilder = seLinuxBuilder;
            return this;
        }

        public Single<RootContext> build() {
            return Single.create(emitter -> {
                Timber.tag(TAG).d("Building RootContext...");
                RxCmdShell.Session shell = null;
                try {
                    if (shellBuilder == null) shellBuilder = RxCmdShell.builder();
                    shell = RxCmdShellHelper.blockingOpen(shellBuilder);

                    if (seLinuxBuilder == null) seLinuxBuilder = new SELinux.Builder();
                    SELinux seLinux = seLinuxBuilder.session(shell).build().blockingGet();
                    Timber.tag(TAG).d("SeLinux: %s", seLinux);

                    if (suBinaryBuilder == null) suBinaryBuilder = new SuBinary.Builder();
                    SuBinary suBinary = suBinaryBuilder.session(shell).build().blockingGet();
                    Timber.tag(TAG).d("SuBinary: %s", suBinary);

                    if (suAppBuilder == null) suAppBuilder = new SuApp.Builder(context.getPackageManager());
                    SuApp suApp = suAppBuilder.build(suBinary).blockingGet();
                    Timber.tag(TAG).d("SuApp: %s", suApp);

                    if (rootBuilder == null) rootBuilder = new Root.Builder();
                    Root root = rootBuilder.suBinary(suBinary).build().blockingGet();
                    Timber.tag(TAG).d("Root: %s", root);

                    ContextSwitch contextSwitch;
                    if (suBinary.getType() == SuBinary.Type.CHAINFIRE_SUPERSU) {
                        contextSwitch = (context, command) -> "su --context " + context + " -c " + CmdHelper.san(command) + " < /dev/null";
                    } else {
                        contextSwitch = (context, command) -> command;
                    }
                    emitter.onSuccess(new RootContext(root, suBinary, suApp, seLinux, contextSwitch));
                } catch (IOException e) {
                    emitter.onError(e);
                } finally {
                    RxCmdShellHelper.blockingClose(shell);
                }
            });
        }
    }
}
