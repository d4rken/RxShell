package eu.darken.rxshell.cmd;

import android.support.v4.util.Pair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collections;

import eu.darken.rxshell.extra.HasEnvironmentVariables;
import eu.darken.rxshell.shell.RxShell;
import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RxCmdShellBuilderTest extends BaseTest {

    @Test
    public void testInstantiation() {
        final RxCmdShell shell = RxCmdShell.builder().build();
        assertThat(shell, is(not(nullValue())));
    }

    @Test
    public void testAutoRxShellCreation() {
        final RxCmdShell.Builder shellBuilder = RxCmdShell.builder();
        assertThat(shellBuilder.getRxShell(), is(nullValue()));
        shellBuilder.build();
        assertThat(shellBuilder.getRxShell(), is(not(nullValue())));
    }


    @Test
    public void testEnvironmentBuilding() {
        RxCmdShell.Builder shellBuilder = RxCmdShell.builder();
        assertThat(shellBuilder.getEnvironment().isEmpty(), is(true));

        final Pair<String, String> testPair = new Pair<>("1", "2");
        shellBuilder.shellEnvironment(root -> {
            assertThat(root, is(false));
            return Collections.singletonList(testPair);
        });
        shellBuilder.build();
        assertThat(shellBuilder.getEnvironment().size(), is(1));
    }

    @Test
    public void testEnvironmentBuilding_useRoot() {
        RxCmdShell.Builder shellBuilder = RxCmdShell.builder();
        HasEnvironmentVariables m = mock(HasEnvironmentVariables.class);
        shellBuilder.shellEnvironment(m);
        shellBuilder.build();
        verify(m).getEnvironmentVariables(false);
        shellBuilder.root(true);
        shellBuilder.build();
        verify(m).getEnvironmentVariables(true);
    }

    @Test(expected = IOException.class)
    public void testBlockingOpen_unwrap() throws IOException {
        final RxCmdShell.Builder shellBuilder = spy(RxCmdShell.builder());

        RxShell rxShell = mock(RxShell.class);
        doThrow(new RuntimeException(new IOException())).when(rxShell).open();
        when(shellBuilder.getRxShell()).thenReturn(rxShell);

        shellBuilder.blockingOpen();
    }

    @Test(expected = RuntimeException.class)
    public void testBlockingOpen() throws IOException {
        final RxCmdShell.Builder shellBuilder = spy(RxCmdShell.builder());

        RxShell rxShell = mock(RxShell.class);
        doThrow(new RuntimeException(new ArithmeticException())).when(rxShell).open();
        when(shellBuilder.getRxShell()).thenReturn(rxShell);

        shellBuilder.blockingOpen();
    }
}
