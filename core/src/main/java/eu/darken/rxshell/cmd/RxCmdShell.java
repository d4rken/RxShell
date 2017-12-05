package eu.darken.rxshell.cmd;

import android.support.v4.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    RxCmdShell(Builder builder) {
        environment = builder.getEnvironment();
        rxShell = builder.getRxShell();
        processorFactory = builder.getProcessorFactory();
    }

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

    public Single<Boolean> isAlive() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("isAlive()");
        if (session == null) return Single.just(false);
        else return session.flatMap(Session::isAlive);
    }

    public synchronized Completable cancel() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel()");
        if (session == null) return Completable.complete();
        else return session.flatMapCompletable(Session::cancel);
    }

    public synchronized Single<Integer> close() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("close()");
        if (session == null) return Single.just(0);
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

        public Single<Cmd.Result> submit(Cmd cmd) {
            return cmdProcessor.submit(cmd);
        }

        public Single<Boolean> isAlive() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("isAlive()");
            return session.isAlive();
        }

        public Single<Integer> waitFor() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("waitFor()");
            return waitFor;
        }

        public Completable cancel() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel()");
            return cancel;
        }

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
        private RxShell rxShell;

        CmdProcessor.Factory getProcessorFactory() {
            return processorFactory;
        }

        RxShell getRxShell() {
            return rxShell;
        }

        public Builder shellEnvironment(String variable, String value) {
            this.environment.put(variable, value);
            return this;
        }

        public Builder shellEnvironment(Collection<Pair<String, String>> vars) {
            for (Pair<String, String> pair : vars) {
                shellEnvironment(pair.first, pair.second);
            }
            return this;
        }

        public Builder shellEnvironment(HasEnvironmentVariables envVars) {
            this.envVarSources.add(envVars);
            return this;
        }

        Map<String, String> getEnvironment() {
            return environment;
        }

        public Builder root(boolean root) {
            this.useRoot = root;
            return this;
        }

        public RxCmdShell build() {
            for (HasEnvironmentVariables envVars : envVarSources) {
                shellEnvironment(envVars.getEnvironmentVariables(useRoot));
            }
            if (rxShell == null) {
                final ProcessFactory processFactory = new DefaultProcessFactory();
                final ProcessKiller processKiller = useRoot ? new RootKiller(processFactory) : new UserKiller();
                final String command = useRoot ? "su" : "sh";
                rxShell = new RxShell(new RxProcess(processFactory, processKiller, command));
            }
            return new RxCmdShell(this);
        }

        public Single<Session> open() {
            return build().open();
        }
    }

}
