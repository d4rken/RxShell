package eu.darken.rxshell.process;

import android.annotation.SuppressLint;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import eu.darken.rxshell.extra.ApiWrap;
import eu.darken.rxshell.extra.RXSDebug;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;


public class RxProcess {
    public static class ExitCode {
        public static final int OK = 0;
        public static final int PROBLEM = 1;
        public static final int OUTOFRANGE = 255;
    }

    private static final String TAG = "RXS:RxProcess";
    private final Observable<? extends Process> processCreator;
    private Single<Session> session;

    public RxProcess(ProcessFactory processFactory, ProcessKiller processKiller, String... commands) {
        this.processCreator = Observable.create(e -> {
            final Process process = processFactory.start(commands);
            e.setCancellable(() -> {
                if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel()");
                processKiller.kill(process);
            });
            e.onNext(process);
            process.waitFor();
            e.onComplete();
        });
    }

    public synchronized Single<Session> open() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("open()");
        if (session == null) {
            this.session = Single
                    .create(new SingleOnSubscribe<Session>() {
                        WeakReference<Process> debugRef;

                        @Override
                        public void subscribe(SingleEmitter<Session> emitter) {
                            processCreator
                                    .doFinally((Action) () -> {
                                        synchronized (RxProcess.this) {
                                            RXSDebug.notifyOnProcessEnd(debugRef != null ? debugRef.get() : null);
                                            if (RXSDebug.isDebug()) Timber.tag(TAG).v("Process finished, clearing session");
                                            session = null;
                                        }
                                    })
                                    .subscribe(new Observer<Process>() {
                                        Disposable disposable;

                                        @Override
                                        public void onSubscribe(Disposable disposable) {
                                            this.disposable = disposable;
                                        }

                                        @Override
                                        public void onNext(Process process) {
                                            debugRef = new WeakReference<>(process);
                                            RXSDebug.notifyOnProcessStart(process);
                                            if (RXSDebug.isDebug()) Timber.tag(TAG).v("processCreator:onNext(%s)", process);
                                            emitter.onSuccess(new Session(process, disposable));
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            if (RXSDebug.isDebug()) Timber.tag(TAG).v(e, "processCreator:onError()");
                                            emitter.tryOnError(e);
                                        }

                                        @Override
                                        public void onComplete() {
                                            if (RXSDebug.isDebug()) Timber.tag(TAG).v("processCreator:onComplete()");
                                            disposable.dispose();
                                        }
                                    });
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .doOnSuccess(s -> { if (RXSDebug.isDebug()) Timber.tag(TAG).d("open():doOnSuccess %s", s);})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "open():doOnError");})
                    .cache();
        }
        return session;
    }

    public synchronized Single<Boolean> isAlive() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("isAlive()");
        if (session == null) return Single.just(false);
        else return session.flatMap(Session::isAlive);
    }

    public synchronized Completable close() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("close()");
        if (session == null) return Completable.complete();
        else return session.flatMapCompletable(s -> s.destroy().andThen(s.waitFor().ignoreElement()));
    }

    public static class Session {
        private static final String TAG = RxProcess.TAG + ":Session";
        final Process process;
        private final Single<Integer> waitFor;
        private final Completable destroy;

        public Session(Process process, Disposable processDisposable) {
            this.process = process;
            this.destroy = Completable
                    .create(e -> {
                        processDisposable.dispose();
                        e.onComplete();
                    })
                    .subscribeOn(Schedulers.io())
                    .doOnComplete(() -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("destroy():doOnComplete");})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "destroy():doOnError");})
                    .cache();
            this.waitFor = Single
                    .create((SingleOnSubscribe<Integer>) e -> {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).d("Waiting for %s to exit.", process);
                        int exitCode = process.waitFor();
                        if (RXSDebug.isDebug()) Timber.tag(TAG).d("Exitcode: %d, Process: %s", exitCode, process);
                        e.onSuccess(exitCode);
                    })
                    .subscribeOn(Schedulers.io())
                    .doOnSuccess(s -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("waitFor():doOnSuccess %s", s);})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "waitFor():doOnError");})
                    .cache();
        }

        @SuppressLint("NewApi")
        public Single<Boolean> isAlive() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("isAlive()");
            return Single
                    .create((SingleEmitter<Boolean> emitter) -> {
                        if (ApiWrap.hasOreo()) {
                            emitter.onSuccess(process.isAlive());
                        } else {
                            try {
                                process.exitValue();
                                emitter.onSuccess(false);
                            } catch (IllegalThreadStateException e) {
                                emitter.onSuccess(true);
                            }
                        }
                    })
                    .subscribeOn(Schedulers.io());
        }

        public Single<Integer> waitFor() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("waitFor()");
            return waitFor;
        }

        public Completable destroy() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("destroy()");
            return destroy;
        }

        public OutputStream input() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("input()");
            return process.getOutputStream();
        }

        public InputStream output() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("output()");
            return process.getInputStream();
        }

        public InputStream error() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("error()");
            return process.getErrorStream();
        }

        @Override
        public String toString() {
            return "RxProcess.Session(process=" + process + ")";
        }
    }
}
