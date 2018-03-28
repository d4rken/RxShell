package eu.darken.rxshell.cmd;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.subscribers.TestSubscriber;
import testtools.BaseTest;

import static junit.framework.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CmdBuilderTest extends BaseTest {

    @Test
    public void testBuilder_from() {
        Cmd orig = Cmd.builder(UUID.randomUUID().toString())
                .outputBuffer(false)
                .errorBuffer(false)
                .timeout(1337)
                .outputProcessor(PublishProcessor.create())
                .errorProcessor(PublishProcessor.create())
                .build();

        Cmd copy = Cmd.from(orig).build();
        assertEquals(orig.getCommands(), copy.getCommands());
        assertEquals(orig.isOutputBufferEnabled(), copy.isOutputBufferEnabled());
        assertEquals(orig.isErrorBufferEnabled(), copy.isErrorBufferEnabled());
        assertEquals(orig.getTimeout(), copy.getTimeout());
        assertEquals(orig.getOutputProcessor(), copy.getOutputProcessor());
        assertEquals(orig.getErrorProcessor(), copy.getErrorProcessor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_empty() {
        Cmd.builder().build();
    }

    @Test
    public void testBuilder_type1() {
        Cmd cmd = Cmd.builder("cmd1", "cmd2").build();
        assertThat(cmd.getCommands(), contains("cmd1", "cmd2"));
    }

    @Test
    public void testBuilder_type2() {
        Cmd cmd = Cmd.builder(Arrays.asList("cmd1", "cmd2")).build();
        assertThat(cmd.getCommands(), contains("cmd1", "cmd2"));
    }

    @Test
    public void testBuild() {
        final PublishProcessor<String> outputPub = PublishProcessor.create();
        final PublishProcessor<String> errorPub = PublishProcessor.create();
        Cmd cmd = Cmd.builder("cmd1")
                .outputBuffer(false)
                .errorBuffer(false)
                .timeout(1337)
                .outputProcessor(outputPub)
                .errorProcessor(errorPub)
                .build();
        assertThat(cmd.getCommands(), contains("cmd1"));
        assertThat(cmd.getOutputProcessor(), is(outputPub));
        assertThat(cmd.getErrorProcessor(), is(errorPub));
        assertThat(cmd.getTimeout(), is(1337L));
        assertThat(cmd.isOutputBufferEnabled(), is(false));
        assertThat(cmd.isErrorBufferEnabled(), is(false));
    }

    @Test
    public void testBuild_buffer_on_by_default() {
        Cmd cmd = Cmd.builder("k").build();
        assertThat(cmd.isOutputBufferEnabled(), is(true));
        assertThat(cmd.isErrorBufferEnabled(), is(true));
    }

    @Test
    public void testSubmit() {
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(session.submit(any())).thenReturn(Single.just(new Cmd.Result(null)));

        Cmd.builder("").submit(session).blockingGet();

        verify(session).submit(any());
    }

    @Test
    public void testSubmit_oneshot() {
        RxCmdShell shell = mock(RxCmdShell.class);
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(shell.open()).thenReturn(Single.just(session));
        when(shell.isAlive()).thenReturn(Single.just(false));
        when(session.submit(any())).thenReturn(Single.just(new Cmd.Result(null)));
        when(session.close()).thenReturn(Single.just(0));

        Cmd.builder("").submit(shell).blockingGet();

        verify(shell).isAlive();
        verify(shell).open();
        verify(session).submit(any());
        verify(session).close();
    }

    @Test
    public void testSubmit_oneshot_exception() throws IOException {
        RxCmdShell shell = mock(RxCmdShell.class);
        when(shell.open()).thenReturn(Single.error(new IOException()));
        when(shell.isAlive()).thenReturn(Single.just(false));

        Cmd.builder("").submit(shell).test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertError(IOException.class);
    }

    @Test
    public void testExecute() {
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(session.submit(any())).thenReturn(Single.just(new Cmd.Result(null)));

        Cmd.builder("").execute(session);

        verify(session).submit(any());
    }

    @Test
    public void testExecute_oneshot() {
        RxCmdShell shell = mock(RxCmdShell.class);
        RxCmdShell.Session session = mock(RxCmdShell.Session.class);
        when(shell.open()).thenReturn(Single.just(session));
        when(shell.isAlive()).thenReturn(Single.just(false));
        when(session.submit(any())).thenReturn(Single.just(new Cmd.Result(null)));
        when(session.close()).thenReturn(Single.just(0));

        Cmd.builder("").execute(shell);

        verify(shell).isAlive();
        verify(shell).open();
        verify(session).submit(any());
        verify(session).close();
    }

    @Test
    public void testExecute_oneshot_exception() throws IOException {
        RxCmdShell shell = mock(RxCmdShell.class);
        Exception ex = new IOException("Error message");
        when(shell.open()).thenReturn(Single.error(ex));
        when(shell.isAlive()).thenReturn(Single.just(false));

        final Cmd.Result result = Cmd.builder("").execute(shell);
        assertThat(result.getExitCode(), is(Cmd.ExitCode.EXCEPTION));
        assertThat(result.getErrors(), contains(ex.toString()));
        assertThat(result.getOutput(), is(not(nullValue())));
    }

    @Test
    public void testExecute_oneshot_exception_no_buffers() throws IOException {
        RxCmdShell shell = mock(RxCmdShell.class);
        Exception ex = new IOException("Error message");
        when(shell.open()).thenReturn(Single.error(ex));
        when(shell.isAlive()).thenReturn(Single.just(false));

        final PublishProcessor<String> errorPub = PublishProcessor.create();
        final TestSubscriber<String> errorSub = errorPub.test();
        final PublishProcessor<String> outputPub = PublishProcessor.create();
        final TestSubscriber<String> outputSub = outputPub.test();
        final Cmd.Result result = Cmd.builder("")
                .outputBuffer(false)
                .errorBuffer(false)
                .outputProcessor(outputPub)
                .errorProcessor(errorPub)
                .execute(shell);
        assertThat(result.getExitCode(), is(Cmd.ExitCode.EXCEPTION));
        assertThat(result.getErrors(), is(nullValue()));
        assertThat(result.getOutput(), is(nullValue()));
        assertThat(errorSub.valueCount(), is(1));
        assertThat(outputSub.valueCount(), is(0));
        errorSub.assertComplete();
        outputSub.assertComplete();
    }
}
