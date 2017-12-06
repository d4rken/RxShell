package eu.darken.rxshell.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import eu.darken.rxshell.extra.RXSDebug;
import eu.darken.rxshell.process.RxProcess;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

public class RxShell {
    private static final String TAG = "RXS:RxShell";
    private RxProcess rxProcess;
    private Single<Session> session;

    public RxShell(RxProcess rxProcess) {
        this.rxProcess = rxProcess;
    }

    public synchronized Single<Session> open() {
        if (RXSDebug.isDebug()) Timber.tag(TAG).v("open()");
        if (session == null) {
            session = rxProcess.open()
                    .map(session -> {
                        OutputStreamWriter writer = new OutputStreamWriter(session.input(), "UTF-8");
                        return new Session(session, writer);
                    })
                    .subscribeOn(Schedulers.io())
                    .doOnSuccess(s -> {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).v("open():doOnSuccess %s", s);
                        s.waitFor().subscribe(integer -> {
                            synchronized (RxShell.this) {
                                session = null;
                            }
                        }, e -> Timber.tag(TAG).w(e, "Error resetting session."));
                    })
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
        private static final String TAG = RxShell.TAG + ":Session";
        private final RxProcess.Session processSession;
        private final OutputStreamWriter writer;
        private final Flowable<String> outputLines;
        private final Flowable<String> errorLines;
        private final Single<Integer> close;
        private final Single<Integer> waitFor;
        private final Disposable errorKeepAlive;
        private final Disposable outputKeepAlive;
        private final Completable cancel;

        public Session(RxProcess.Session processSession, OutputStreamWriter writer) {
            this.processSession = processSession;
            this.writer = writer;

            this.outputLines = makeLineStream(processSession.output(), "output");
            this.outputKeepAlive = this.outputLines.subscribe(s -> { }, t -> Timber.w(t, "OutputLines KeepAlive"));

            this.errorLines = makeLineStream(processSession.error(), "error");
            this.errorKeepAlive = this.errorLines().subscribe(s -> { }, t -> Timber.w("ErrorLines KeepAlive"));

            this.cancel = processSession.destroy()
                    .doOnComplete(() -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel():doOnComplete");})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "cancel():doOnError");})
                    .cache();
            this.waitFor = processSession.waitFor()
                    .doOnSuccess(s -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("waitFor():doOnSuccess %s", s);})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "waitFor():doOnError");})
                    .cache();
            this.close = Completable
                    .create(emitter -> {
                        try {
                            writeLine("exit", true);
                            writer.close();
                        } catch (IOException e) {
                            if (RXSDebug.isDebug())
                                Timber.tag(TAG).v("Trying to close output, but it's already closed: %s", e.getMessage());
                        } finally {
                            emitter.onComplete();
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .andThen(waitFor())
                    .doFinally(() -> {
                        outputKeepAlive.dispose();
                        errorKeepAlive.dispose();
                    })
                    .doOnSuccess(s -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("close():doOnSuccess %s", s);})
                    .doOnError(t -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v(t, "close():doOnError");})
                    .cache();
        }

        public void writeLine(String line, boolean flush) throws IOException {
            if (RXSDebug.isDebug()) Timber.tag(TAG).d("writeLine(line=%s, flush=%b)", line, flush);
            writer.write(line + LineReader.getLineSeparator());
            if (flush) writer.flush();
        }

        public Single<Boolean> isAlive() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("isAlive()");
            return processSession.isAlive();
        }

        public Completable cancel() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("cancel()");
            return cancel;
        }

        public Single<Integer> waitFor() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("waitFor()");
            return waitFor;
        }

        public Single<Integer> close() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("close()");
            return close;
        }

        public Flowable<String> outputLines() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("outputLines()");
            return outputLines;
        }

        public Flowable<String> errorLines() {
            if (RXSDebug.isDebug()) Timber.tag(TAG).v("errorLines()");
            return errorLines;
        }

        @Override
        public String toString() {
            return "RxShell.Session(processSession=" + processSession + ")";
        }
    }

    static Flowable<String> makeLineStream(InputStream stream, String tag) {
        return Flowable
                .create((FlowableEmitter<String> emitter) -> {
                    final InputStreamReader inputStreamReader = new InputStreamReader(stream, "UTF-8");
                    final BufferedReader reader = new BufferedReader(inputStreamReader);
                    emitter.setCancellable(() -> {
                        try {
                            if (RXSDebug.isDebug()) Timber.tag(TAG).v("LineStream:%s onCancel()", tag);
                            // https://stackoverflow.com/questions/3595926/how-to-interrupt-bufferedreaders-readline
                            stream.close();
                            reader.close();
                        } catch (IOException e) {
                            if (RXSDebug.isDebug()) Timber.tag(TAG).w("LineStream:%s Cancel error: %s", tag, e.getMessage());
                        }
                    });
                    LineReader lineReader = new LineReader();
                    String line;
                    try {
                        while ((line = lineReader.readLine(reader)) != null && !emitter.isCancelled()) {
                            emitter.onNext(line);
                        }
                    } catch (IOException e) {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).v("LineStream:%s Read error: %s", tag, e.getMessage());
                    } finally {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).v("LineStream:%s onComplete()", tag);
                        emitter.onComplete();
                    }
                }, BackpressureStrategy.MISSING)
                .subscribeOn(Schedulers.io())
                .share();
    }
}
