package eu.darken.rxshell.cmd;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.darken.rxshell.extra.EnvVar;
import eu.darken.rxshell.process.RxProcess;
import eu.darken.rxshell.shell.RxShell;
import io.reactivex.observers.TestObserver;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;
import testtools.BaseTest;
import testtools.MockRxShellSession;
import testtools.TestHelper;
import timber.log.Timber;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CmdProcessorTest extends BaseTest {

    CmdProcessor processor;
    MockRxShellSession mockSession;
    RxShell.Session session;

    @Before
    public void setup() throws Exception {
        super.setup();
        mockSession = new MockRxShellSession();
        session = mockSession.getSession();
        processor = new CmdProcessor(new Harvester.Factory());
    }


    @Test
    public void testCommand_normal() throws IOException {
        processor.attach(session);
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        Cmd cmd = Cmd.builder("echo straw", "error berry").build();
        final TestObserver<Cmd.Result> observer = processor.submit(cmd).test().awaitDone(3, TimeUnit.SECONDS).assertNoTimeout();
        final Cmd.Result result = observer.assertValueCount(1).values().get(0);

        session.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(0);

        assertThat(result.getExitCode(), is(0));
        assertThat(result.getOutput().get(0), is("straw"));
        assertThat(result.getOutput().size(), is(1));
        assertThat(result.getErrors().get(0), is("berry"));
        assertThat(result.getErrors().size(), is(1));

        InOrder order = inOrder(session);
        // Harvester subs
        order.verify(session).outputLines();
        order.verify(session).errorLines();

        order.verify(session).writeLine("echo straw", false);
        order.verify(session).writeLine("error berry", false);
        order.verify(session).writeLine("echo " + cmd.getMarker() + " $?", false);
        order.verify(session).writeLine("echo " + cmd.getMarker() + " >&2", true);

        assertThat(mockSession.getOutputPub().hasSubscribers(), is(false));
        assertThat(mockSession.getErrorPub().hasSubscribers(), is(false));
    }

    @Test
    public void testCommand_input_error() throws IOException {
        processor.attach(session);
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);
        doThrow(new IOException()).when(session).writeLine(anyString(), anyBoolean());

        Cmd cmd = Cmd.builder("echo straw", "error berry").build();
        final TestObserver<Cmd.Result> observer = processor.submit(cmd).test().awaitDone(3, TimeUnit.SECONDS).assertNoTimeout();
        final Cmd.Result result = observer.assertValueCount(1).values().get(0);

        session.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(1);

        assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));

        assertThat(mockSession.getOutputPub().hasSubscribers(), is(false));
        assertThat(mockSession.getErrorPub().hasSubscribers(), is(false));
    }

    @Test
    public void testCommand_empty() {
        processor.attach(session);
        Cmd cmd = Cmd.builder("").build();
        final Cmd.Result result = processor.submit(cmd).test().awaitDone(2, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);

        assertThat(result.getExitCode(), is(0));
        assertThat(result.getOutput(), is(notNullValue()));
        assertThat(result.getErrors(), is(notNullValue()));
    }

    @Test
    public void testCommand_multiple() {
        processor.attach(session);

        int cnt = 100;
        List<TestObserver<Cmd.Result>> testObservers = new ArrayList<>();
        for (int i = 0; i < cnt; i++) {
            testObservers.add(processor.submit(Cmd.builder("sleep 10", "echo o" + i, "error e" + i).build()).observeOn(Schedulers.newThread()).test());
        }

        for (int i = 0; i < testObservers.size(); i++) {
            final TestObserver<Cmd.Result> t = testObservers.get(i);
            t.awaitDone(2, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
            assertThat(t.values().get(0).getExitCode(), is(0));

            assertThat(t.values().get(0).getOutput().get(0), is("o" + String.valueOf(i)));
            assertThat(t.values().get(0).getOutput().size(), is(1));

            assertThat(t.values().get(0).getErrors().get(0), is("e" + String.valueOf(i)));
            assertThat(t.values().get(0).getErrors().size(), is(1));
        }
    }

    @Test
    public void testCommand_callback_sync() {
        processor.attach(session);

        int cnt = 100;
        List<EnvVar<TestObserver<Cmd.Result>, TestSubscriber<String>>> testSubscribers = new ArrayList<>();
        for (int j = 0; j < cnt; j++) {
            List<String> cmds = new ArrayList<>();
            for (int i = 0; i < 10; i++) cmds.add("echo " + i);
            cmds.add("echo " + j);

            PublishProcessor<String> outputListener = PublishProcessor.create();
            TestSubscriber<String> outputObserver = outputListener.doOnEach(stringNotification -> TestHelper.sleep(1)).test();
            final Cmd cmd = Cmd.builder(cmds).outputProcessor(outputListener).build();
            final TestObserver<Cmd.Result> resultObserver = processor.submit(cmd).subscribeOn(Schedulers.newThread()).test();
            testSubscribers.add(new EnvVar<>(resultObserver, outputObserver));
        }
        for (EnvVar<TestObserver<Cmd.Result>, TestSubscriber<String>> envVar : testSubscribers) {
            envVar.first.awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
            envVar.second.awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(11);
        }
    }

    @Test
    public void testCommand_callback_async() {
        processor.attach(session);

        int cnt = 100;
        List<EnvVar<TestObserver<Cmd.Result>, TestSubscriber<String>>> testSubscribers = new ArrayList<>();
        for (int j = 0; j < cnt; j++) {
            List<String> cmds = new ArrayList<>();
            for (int i = 0; i < 10; i++) cmds.add("echo " + i);
            cmds.add("echo " + j);

            PublishProcessor<String> outputListener = PublishProcessor.create();
            TestSubscriber<String> outputObserver = outputListener.observeOn(Schedulers.newThread()).doOnEach(stringNotification -> TestHelper.sleep(1)).test();
            final Cmd cmd = Cmd.builder(cmds).outputProcessor(outputListener).build();
            final TestObserver<Cmd.Result> resultObserver = processor.submit(cmd).subscribeOn(Schedulers.newThread()).test();
            testSubscribers.add(new EnvVar<>(resultObserver, outputObserver));
        }
        for (EnvVar<TestObserver<Cmd.Result>, TestSubscriber<String>> envVar : testSubscribers) {
            envVar.first.awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
            envVar.second.awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(11);
        }
    }

    @Test
    public void testIsIdle() {
        processor.isIdle().test().assertValueCount(1).assertNotTerminated().assertValue(true);
        processor.attach(session);
        processor.isIdle().test().assertValueCount(1).assertNotTerminated().assertValue(true);

        processor.submit(Cmd.builder("sleep 400").build()).subscribe();
        await().atMost(1, TimeUnit.SECONDS).until(() -> processor.isIdle().blockingFirst(), is(false));

        await().atMost(1, TimeUnit.SECONDS).until(() -> processor.isIdle().blockingFirst(), is(true));
    }

    @Test
    public void testDeadShellClearsQueue() {
        processor.attach(session);

        int cnt = 200;
        List<TestObserver<Cmd.Result>> testObservers = new ArrayList<>();
        for (int i = 0; i < cnt; i++) {
            final TestObserver<Cmd.Result> testObserver = processor.submit(Cmd.builder("sleep 10000", "echo o" + i, "error e" + i).build()).test();
            testObservers.add(testObserver);
        }

        mockSession.getOutputPub().onComplete();
        mockSession.getErrorPub().onComplete();

        for (int i = 0; i < testObservers.size(); i++) {
            final TestObserver<Cmd.Result> t = testObservers.get(i);
            Timber.i("Awaiting: %d", i);
            t.awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
            assertThat(t.values().get(0).getExitCode(), is(Cmd.ExitCode.SHELL_DIED));

            assertThat(t.values().get(0).getOutput(), is(not(nullValue())));
            assertThat(t.values().get(0).getErrors(), is(not(nullValue())));
        }
    }

    @Test
    public void testCommand_disabledBuffers() {
        processor.attach(session);

        Cmd.Result result = processor
                .submit(Cmd.builder("echo test", "error test")
                        .outputBuffer(false)
                        .errorBuffer(false)
                        .build())
                .test().awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);

        assertThat(result.getOutput(), is(nullValue()));
        assertThat(result.getErrors(), is(nullValue()));
    }

    @Test
    public void testWatchdog() {
        processor.attach(session);

        final Cmd.Result result = processor
                .submit(Cmd.builder("sleep 5000").timeout(1000).build())
                .test().awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);

        assertThat(result.getExitCode(), is(Cmd.ExitCode.TIMEOUT));

        verify(session, timeout(1000)).cancel();
    }

    @Test
    public void testHarvestersUpstreamError_both() {
        processor.attach(session);

        new Thread(() -> {
            TestHelper.sleep(1000);
            mockSession.getErrorPub().onError(new IOException());
            mockSession.getOutputPub().onError(new IOException());
        }).start();

        final Cmd.Result result = processor.submit(Cmd.builder("sleep 5000").build())
                .test().awaitDone(2, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);

        assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));
        assertThat(result.getOutput(), is(not(nullValue())));
        assertThat(result.getErrors(), is(not(nullValue())));
    }

    @Test
    public void testHarvestersUpstreamError_outpout() {
        processor.attach(session);

        new Thread(() -> {
            TestHelper.sleep(100);
            mockSession.getOutputPub().onError(new IOException());
        }).start();

        final Cmd.Result result = processor.submit(Cmd.builder("sleep 1000").build())
                .test().awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);

        assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));
    }

    @Test
    public void testHarvestersUpstreamError_error() {
        processor.attach(session);

        new Thread(() -> {
            TestHelper.sleep(100);
            mockSession.getErrorPub().onError(new IOException());
        }).start();

        final Cmd.Result result = processor.submit(Cmd.builder("sleep 1000").build())
                .test().awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);

        assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));
    }

    @Test
    public void testHarvestersPrematureCompletion() {
        processor.attach(session);

        new Thread(() -> {
            TestHelper.sleep(1000);
            mockSession.getErrorPub().onComplete();
            mockSession.getOutputPub().onComplete();
        }).start();

        final Cmd.Result result = processor.submit(Cmd.builder("sleep 5000").build())
                .test().awaitDone(2, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);

        assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));
    }

    @Test(expected = IllegalStateException.class)
    public void testNoReuse() throws IOException {
        processor.attach(session);

        final Cmd.Result result = processor.submit(Cmd.builder("echo straw").build()).test().awaitDone(3, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        assertThat(result.getExitCode(), is(RxProcess.ExitCode.OK));
        assertThat(result.getOutput(), Matchers.contains("straw"));

        session.cancel().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();

        MockRxShellSession mockSession2 = new MockRxShellSession();
        processor.attach(mockSession2.getSession());
    }

    @Test
    public void testProcessorDying_race1() throws IOException {
        for (int i = 0; i < 1000; i++) {
            final CmdProcessor processor = new CmdProcessor(new Harvester.Factory());
            final MockRxShellSession mockSession = new MockRxShellSession();
            mockSession.getSession().close().blockingGet();
            processor.attach(mockSession.getSession());

            Cmd cmd = Cmd.builder("sleep 100").build();
            final Cmd.Result result = processor.submit(cmd).test().awaitDone(5, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);
            assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));
        }
    }

    @Test
    public void testProcessorDying_race2() throws IOException {
        for (int i = 0; i < 1000; i++) {
            final CmdProcessor processor = new CmdProcessor(new Harvester.Factory());
            final MockRxShellSession mockSession = new MockRxShellSession();
            processor.attach(mockSession.getSession());

            new Thread(() -> {
                TestHelper.sleep(1);
                mockSession.getSession().cancel().subscribe();
            }).start();

            processor.submit(Cmd.builder("sleep 100").build()).test().awaitDone(2, TimeUnit.SECONDS).assertNoTimeout().assertComplete();

            assertThat(getUncaughtExceptions().isEmpty(), is(true));
        }
    }

    @Test
    public void testDyingHarvesters_race() throws IOException {
        for (int i = 0; i < 1000; i++) {
            final CmdProcessor processor = new CmdProcessor(new Harvester.Factory());
            final MockRxShellSession mockSession = new MockRxShellSession();
            processor.attach(mockSession.getSession());

            new Thread(() -> {
                TestHelper.sleep(1);
                mockSession.getOutputPub().onComplete();
                mockSession.getErrorPub().onComplete();
            }).start();

            processor.submit(Cmd.builder("sleep 100").build()).blockingGet();

            assertThat(getUncaughtExceptions().isEmpty(), is(true));
        }
    }

    @Test
    public void testCmdQueue_buffers() {
        Cmd cmd = mock(Cmd.class);
        CmdProcessor.QueueCmd queueCmd = new CmdProcessor.QueueCmd(cmd, null);

        assertThat(queueCmd.buildResult().getOutput(), is(nullValue()));
        assertThat(queueCmd.buildResult().getErrors(), is(nullValue()));

        when(cmd.isOutputBufferEnabled()).thenReturn(true);
        assertThat(queueCmd.buildResult().getOutput(), is(not(nullValue())));
        assertThat(queueCmd.buildResult().getErrors(), is(nullValue()));

        when(cmd.isErrorBufferEnabled()).thenReturn(true);
        assertThat(queueCmd.buildResult().getOutput(), is(not(nullValue())));
        assertThat(queueCmd.buildResult().getErrors(), is(not(nullValue())));

        when(cmd.isOutputBufferEnabled()).thenReturn(false);
        assertThat(queueCmd.buildResult().getOutput(), is(nullValue()));
        assertThat(queueCmd.buildResult().getErrors(), is(not(nullValue())));
    }

}
