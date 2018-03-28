package eu.darken.rxshell.root;


import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Locale;

import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import eu.darken.rxshell.extra.ApiWrap;
import io.reactivex.Single;
import timber.log.Timber;

public class SELinux {

    public enum State {
        DISABLED, PERMISSIVE, ENFORCING
    }

    private final State state;

    public SELinux(State state) {this.state = state;}

    public State getState() {
        return state;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "SELinux(state=%s)", state.name());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SELinux seLinux = (SELinux) o;

        return state == seLinux.state;
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    public static class Builder {
        static final String TAG = "RXS:Root:SELinux";

        private final static String SELINUX_GETENFORCE_DISABLED = "Disabled";
        private final static String SELINUX_GETENFORCE_PERMISSIVE = "Permissive";
        private final static String SELINUX_GETENFORCE_ENFORCING = "Enforcing";
        private RxCmdShell.Session session;

        public Builder() {

        }

        /**
         * If you want to reuse an existing session.
         */
        public Builder session(@Nullable RxCmdShell.Session shellSession) {
            this.session = shellSession;
            return this;
        }

        private Cmd.Result trySession(Cmd.Builder cmdBuilder) {
            if (session != null) return cmdBuilder.execute(session);
            else return cmdBuilder.execute(RxCmdShell.builder().build());
        }

        public Single<SELinux> build() {
            return Single.create(emitter -> {
                State state = null;
                // First known firmware with SELinux built-in was a 4.2 (17) leak
                if (ApiWrap.hasJellyBeanMR1()) {
                    //noinspection ConstantConditions
                    if (state == null) {
                        // Detect enforcing through sysfs, not always present
                        File f = new File("/sys/fs/selinux/enforce");
                        if (f.exists()) {
                            try {
                                InputStream is = new FileInputStream("/sys/fs/selinux/enforce");
                                try {
                                    if (is.read() == '1') state = State.ENFORCING;
                                } finally { is.close();}
                            } catch (FileNotFoundException e) {
                                Timber.d(e.getMessage());
                            } catch (Exception e) {
                                Timber.w(e);
                            }
                        }
                    }

                    if (state == null) {
                        Cmd.Result result = trySession(Cmd.builder("getenforce").timeout(5000));

                        if (result.getExitCode() == Cmd.ExitCode.OK) {
                            //noinspection ConstantConditions
                            for (String line : result.getOutput()) {
                                if (line.contains(SELINUX_GETENFORCE_DISABLED)) state = State.DISABLED;
                                else if (line.contains(SELINUX_GETENFORCE_PERMISSIVE)) state = State.PERMISSIVE;
                                else if (line.contains(SELINUX_GETENFORCE_ENFORCING)) state = State.ENFORCING;

                                if (state != null) break;
                            }
                        }
                    }

                    if (state == null) {
                        if (ApiWrap.hasKitKat()) {
                            // 4.4+ builds are enforcing by default, take the gamble
                            state = State.ENFORCING;
                        } else {
                            //  Between 17 and 19, most likely PERMISSIVE
                            state = State.PERMISSIVE;
                        }
                    }
                }
                if (state == null) state = State.DISABLED;
                emitter.onSuccess(new SELinux(state));
            });
        }
    }
}
