package eu.darken.rxshell.root;

import android.content.Context;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import eu.darken.rxshell.cmd.RxCmdShell;
import io.reactivex.Single;
import testhelper.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RootContextTest extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock RxCmdShell.Builder shellBuilder;
    @Mock RxCmdShell shell;
    @Mock RxCmdShell.Session shellSession;
    @Mock Context context;

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

}
