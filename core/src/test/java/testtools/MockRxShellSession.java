package testtools;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import eu.darken.rxshell.shell.LineReader;
import eu.darken.rxshell.shell.RxShell;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.processors.ReplayProcessor;
import timber.log.Timber;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockRxShellSession {
    PublishProcessor<String> outputPub;
    PublishProcessor<String> errorPub;
    ReplayProcessor<Integer> waitForPub = ReplayProcessor.create();
    LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    final RxShell.Session session;

    public MockRxShellSession() throws IOException {
        Thread thread = new Thread(() -> {
            while (true) {
                String line;
                try {
                    line = queue.take();
                } catch (InterruptedException e) {
                    Timber.e(e);
                    return;
                }
                if (line.endsWith(" $?")) {
                    // By default we assume all commands exit OK
                    final String[] split = line.split(" ");
                    outputPub.onNext(split[1] + " " + 0);
                } else if (line.endsWith(" >&2")) {
                    final String[] split = line.split(" ");
                    errorPub.onNext(split[1]);
                } else if (line.startsWith("sleep")) {
                    final String[] split = line.split(" ");
                    long delay = Long.parseLong(split[1]);
                    Timber.v("Sleeping for %d", delay);
                    TestHelper.sleep(delay);
                } else if (line.startsWith("echo")) {
                    final String[] split = line.split(" ");
                    outputPub.onNext(split[1]);
                } else if (line.startsWith("error")) {
                    final String[] split = line.split(" ");
                    errorPub.onNext(split[1]);
                } else if (line.startsWith("exit")) {
                    break;
                }
            }
            outputPub.onComplete();
            errorPub.onComplete();
            waitForPub.onNext(0);
            waitForPub.onComplete();
        });
        thread.start();
        session = mock(RxShell.Session.class);

        doAnswer(invocation -> {
            String line = invocation.getArgument(0);
            boolean flush = invocation.getArgument(1);
            Timber.d("writeLine(%s, %b)", line, flush);
            queue.add(line);
            return null;
        }).when(session).writeLine(any(), anyBoolean());

        outputPub = PublishProcessor.create();
        when(session.outputLines()).thenReturn(outputPub);

        errorPub = PublishProcessor.create();
        when(session.errorLines()).thenReturn(errorPub);

        when(session.cancel()).then(invocation -> Completable.create(e -> {
            Timber.i("cancel()");
            thread.interrupt();

            outputPub.onComplete();
            errorPub.onComplete();

            waitForPub.onNext(1);
            waitForPub.onComplete();
            e.onComplete();
        }));

        Single<Integer> close = Completable.create(e -> {
            queue.add("exit" + LineReader.getLineSeparator());
            e.onComplete();
        }).andThen(waitForPub.lastOrError()).cache();
        when(session.close()).thenReturn(close);

        waitForPub.doOnEach(integerNotification -> Timber.i("waitFor %s", integerNotification)).subscribe();

        when(session.waitFor()).thenReturn(waitForPub.lastOrError());
        when(session.isAlive()).thenReturn(Single.create(e -> e.onSuccess(thread.isAlive())));
    }

    public RxShell.Session getSession() {
        return session;
    }

    public PublishProcessor<String> getErrorPub() {
        return errorPub;
    }

    public PublishProcessor<String> getOutputPub() {
        return outputPub;
    }
}
