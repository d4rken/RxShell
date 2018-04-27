package eu.darken.rxshell.cmd;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import eu.darken.rxshell.extra.HasEnvironmentVariables;
import eu.darken.rxshell.extra.Pair;
import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RxCmdShellBuilderTest extends BaseTest {

    @Test
    public void testInstantiation() {
        final RxCmdShell shell = RxCmdShell.builder().build();
        assertThat(shell, is(not(nullValue())));
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

}
