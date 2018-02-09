package eu.darken.rxshell.cmd;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import eu.darken.rxshell.extra.RxCmdShellHelper;
import testtools.BaseTest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RxCmdShellHelperTest extends BaseTest {

    @Test(expected = RuntimeException.class)
    public void blockingOpenBuilder_unchecked() throws IOException {
        RxCmdShell.Builder builder = mock(RxCmdShell.Builder.class);
        RxCmdShell shell = mock(RxCmdShell.class);
        when(builder.build()).thenReturn(shell);
        when(shell.open()).thenThrow(new RuntimeException(new Exception()));
        RxCmdShellHelper.blockingOpen(builder);
    }

    @Test(expected = IOException.class)
    public void blockingOpenBuilder_checked() throws IOException {
        RxCmdShell.Builder builder = mock(RxCmdShell.Builder.class);
        RxCmdShell shell = mock(RxCmdShell.class);
        when(builder.build()).thenReturn(shell);
        when(shell.open()).thenThrow(new RuntimeException(new IOException()));
        RxCmdShellHelper.blockingOpen(builder);
    }


    @Test(expected = RuntimeException.class)
    public void blockingOpenShell_unchecked() throws IOException {
        RxCmdShell shell = mock(RxCmdShell.class);
        when(shell.open()).thenThrow(new RuntimeException(new Exception()));
        RxCmdShellHelper.blockingOpen(shell);
    }

    @Test(expected = IOException.class)
    public void blockingOpenShell_checked() throws IOException {
        RxCmdShell shell = mock(RxCmdShell.class);
        when(shell.open()).thenThrow(new RuntimeException(new IOException()));
        RxCmdShellHelper.blockingOpen(shell);
    }

    @Test(expected = RuntimeException.class)
    public void blockinCancel_unchecked() throws IOException {
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(session.cancel()).thenThrow(new RuntimeException(new Exception()));
        RxCmdShellHelper.blockingCancel(session);
    }

    @Test(expected = IOException.class)
    public void blockingCancel_checked() throws IOException {
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(session.cancel()).thenThrow(new RuntimeException(new IOException()));
        RxCmdShellHelper.blockingCancel(session);
    }

    @Test
    public void blockingCancel_nullable() throws IOException {
        RxCmdShellHelper.blockingCancel(null);
    }

    @Test(expected = RuntimeException.class)
    public void blockinClose_unchecked() throws IOException {
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(session.close()).thenThrow(new RuntimeException(new Exception()));
        RxCmdShellHelper.blockingClose(session);
    }

    @Test(expected = IOException.class)
    public void blockingClose_checked() throws IOException {
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(session.close()).thenThrow(new RuntimeException(new IOException()));
        RxCmdShellHelper.blockingClose(session);
    }

    @Test
    public void blockingClose_nullable() throws IOException {
        RxCmdShellHelper.blockingClose(null);
    }

}
