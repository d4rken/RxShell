package eu.darken.rxshell.process;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.darken.rxshell.extra.ApiWrap;
import eu.darken.rxshell.extra.RXSDebug;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import testtools.BaseTest;
import testtools.MockProcess;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RxProcessTest extends BaseTest {
    @Mock ProcessFactory processFactory;
    @Mock ProcessKiller processKiller;
    final List<MockProcess> mockProcesses = new ArrayList<>();

    @Before
    public void setup() throws Exception {
        super.setup();
        when(processFactory.start(any())).thenAnswer(invocation -> {
            MockProcess mockProcess = spy(new MockProcess());
            mockProcesses.add(mockProcess);
            return mockProcess;
        });
        when(processKiller.kill(any())).thenAnswer(invocation -> {
            Process process = invocation.getArgument(0);
            process.destroy();
            return true;
        });
    }

    @After
    public void tearDown() {
        for (MockProcess mp : mockProcesses) mp.destroy();
        mockProcesses.clear();

        super.tearDown();
    }

    @Test
    public void testOpen() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertValue(false);

        TestObserver<RxProcess.Session> sessionObs = rxProcess.open().test().awaitCount(1).assertNoTimeout().assertTerminated();
        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertValue(true);

        await().atMost(2, TimeUnit.SECONDS).until(mockProcesses::size, is(1));
        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(0).isAlive(), is(true));

        RxProcess.Session session = sessionObs.values().get(0);
        assertThat(session.input(), is(mockProcesses.get(0).getOutputStream()));
        assertThat(session.output(), is(mockProcesses.get(0).getInputStream()));
        assertThat(session.error(), is(mockProcesses.get(0).getErrorStream()));

        session.destroy().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();

        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(0).isAlive(), is(false));
        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);
    }

    @Test
    public void testOpen_initial_exception() throws IOException {
        when(processFactory.start(any())).thenThrow(new IOException());
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");

        final TestObserver<RxProcess.Session> sessionObs = rxProcess.open().test();
        sessionObs.awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertError(IOException.class);
    }

    @Test
    public void testExitCode() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        RxProcess.Session session = rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        session.waitFor().test().awaitDone(1, TimeUnit.SECONDS).assertTimeout();
        session.destroy().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        session.waitFor().test().awaitDone(1, TimeUnit.SECONDS).assertValue(1);
    }

    @Test
    public void testReinit() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");

        // A shell is only opened once and while alive returns the cached Single<Session>
        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);
        rxProcess.open().test().awaitCount(1).assertNoTimeout();
        assertThat(rxProcess.open(), is(rxProcess.open()));
        rxProcess.open().test().awaitCount(1).assertNoTimeout();
        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        await().atMost(2, TimeUnit.SECONDS).until(mockProcesses::size, is(1));
        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(0).isAlive(), is(true));

        // Closing the previous session...
        rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0).destroy().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);
        // ...lets the next open() call open a new one
        rxProcess.open().test().awaitCount(1).assertNoTimeout();
        await().atMost(2, TimeUnit.SECONDS).until(mockProcesses::size, is(2));
        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(0).isAlive(), is(false));
        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(1).isAlive(), is(true));

        // Which can also be closed and reopened without issue
        rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0).destroy().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        // Open and close one more in a single swoop
        rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0).destroy().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        await().atMost(2, TimeUnit.SECONDS).until(mockProcesses::size, is(3));
        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(0).isAlive(), is(false));
        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(1).isAlive(), is(false));
        await().atMost(2, TimeUnit.SECONDS).until(() -> mockProcesses.get(2).isAlive(), is(false));
    }

    @Test
    public void testDestroy() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        RxProcess.Session session = rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        assertThat(mockProcesses.get(0).isAlive(), is(true));

        session.destroy().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        assertThat(mockProcesses.get(0).isAlive(), is(false));
    }

    @Test
    public void testIsAlive_legacy() {
        ApiWrap.setSDKInt(16);
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        RxProcess.Session session = rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        assertThat(mockProcesses.get(0).isAlive(), is(true));
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertValue(true);
        verify(mockProcesses.get(0), times(1)).exitValue();

        session.destroy().andThen(session.waitFor()).test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(1);
        assertThat(mockProcesses.get(0).isAlive(), is(false));
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertValue(false);

        verify(mockProcesses.get(0), times(2)).exitValue();
    }

    @Test
    public void testIsAlive_oreo() {
        ApiWrap.setSDKInt(26);
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        RxProcess.Session session = rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0);

        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertValue(true);
        verify(mockProcesses.get(0), times(1)).isAlive();

        session.destroy().andThen(session.waitFor()).test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(1);
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertValue(false);
        verify(mockProcesses.get(0), times(2)).isAlive();
    }

    @Test
    public void testIsAlive_indirect() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        rxProcess.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();

        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);
        verify(mockProcesses.get(0)).isAlive();

        mockProcesses.get(0).destroy();
        rxProcess.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);
    }

    @Test
    public void testWaitFor() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        RxProcess.Session session = rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        assertThat(mockProcesses.get(0).isAlive(), is(true));

        session.destroy().andThen(session.waitFor()).test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(1);
        assertThat(mockProcesses.get(0).isAlive(), is(false));
    }

    @Test
    public void testClose() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        rxProcess.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();

        rxProcess.open().test().awaitCount(1).assertNoTimeout();
        assertThat(mockProcesses.get(0).isAlive(), is(true));

        rxProcess.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        verify(mockProcesses.get(0)).destroy();

        assertThat(mockProcesses.get(0).isAlive(), is(false));
        verify(processKiller).kill(mockProcesses.get(0));
    }

    @Test
    public void testClose_raceconditions() {
        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        int cnt = 1000;
        for (int i = 0; i < cnt; i++) {
            rxProcess.close().observeOn(Schedulers.newThread()).test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        }
        verify(mockProcesses.get(0)).destroy();
    }

    @Test
    public void testProcessCallbacks() {
        RXSDebug.ProcessCallback callback = mock(RXSDebug.ProcessCallback.class);
        RXSDebug.addCallback(callback);

        RxProcess rxProcess = new RxProcess(processFactory, processKiller, "sh");
        RxProcess.Session session = rxProcess.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        assertThat(mockProcesses.get(0).isAlive(), is(true));

        session.destroy().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        assertThat(mockProcesses.get(0).isAlive(), is(false));

        verify(callback).onProcessStart(mockProcesses.get(0));
        verify(callback).onProcessEnd(mockProcesses.get(0));
    }

}
