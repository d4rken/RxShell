package eu.darken.rxshell.root;

import android.content.Context;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;

import eu.darken.rxshell.cmd.RxCmdShell;
import io.reactivex.Single;
import testhelper.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RootContextTest extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock RxCmdShell.Builder shellBuilder;
    @Mock RxCmdShell shell;
    @Mock RxCmdShell.Session shellSession;
    @Mock Context context;

    @Mock Root.Builder rootBuilder;
    @Mock SELinux.Builder seLinuxBuilder;
    @Mock SuApp.Builder suAppBuilder;
    @Mock SuBinary.Builder suBinaryBuilder;

    @Before
    public void setup() throws Exception {
        super.setup();

        when(shellBuilder.build()).thenReturn(shell);
        when(shellBuilder.root(anyBoolean())).thenReturn(shellBuilder);
        when(shell.open()).thenReturn(Single.just(shellSession));
        when(shellSession.close()).thenReturn(Single.just(0));
        when(shell.isAlive()).thenReturn(Single.just(false));

        when(rootBuilder.suBinary(any())).thenReturn(rootBuilder);
        when(rootBuilder.build()).thenReturn(Single.just(new Root(Root.State.DENIED)));

        when(seLinuxBuilder.session(any())).thenReturn(seLinuxBuilder);
        when(seLinuxBuilder.build()).thenReturn(Single.just(new SELinux(SELinux.State.ENFORCING)));

        when(suBinaryBuilder.session(any())).thenReturn(suBinaryBuilder);
        when(suBinaryBuilder.build()).thenReturn(Single.just(new SuBinary(SuBinary.Type.NONE, null, null, Collections.emptyList())));

        when(suAppBuilder.build(any())).thenReturn(Single.just(new SuApp(SuBinary.Type.NONE, null, null, null, null)));
    }

    @Test
    public void testRoot() {
        final Root root = mock(Root.class);
        final SuBinary binary = mock(SuBinary.class);
        final SuApp app = mock(SuApp.class);
        final SELinux seLinux = mock(SELinux.class);
        final RootContext.ContextSwitch contextSwitch = mock(RootContext.ContextSwitch.class);
        final RootContext rootContext = new RootContext(root, binary, app, seLinux, contextSwitch);

        when(root.getState()).thenReturn(Root.State.ROOTED);
        assertThat(rootContext.isRooted(), is(true));
        when(root.getState()).thenReturn(Root.State.DENIED);
        assertThat(rootContext.isRooted(), is(false));
    }

    @Test
    public void testParse_version() {
        RootContext.Builder builder = new RootContext.Builder(context);
        builder.rootBuilder(rootBuilder);
        builder.seLinuxBuilder(seLinuxBuilder);
        builder.suAppBuilder(suAppBuilder);
        builder.suBinaryBuilder(suBinaryBuilder);
        builder.shellBuilder(shellBuilder);
        builder.build().blockingGet();

        verify(rootBuilder).build();
        verify(seLinuxBuilder).build();
        verify(suAppBuilder).build(any());
        verify(suBinaryBuilder).build();
        verify(shellBuilder).build();
    }
}
