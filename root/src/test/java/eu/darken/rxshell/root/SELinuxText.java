package eu.darken.rxshell.root;


import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;

import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import eu.darken.rxshell.extra.ApiWrap;
import io.reactivex.Single;
import testhelper.BaseTest;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class SELinuxText extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock RxCmdShell.Session session;

    @Test
    public void testDetection_old_api() {
        ApiWrap.setSDKInt(0);
        SELinux seLinux = new SELinux.Builder().session(session).build().blockingGet();
        assertThat(seLinux.getState(), is(SELinux.State.DISABLED));
    }

    @Test
    public void testDetection_getEnforce_disabled() {
        ApiWrap.setSDKInt(18);
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().contains("getenforce")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OK, Collections.singletonList("Disabled"), null));
            } else {
                return Single.just(new Cmd.Result(cmd, 1));
            }
        });
        SELinux seLinux = new SELinux.Builder().session(session).build().blockingGet();
        assertThat(seLinux.getState(), is(SELinux.State.DISABLED));
    }

    @Test
    public void testDetection_getEnforce_permissive() {
        ApiWrap.setSDKInt(18);
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().contains("getenforce")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OK, Collections.singletonList("Permissive"), null));
            } else {
                return Single.just(new Cmd.Result(cmd, 1));
            }
        });
        SELinux seLinux = new SELinux.Builder().session(session).build().blockingGet();
        assertThat(seLinux.getState(), is(SELinux.State.PERMISSIVE));
    }

    @Test
    public void testDetection_getEnforce_enforcing() {
        ApiWrap.setSDKInt(18);
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().contains("getenforce")) {
                return Single.just(new Cmd.Result(cmd, Cmd.ExitCode.OK, Collections.singletonList("Enforcing"), null));
            } else {
                return Single.just(new Cmd.Result(cmd, 1));
            }
        });
        SELinux seLinux = new SELinux.Builder().session(session).build().blockingGet();
        assertThat(seLinux.getState(), is(SELinux.State.ENFORCING));
    }

    @Test
    public void testDetection_fallback() {
        ApiWrap.setSDKInt(17);
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            return Single.just(new Cmd.Result(cmd, 1));
        });
        SELinux between17And19 = new SELinux.Builder().session(session).build().blockingGet();
        assertThat(between17And19.getState(), is(SELinux.State.PERMISSIVE));


        ApiWrap.setSDKInt(19);
        SELinux above19OrLater = new SELinux.Builder().session(session).build().blockingGet();
        assertThat(above19OrLater.getState(), is(SELinux.State.ENFORCING));
    }

    @Test
    public void testEqualsAndHashCode() {
        SELinux seLinux1 = new SELinux(SELinux.State.DISABLED);
        SELinux seLinux2 = new SELinux(SELinux.State.DISABLED);
        SELinux seLinux3 = new SELinux(SELinux.State.ENFORCING);
        assertThat(seLinux1, is(seLinux2));
        assertThat(seLinux1.hashCode(), is(seLinux2.hashCode()));

        assertThat(seLinux1, is(not(seLinux3)));
        assertThat(seLinux1.hashCode(), is(not(seLinux3.hashCode())));
    }
}
