package eu.darken.rxshell.root;


import java.util.Collection;
import java.util.Locale;

import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import io.reactivex.Single;
import timber.log.Timber;

public class Root {

    public enum State {
        ROOTED, DENIED, UNAVAILABLE, RELINQUISHED
    }

    private final State state;

    Root(State state) {this.state = state;}

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
        static final String TAG = "Root:Factory";
        private final SuBinary suBinary;
        private final RxCmdShell.Builder shellBuilder;

        public Builder(RxCmdShell.Builder shellBuilder, SuBinary suBinary) {
            this.suBinary = suBinary;
            this.shellBuilder = shellBuilder.root(true);
        }

        public Single<Root> build() {
            return Single.create(emitter -> {
                State rootState = State.UNAVAILABLE;

                final Cmd.Result result = Cmd.builder("id").timeout(20 * 1000).execute(shellBuilder.build());
                boolean kingoRoot = suBinary.getType() == SuBinary.Type.KINGOUSER && result.getExitCode() == Cmd.ExitCode.OUTOFRANGE;

                if (kingoRoot) Timber.tag(TAG).w("KingoRoot workaround! Ignoring exitcode 255.");

                if (result.getExitCode() == Cmd.ExitCode.OK || kingoRoot) {
                    Collection<String> mergedOutput = result.merge();
                    for (String line : mergedOutput) {
                        if (line.contains("uid=0")) {
                            Timber.tag(TAG).d("We got ROOT on first try :D !");
                            rootState = State.ROOTED;
                        }
                    }
                } else if (result.getExitCode() == Cmd.ExitCode.EXCEPTION) {
                    // There was likely no su binary at all
                    Timber.tag(TAG).d("IOException when launching shell, no su binary?");
                } else if (result.getExitCode() == Cmd.ExitCode.PROBLEM || result.getExitCode() == Cmd.ExitCode.SHELL_DIED || result.getExitCode() == Cmd.ExitCode.TIMEOUT) {
                    // Either we were denied root or there was an error with one of the commands, lets switch up
                    Cmd.Result secondTry = Cmd.builder("echo test > /cache/root_test.tmp").timeout(20 * 1000).execute(shellBuilder.build());
                    rootState = secondTry.getExitCode() == Cmd.ExitCode.OK ? State.ROOTED : State.DENIED;
                    if (rootState == State.ROOTED) Timber.tag(TAG).d("We got ROOT on second try :o ?");
                }

                emitter.onSuccess(new Root(rootState));
            });
        }
    }
}
