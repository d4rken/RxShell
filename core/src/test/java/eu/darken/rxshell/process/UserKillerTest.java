package eu.darken.rxshell.process;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import eu.darken.rxshell.extra.ApiWrap;
import testtools.BaseTest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserKillerTest extends BaseTest {
    @Mock Process process;

    @Test
    public void testKill_legacy() {
        ApiWrap.setSDKInt(16);
        when(process.exitValue()).thenThrow(new IllegalThreadStateException());
        final ProcessKiller killer = new UserKiller();
        killer.kill(process);
        verify(process).destroy();
    }

    @Test
    public void testKill_oreo() {
        ApiWrap.setSDKInt(26);
        when(process.isAlive()).thenReturn(true);
        final ProcessKiller killer = new UserKiller();
        killer.kill(process);
        verify(process).destroyForcibly();
    }
}
