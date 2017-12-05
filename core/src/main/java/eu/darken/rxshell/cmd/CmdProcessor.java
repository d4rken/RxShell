package eu.darken.rxshell.cmd;

import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.darken.rxshell.extra.RXSDebug;
import eu.darken.rxshell.shell.RxShell;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import timber.log.Timber;

public class CmdProcessor {
    static final String TAG = "RXS:CmdProcessor";
    final Harvester.Factory factory;
    final BehaviorSubject<Boolean> idlePub = BehaviorSubject.createDefault(true);
    final LinkedBlockingDeque<QueueCmd> cmdQueue = new LinkedBlockingDeque<>();
    final AtomicBoolean attached = new AtomicBoolean(false);
    volatile boolean dead = false;

    public CmdProcessor(Harvester.Factory factory) {
        this.factory = factory;
    }

    public Single<Cmd.Result> submit(Cmd cmd) {
        return Single.create(emitter -> {
            QueueCmd item = new QueueCmd(cmd, emitter);
            synchronized (CmdProcessor.this) {
                if (dead) {
                    if (RXSDebug.isDebug()) Timber.tag(TAG).w("Processor wasn't running: %s", cmd);
                    item.exitCode(Cmd.ExitCode.SHELL_DIED);
                    item.emit();
                } else {
                    if (RXSDebug.isDebug()) Timber.tag(TAG).d("Submitted: %s", cmd);
                    cmdQueue.add(item);
                }
            }
        });
    }

    public synchronized void attach(RxShell.Session session) {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("attach(%s)", session);
        if (attached.getAndSet(true)) throw new IllegalStateException("Processor is already attached!");

        Observable
                .create((ObservableOnSubscribe<QueueCmd>) emitter -> {
                    while (true) {
                        QueueCmd item = cmdQueue.take();
                        if (item.isPoisonPill()) {
                            if (RXSDebug.isDebug()) Timber.tag(TAG).d("Poison pill!");
                            break;
                        } else {
                            idlePub.onNext(false);
                            emitter.onNext(item);
                        }
                    }
                    synchronized (CmdProcessor.this) {
                        dead = true;
                        while (!cmdQueue.isEmpty()) {
                            final QueueCmd item = cmdQueue.poll();
                            if (item.isPoisonPill()) continue;
                            item.exitCode(Cmd.ExitCode.SHELL_DIED);
                            item.emit();
                        }
                    }
                    emitter.onComplete();
                    idlePub.onNext(true);
                    idlePub.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .concatMap(item -> {
                    if (RXSDebug.isDebug()) Timber.tag(TAG).i("Processing: %s", item.cmd);
                    final Observable<Harvester.Batch> outputs = session.outputLines()
                            .compose(upstream -> factory.create(upstream, item.cmd, Harvester.Type.OUTPUT))
                            .onErrorReturnItem(new Harvester.Batch(Cmd.ExitCode.SHELL_DIED, null))
                            .doOnEach(n -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("outputLine():doOnEach: %s", n); })
                            .doOnTerminate(() -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("outputLine():doOnTerminate"); })
                            .toObservable().cache();
                    outputs.subscribe(s -> {}, e -> {});

                    final Observable<Harvester.Batch> errors = session.errorLines()
                            .compose(upstream -> factory.create(upstream, item.cmd, Harvester.Type.ERROR))
                            .onErrorReturnItem(new Harvester.Batch(Cmd.ExitCode.SHELL_DIED, null))
                            .doOnEach(n -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("errorLines():doOnEach: %s", n); })
                            .doOnTerminate(() -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("errorLines():doOnTerminate"); })
                            .toObservable().cache();
                    errors.subscribe(s -> {}, e -> {});

                    for (String write : item.cmd.getCommands()) session.writeLine(write, false);

                    session.writeLine("echo " + item.cmd.getMarker() + " $?", false);
                    session.writeLine("echo " + item.cmd.getMarker() + " >&2", true);

                    Observable<QueueCmd> zip = Observable.zip(outputs, errors, (out, err) -> item.exitCode(out.exitCode).output(out.buffer).errors(err.buffer));

                    if (item.cmd.getTimeout() > 0) {
                        zip = zip.timeout(item.cmd.getTimeout(), TimeUnit.MILLISECONDS).onErrorReturn(error -> {
                            if (error instanceof TimeoutException) {
                                if (RXSDebug.isDebug()) Timber.tag(TAG).w("Command timed out: %s", item);
                                return item.exitCode(Cmd.ExitCode.TIMEOUT);
                            } else {
                                throw new RuntimeException(error);
                            }
                        });
                    }
                    return zip;
                })
                .doOnNext(item -> { if (RXSDebug.isDebug()) Timber.tag(TAG).i("Processed: %s", item); })
                .doOnEach(n -> { if (RXSDebug.isDebug()) Timber.tag(TAG).d("Post zip: %s", n); })
                .doAfterTerminate(() -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("Processor terminated."); })
                .subscribe(new Observer<QueueCmd>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        session.waitFor().subscribeOn(Schedulers.io()).subscribe(integer -> {
                            if (RXSDebug.isDebug()) Timber.tag(TAG).v("Attached session ended!");
                            cmdQueue.add(QueueCmd.poisonPill());
                        });
                    }

                    @Override
                    public void onNext(QueueCmd item) {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).v("onNext(%s)", item);
                        if (item.exitCode < 0) {
                            cmdQueue.addFirst(QueueCmd.poisonPill());
                            session.cancel().subscribe();
                        }
                        item.resultEmitter.onSuccess(item.buildResult());
                        idlePub.onNext(cmdQueue.isEmpty());
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).v(error, "onError()");
                    }

                    @Override
                    public void onComplete() {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).v("onComplete()");
                    }
                });
    }

    public Observable<Boolean> isIdle() {
        return idlePub.doOnEach(n -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("isIdle: %s", n);});
    }

    static class QueueCmd {
        final Cmd cmd;
        final SingleEmitter<Cmd.Result> resultEmitter;
        int exitCode = Cmd.ExitCode.INITIAL;
        List<String> output;
        List<String> errors;

        QueueCmd(Cmd cmd, SingleEmitter<Cmd.Result> resultEmitter) {
            this.cmd = cmd;
            this.resultEmitter = resultEmitter;
        }

        QueueCmd exitCode(int exitCode) {
            if (this.exitCode != Cmd.ExitCode.INITIAL) throw new IllegalStateException("ExitCode already set!");
            this.exitCode = exitCode;
            return this;
        }

        QueueCmd output(List<String> output) {
            if (this.output != null) throw new IllegalStateException("Output already set!");
            this.output = output;
            return this;
        }

        QueueCmd errors(List<String> errors) {
            if (this.errors != null) throw new IllegalStateException("Errors already set!");
            this.errors = errors;
            return this;
        }

        Cmd.Result buildResult() {
            return new Cmd.Result(cmd, exitCode, output, errors);
        }

        void emit() {
            resultEmitter.onSuccess(buildResult());
        }

        boolean isPoisonPill() {
            return cmd == null && resultEmitter == null;
        }

        static QueueCmd poisonPill() {
            return new QueueCmd(null, null);
        }

        @Override
        public String toString() {
            return "QueueCmd(command=" + cmd + ", exitCode=" + exitCode + ", output=" + output + ", errors=" + errors + ")";
        }
    }

    public static class Factory {
        private final Harvester.Factory harvesterFactory;

        public Factory(Harvester.Factory harvesterFactory) {this.harvesterFactory = harvesterFactory;}

        public CmdProcessor create() {
            return new CmdProcessor(harvesterFactory);
        }
    }
}
