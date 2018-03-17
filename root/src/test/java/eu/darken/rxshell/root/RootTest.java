package eu.darken.rxshell.root;


import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import io.reactivex.Single;
import testhelper.BaseTest;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

public class RootTest extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock RxCmdShell.Builder shellBuilder;
    @Mock RxCmdShell shell;
    @Mock RxCmdShell.Session shellSession;
    @Mock SuBinary suBinary;

    @Before
    public void setup() throws Exception {
        super.setup();

        when(shellBuilder.build()).thenReturn(shell);
        when(shellBuilder.root(anyBoolean())).thenReturn(shellBuilder);
        when(shell.open()).thenReturn(Single.just(shellSession));
        when(shellSession.close()).thenReturn(Single.just(0));
        when(shell.isAlive()).thenReturn(Single.just(false));
    }

    @Test
    public void testOpen_no_binary() {
        when(shell.open()).thenReturn(Single.error(new IOException()));

        Root.Builder builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.UNAVAILABLE));
    }

    @Test
    public void testOpen_timeout() {
        when(shellSession.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().contains("id")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OK, Collections.singletonList("uid=0(root) gid=0(root) groups=0(root) context=u:r:supersu:s0"), new ArrayList<>()));
            } else return Single.error(new Exception("Unexpected state"));
        });
        when(shell.open()).thenReturn(Single.just(shellSession).delay(2000, TimeUnit.MILLISECONDS));

        Root.Builder builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.ROOTED));

        builder = new Root.Builder().timeout(1000);
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.UNAVAILABLE));

        builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.ROOTED));
    }

    @Test
    public void testCommand_rooted() {
        when(shellSession.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().contains("id")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OK, Collections.singletonList("uid=0(root) gid=0(root) groups=0(root) context=u:r:supersu:s0"), new ArrayList<>()));
            } else return Single.error(new Exception("Unexpected state"));
        });
        when(suBinary.getType()).thenReturn(SuBinary.Type.CHAINFIRE_SUPERSU);
        Root.Builder builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.ROOTED));

        when(suBinary.getType()).thenReturn(SuBinary.Type.UNKNOWN);
        builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.ROOTED));
    }

    @Test
    public void testCommand_timeout() {
        when(shell.open()).thenReturn(Single.just(shellSession));

        when(shellSession.submit(any(Cmd.class))).thenAnswer(invocation -> Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.TIMEOUT, new ArrayList<>(), new ArrayList<>())));
        Root.Builder builder = new Root.Builder().timeout(1000);
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.DENIED));

        when(shellSession.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OK, Collections.singletonList("uid=0(root) gid=0(root) groups=0(root) context=u:r:supersu:s0"), new ArrayList<>()));
        });
        builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.ROOTED));
    }

    @Test
    public void testRooted_kingoroot_workaround() {
        when(shellSession.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().contains("id")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OUTOFRANGE, Collections.singletonList("uid=0(root) gid=0(root) groups=0(root)"), new ArrayList<>()));
            } else return Single.error(new Exception("Unexpected state"));
        });

        when(suBinary.getType()).thenReturn(SuBinary.Type.UNKNOWN);
        Root.Builder builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.UNAVAILABLE));

        when(suBinary.getType()).thenReturn(SuBinary.Type.KINGOUSER);
        builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.ROOTED));
    }

    @Test
    public void testRooted_second_chance() {
        when(shellSession.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().contains("id")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.PROBLEM));
            } else if (cmd.getCommands().contains("echo test > /cache/root_test.tmp")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OK));
            } else return Single.error(new Exception("Unexpected state"));
        });

        when(suBinary.getType()).thenReturn(SuBinary.Type.UNKNOWN);
        Root.Builder builder = new Root.Builder();
        assertThat(builder.shellBuilder(shellBuilder).suBinary(suBinary).build().blockingGet().getState(), is(Root.State.ROOTED));
    }

    @Test
    public void testEqualsHash() {
        Root root1 = new Root(Root.State.ROOTED);
        Root root2 = new Root(Root.State.ROOTED);
        Root root3 = new Root(Root.State.DENIED);

        assertThat(root1, is(root2));
        assertThat(root1.hashCode(), is(root2.hashCode()));

        assertThat(root1, is(not(root3)));
        assertThat(root1.hashCode(), is(not(root3.hashCode())));
    }
}
