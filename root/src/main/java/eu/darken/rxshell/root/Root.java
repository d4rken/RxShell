package eu.darken.rxshell.root;


import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import eu.darken.rxshell.extra.RxCmdShellHelper;
import io.reactivex.Single;
import timber.log.Timber;

public class Root {

    public enum State {
        /**
         * The device is rooted and access was granted (at least for the test).
         */
        ROOTED,
        /**
         * The device might be rooted, but access was denied.
         */
        DENIED,
        /**
         * Root is not available
         */
        UNAVAILABLE
    }

    private final State state;

    public Root(State state) {this.state = state;}

    public State getState() {
        return state;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Root(state=%s)", state.name());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Root root = (Root) o;

        return state == root.state;
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    public static class Builder {
        static final String TAG = "RXS:Root:Root";
        @Nullable private SuBinary suBinary;
        @Nullable private RxCmdShell.Builder shellBuilder;
        private long timeout = 20 * 1000;

        public Builder() {

        }

        /**
         * Use a specific shell builder instance
         */
        public Builder shellBuilder(@Nullable RxCmdShell.Builder builder) {
            this.shellBuilder = builder;
            return this;
        }

        /**
         * Some su binaries return faulty exit codes despite allowing root.
         * To handle that pass a su binary from {@link SuBinary.Builder}
         */
        public Builder suBinary(@Nullable SuBinary binary) {
            this.suBinary = binary;
            return this;
        }

        /**
         * Configure a timeout for opening a root shell and command execution.<p>
         * This includes the time the user takes to grant/deny a su manager app popup.
         *
         * @param timeout in milliseconds
         */
        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Single<Root> build() {
            return Single.create(emitter -> {
                RxCmdShell.Builder builder = shellBuilder;
                if (builder == null) builder = new RxCmdShell.Builder();
                builder = builder.root(true);

                RxCmdShell.Session session = null;
                Cmd.Result result;
                try {
                    try {
                        session = builder.build().open().timeout(timeout, TimeUnit.MILLISECONDS).blockingGet();
                    } catch (RuntimeException e) {
                        if (e.getCause() instanceof TimeoutException) {
                            Timber.tag(TAG).w("Waiting for su shell to open timed out after %dms", timeout);
                        } else if (e.getCause() instanceof IOException) {
                            Timber.tag(TAG).d("IOException when launching shell, likely no su binary!");
                        }
                        emitter.onSuccess(new Root(State.UNAVAILABLE));
                        return;
                    }
                    result = Cmd.builder("id").timeout(timeout).execute(session);
                } finally {
                    RxCmdShellHelper.blockingClose(session);
                }

                boolean kingoRoot = suBinary != null && suBinary.getType() == SuBinary.Type.KINGOUSER && result.getExitCode() == Cmd.ExitCode.OUTOFRANGE;

                if (kingoRoot) Timber.tag(TAG).w("KingoRoot workaround! Ignoring exitcode 255.");

                State rootState = State.UNAVAILABLE;
                if (result.getExitCode() == Cmd.ExitCode.OK || kingoRoot) {
                    Collection<String> mergedOutput = result.merge();
                    for (String line : mergedOutput) {
                        if (line.contains("uid=0")) {
                            Timber.tag(TAG).d("We got ROOT on first try :D !");
                            rootState = State.ROOTED;
                        }
                    }
                } else if (result.getExitCode() == Cmd.ExitCode.PROBLEM || result.getExitCode() == Cmd.ExitCode.SHELL_DIED || result.getExitCode() == Cmd.ExitCode.TIMEOUT) {
                    // Either we were denied root or there was an error with one of the commands, lets switch up
                    Cmd.Result secondTry = Cmd.builder("echo test > /cache/root_test.tmp").timeout(timeout).execute(builder.build());
                    rootState = secondTry.getExitCode() == Cmd.ExitCode.OK ? State.ROOTED : State.DENIED;
                    if (rootState == State.ROOTED) Timber.tag(TAG).d("We got ROOT on second try :o ?");
                }

                emitter.onSuccess(new Root(rootState));
            });
        }
    }
}
