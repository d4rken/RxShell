package eu.darken.rxshell.process;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import eu.darken.rxshell.extra.ApiWrap;
import eu.darken.rxshell.shell.LineReader;
import testtools.BaseTest;
import testtools.MockProcess;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RootKillerTest extends BaseTest {
    @Mock ProcessFactory processFactory;
    @Mock Process processtoKill;

    MockProcess psProcess;
    MockProcess killProcess;
    LinkedBlockingQueue<MockProcess> queue = new LinkedBlockingQueue<>();

    @Before
    public void setup() throws Exception {
        super.setup();
        psProcess = new MockProcess();
        killProcess = new MockProcess();
        queue.add(psProcess);
        queue.add(killProcess);
        when(processFactory.start(any())).thenAnswer(invocation -> queue.take());
    }

    @Test
    public void testFactory() {
        ProcessKiller killer = new RootKiller(processFactory);
        assertThat(killer, is(notNullValue()));
    }

    @Test
    public void testKill() {
        ApiWrap.setSDKInt(26);
        when(processtoKill.isAlive()).thenReturn(true);
        when(processtoKill.toString()).thenReturn("Process[pid=1234, hasExited=false]");

        ProcessKiller killer = new RootKiller(processFactory);

        psProcess.addCmdListener(line -> {
            if (line.startsWith("ps")) {
                psProcess.printData("      PID    PPID    PGID     WINPID   TTY         UID    STIME COMMAND");
                psProcess.printData("     5678    1234    6316       7860  pty1        1001   Nov  4 /usr/bin/ssh");
            }
            return true;
        });

        assertThat(ProcessHelper.isAlive(processtoKill), is(true));
        assertThat(killer.kill(processtoKill), is(true));
        assertThat(killProcess.getLastCommandRaw(), is("kill 1234" + LineReader.getLineSeparator()));
    }

    @Test
    public void testKill_dontKillTheDead() throws IOException {
        ApiWrap.setSDKInt(26);
        when(processtoKill.isAlive()).thenReturn(false);

        ProcessKiller killer = new RootKiller(processFactory);

        assertThat(killer.kill(processtoKill), is(true));

        verify(processFactory, never()).start(any());
    }

    @Test
    public void testKill_cantFindProcessPid() throws IOException {
        ApiWrap.setSDKInt(26);
        when(processtoKill.isAlive()).thenReturn(true);

        ProcessKiller killer = new RootKiller(processFactory);

        when(processtoKill.toString()).thenReturn("");
        assertThat(psProcess.getLastCommandRaw(), is(nullValue()));
        assertThat(killer.kill(processtoKill), is(false));
    }
}
