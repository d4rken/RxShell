package eu.darken.rxshell.cmd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import eu.darken.rxshell.extra.EnvVar;
import eu.darken.rxshell.extra.HasEnvironmentVariables;
import eu.darken.rxshell.extra.RXSDebug;
import eu.darken.rxshell.process.DefaultProcessFactory;
import eu.darken.rxshell.process.ProcessFactory;
import eu.darken.rxshell.process.ProcessKiller;
import eu.darken.rxshell.process.RootKiller;
import eu.darken.rxshell.process.RxProcess;
import eu.darken.rxshell.process.UserKiller;
import eu.darken.rxshell.shell.RxShell;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;


public class RxCmdShell {
    static final String TAG = "RXS:RxCmdShell";
    final Map<String, String> environment;
    final RxShell rxShell;
    final CmdProcessor.Factory processorFactory;
    Single<Session> session;

    @SuppressWarnings("unused")
    private RxCmdShell() throws InstantiationException {
        throw new InstantiationException("Use the builder()!");
    }

    RxCmdShell(Builder builder, RxShell rxShell) {
        environment = builder.getEnvironment();
        processorFactory = builder.getProcessorFactory();
        this.rxShell = rxShell;
    }

    /**
     * Calling this repeatedly will keep returning the same {@link Session} while it is alive.
     * <p> Session creation may be blocking for longer durations, e.g. if the user is shown a root access prompt.
     *
     * @return a {@link Single} that when subscribed to creates a new session.
     */
    public synchronized Single<Session> open() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("open()");
        if (session == null) {
            session = Single
                    .create((SingleOnSubscribe<Session>) emitter -> rxShell.open().subscribe(new SingleObserver<RxShell.Session>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onSuccess(RxShell.Session shellSession) {
                            try {
                                final Iterator<Map.Entry<String, String>> envIterator = environment.entrySet().iterator();
                                while (envIterator.hasNext()) {
                                    final Map.Entry<String, String> entry = envIterator.next();
                                    shellSession.writeLine(entry.getKey() + "=" + entry.getValue(), !envIterator.hasNext());
                                }
                            } catch (IOException e) {
                                emitter.tryOnError(e);
                                return;
                            }
                            CmdProcessor cmdProcessor = processorFactory.create();
                            cmdProcessor.attach(shellSession);
                            final Session cmdShellSession = new Session(shellSession, cmdProcessor);
                            emitter.onSuccess(cmdShellSession);
                        }

                        @Override
                        public void onError(Throwable e) {
                            Timber.tag(TAG).w("Failed to open RxShell session!");
                            synchronized (RxCmdShell.this) {
                                session = null;
                            }
                            emitter.tryOnError(e);
                        }
                    }))
                    .subscribeOn(Schedulers.io())
                    .doOnSuccess(s -> {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).v("open():doOnSuccess %s", s);
                        s.waitFor().subscribe(integer -> {
                            synchronized (RxCmdShell.this) {
                                session = null;
                            }
                        }, e -> Timber.tag(TAG).w(e, "Error resetting session."));
                    })
                    .doOnError(t -> {if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "open():doOnError");})
                    .cache();
        }
        return session;
    }

    /**
     * @see Session#isAlive()
     */
    public Single<Boolean> isAlive() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("isAlive()");
        if (session == null) return Single.just(false);
        else return session.flatMap(Session::isAlive);
    }

    /**
     * If there is no active session this just completes
     *
     * @see Session#cancel()
     */
    public synchronized Completable cancel() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel()");
        if (session == null) return Completable.complete();
        else return session.flatMapCompletable(Session::cancel);
    }

    /**
     * If there is no active session this returns immediately with exitcode {@link Cmd.ExitCode#OK}
     *
     * @see Session#close()
     */
    public synchronized Single<Integer> close() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("close()");
        if (session == null) return Single.just(Cmd.ExitCode.OK);
        else return session.flatMap(Session::close);
    }

    public static class Session {
        static final String TAG = RxCmdShell.TAG + ":Session";
        private final RxShell.Session session;
        private final CmdProcessor cmdProcessor;
        private final Single<Integer> waitFor;
        private final Completable cancel;
        private final Single<Integer> close;

        public Session(RxShell.Session session, CmdProcessor cmdProcessor) {
            this.session = session;
            this.cmdProcessor = cmdProcessor;
            this.waitFor = session.waitFor().cache();
            this.cancel = session.cancel()
                    .doOnComplete(() -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel():doOnComplete");})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "cancel():doOnError");})
                    .cache();
            this.close = cmdProcessor.isIdle()
                    .filter(i -> i)
                    .first(true)
                    .flatMap(i -> session.close())
                    .doOnSuccess(s -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("close():doOnSuccess %s", s);})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "close():doOnError");})
                    .cache();
        }

        /**
         * @param cmd the command to execute
         * @return a {@link Single} that when subscribed to will submit the command and return it's results.
         */
        public Single<Cmd.Result> submit(Cmd cmd) {
            return cmdProcessor.submit(cmd);
        }

        /**
         * @return {@code true} if the current {@link Session} is alive and usable for command submission.
         */
        public Single<Boolean> isAlive() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("isAlive()");
            return session.isAlive();
        }

        /**
         * Blocks until the {@link RxCmdShell.Session} terminates.
         *
         * @return A blocking single emitting the shell exitCode.
         */
        public Single<Integer> waitFor() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("waitFor()");
            return waitFor;
        }

        /**
         * Cancels the current session, terminating the current command and all queued commands.
         * <p>Canceled commands will return {@link Cmd.ExitCode#SHELL_DIED} as exitcode.
         */
        public Completable cancel() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel()");
            return cancel;
        }

        /**
         * Closes the current session after all commands have executed.
         *
         * @return a {@link Single} that blocks until the session completes and emits the shell processes exitcode.
         */
        public Single<Integer> close() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("close()");
            return close;
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<HasEnvironmentVariables> envVarSources = new ArrayList<>();
        private final Map<String, String> environment = new HashMap<>();
        private final CmdProcessor.Factory processorFactory = new CmdProcessor.Factory(new Harvester.Factory());
        private boolean useRoot = false;

        CmdProcessor.Factory getProcessorFactory() {
            return processorFactory;
        }

        /**
         * Environment variables that will be set when opening the shell session.
         * <p>
         * Think `PATH=$PATH:/something`
         * </p>
         * Calling this the same key will overwrite the previous value.
         *
         * @param variable variable name
         * @param value    variable value
         */
        public Builder shellEnvironment(String variable, String value) {
            this.environment.put(variable, value);
            return this;
        }

        /**
         * @see #shellEnvironment(String, String)
         */
        public Builder shellEnvironment(Collection<EnvVar<String, String>> vars) {
            for (EnvVar<String, String> envVar : vars) {
                shellEnvironment(envVar.first, envVar.second);
            }
            return this;
        }

        /**
         * @param envVars the class which provides environment variables, will be called when calling {@link #build()}
         * @see #shellEnvironment(String, String)
         */
        public Builder shellEnvironment(HasEnvironmentVariables envVars) {
            this.envVarSources.add(envVars);
            return this;
        }

        Map<String, String> getEnvironment() {
            return environment;
        }

        /**
         * A root shell is opened by executing `su` otherwise `sh` is executed.
         *
         * @param useRoot whether to create a root shell. Defaults to `false`.
         */
        public Builder root(boolean useRoot) {
            this.useRoot = useRoot;
            return this;
        }

        /**
         * Each call creates a new instance.
         *
         * @return a new {@link RxCmdShell} instance.
         */
        public RxCmdShell build() {
            for (HasEnvironmentVariables envVars : envVarSources) {
                shellEnvironment(envVars.getEnvironmentVariables(useRoot));
            }

            final ProcessFactory processFactory = new DefaultProcessFactory();
            final ProcessKiller processKiller = useRoot ? new RootKiller(processFactory) : new UserKiller();
            final String command = useRoot ? "su" : "sh";
            RxShell rxShell = new RxShell(new RxProcess(processFactory, processKiller, command));

            return new RxCmdShell(this, rxShell);
        }

        /**
         * Equal to {@code builder.build().open()}
         *
         * @see RxCmdShell#open()
         */
        public Single<Session> open() {
            return build().open();
        }
    }

}
