package eu.darken.rxshell.cmd;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import eu.darken.rxshell.shell.RxShell;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.subjects.BehaviorSubject;
import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RxCmdShellTest extends BaseTest {
    @Mock RxCmdShell.Builder builder;
    @Mock RxShell rxShell;
    @Mock RxShell.Session rxShellSession;
    @Mock CmdProcessor cmdProcessor;
    @Mock CmdProcessor.Factory commandProcessorFactory;

    SingleEmitter<Integer> waitForEmitter;

    @Before
    public void setup() throws Exception {
        super.setup();
        when(builder.getProcessorFactory()).thenReturn(commandProcessorFactory);
        when(commandProcessorFactory.create()).thenReturn(cmdProcessor);
        BehaviorSubject<Boolean> idlePub = BehaviorSubject.createDefault(true);
        when(cmdProcessor.isIdle()).thenReturn(idlePub);

        when(rxShell.open()).thenReturn(Single.create(emitter -> {
            when(rxShellSession.waitFor()).thenReturn(Single.create(e -> waitForEmitter = e));
            emitter.onSuccess(rxShellSession);
        }));

        when(rxShellSession.waitFor()).thenReturn(Single.just(0));
        when(rxShellSession.isAlive()).thenReturn(Single.just(true));
        when(rxShellSession.cancel()).thenReturn(Completable.create(e -> {
            when(rxShellSession.isAlive()).thenReturn(Single.just(false));
            waitForEmitter.onSuccess(1);
            idlePub.onNext(true);
            idlePub.onComplete();
            e.onComplete();
        }));
        when(rxShellSession.close()).thenReturn(Single.create(e -> {
            when(rxShellSession.isAlive()).thenReturn(Single.just(false));
            waitForEmitter.onSuccess(0);
            e.onSuccess(0);
            idlePub.onNext(true);
            idlePub.onComplete();
        }));
    }

    @Test
    public void testInstantiation() {
        new RxCmdShell(builder, rxShell);
        verify(builder).getEnvironment();
    }

    @Test
    public void testOpen() {
        RxCmdShell rxCmdShell = new RxCmdShell(builder, rxShell);
        rxCmdShell.open().test().awaitCount(1).assertNoTimeout();
        verify(rxShell).open();
    }

    @Test
    public void testOpen_exception() {
        doReturn(Single.error(new IOException())).when(rxShell).open();
        RxCmdShell rxCmdShell = new RxCmdShell(builder, rxShell);
        rxCmdShell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertError(IOException.class);
        verify(rxShell).open();

        verify(rxShellSession, never()).outputLines();
        verify(rxShellSession, never()).errorLines();
    }

    @Test
    public void testCancel() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session1 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        session1.cancel().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        verify(rxShellSession).cancel();
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

        RxCmdShell.Session session2 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        assertThat(session2, is(not(session1)));
    }

    @Test
    public void testCancel_indirect() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session1 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        shell.cancel().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        verify(rxShellSession).cancel();
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);


        RxCmdShell.Session session2 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        assertThat(session2, is(not(session1)));
    }

    @Test
    public void testCancel_thenClose() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session1 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        session1.cancel().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        session1.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
    }

    @Test
    public void testCancel_thenClose_indirect() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        shell.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        shell.cancel().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        shell.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
    }

    @Test
    public void testClose() throws IOException {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session1 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        session1.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(0);
        verify(rxShellSession).close();
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

        RxCmdShell.Session session2 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        assertThat(session2, is(not(session1)));
    }

    @Test
    public void testClose_indirect() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session1 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        shell.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(0);
        verify(rxShellSession).close();
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

        RxCmdShell.Session session2 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        assertThat(session2, is(not(session1)));
    }

    @Test
    public void testClose_thenCancel() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session1 = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        session1.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        session1.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        session1.cancel().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
    }

    @Test
    public void testClose_thenCancel_indirect() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        shell.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        shell.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
        shell.cancel().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertComplete();
    }

    @Test
    public void testAlive() throws IOException {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session = shell.open().test().awaitCount(1).assertNoTimeout().values().get(0);

        when(rxShellSession.isAlive()).thenReturn(Single.just(false));
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

        when(rxShellSession.isAlive()).thenReturn(Single.just(true));
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        when(rxShellSession.isAlive()).thenReturn(Single.just(false));
        session.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

    }

    @Test
    public void testAlive_indirect() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        shell.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

        RxCmdShell.Session session = shell.open().test().awaitCount(1).assertNoTimeout().values().get(0);

        when(rxShellSession.isAlive()).thenReturn(Single.just(false));
        shell.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

        when(rxShellSession.isAlive()).thenReturn(Single.just(false));
        shell.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);

        session.close();

        shell.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(false);
    }

    @Test
    public void testReinit() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session1a = shell.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        RxCmdShell.Session session1b = shell.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        assertThat(session1a, is(session1b));
        session1a.close().test();
        RxCmdShell.Session session2 = shell.open().test().awaitCount(1).assertNoTimeout().values().get(0);
        assertThat(session1a, is(not(session2)));
    }

    @Test
    public void testReinit_exception() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        doReturn(Single.error(new IOException())).when(rxShell).open();
        shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertError(IOException.class);

        doReturn(Single.just(rxShellSession)).when(rxShell).open();
        shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1);
    }

    @Test
    public void testEnvironmentSetting() throws IOException {
        final HashMap<String, String> envMap = new HashMap<>();
        envMap.put("key", "value");
        when(builder.getEnvironment()).thenReturn(envMap);

        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        shell.open().test().awaitCount(1).assertNoTimeout();
        verify(rxShellSession).writeLine("key=value", true);
    }

    @Test
    public void testWaitFor() {
        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        RxCmdShell.Session session = shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        session.waitFor().test().awaitDone(1, TimeUnit.SECONDS).assertTimeout();

        waitForEmitter.onSuccess(55);
        session.waitFor().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(55);
    }

    @Test
    public void testClose_waitForCommands() {
        BehaviorSubject<Boolean> idler = BehaviorSubject.createDefault(false);
        when(cmdProcessor.isIdle()).thenReturn(idler);

        RxCmdShell shell = new RxCmdShell(builder, rxShell);
        shell.open().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().values().get(0);
        shell.isAlive().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(true);

        shell.close().test().awaitDone(1, TimeUnit.SECONDS).assertTimeout();

        idler.onNext(true);

        shell.close().test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValue(0);

        verify(cmdProcessor).isIdle();
        verify(rxShellSession).close();
    }

}
