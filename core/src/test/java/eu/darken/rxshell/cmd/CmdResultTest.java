package eu.darken.rxshell.cmd;

import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;


public class CmdResultTest extends BaseTest {
    @Mock Cmd cmd;

    @Test
    public void testConstructor1() {
        Cmd.Result result = new Cmd.Result(cmd);
        assertThat(result.getExitCode(), is(Cmd.ExitCode.INITIAL));
        assertThat(result.getCmd(), is(cmd));
        assertThat(result.getOutput(), is(not(nullValue())));
        assertThat(result.getErrors(), is(not(nullValue())));
    }

    @Test
    public void testConstructor2() {
        Cmd.Result result = new Cmd.Result(cmd, Cmd.ExitCode.SHELL_DIED);
        assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));
        assertThat(result.getCmd(), is(cmd));
        assertThat(result.getOutput(), is(not(nullValue())));
        assertThat(result.getErrors(), is(not(nullValue())));
    }

    @Test
    public void testConstructor3() {
        List<String> output = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Cmd.Result result = new Cmd.Result(cmd, Cmd.ExitCode.SHELL_DIED, output, errors);
        assertThat(result.getExitCode(), is(Cmd.ExitCode.SHELL_DIED));
        assertThat(result.getCmd(), is(cmd));
        assertThat(result.getOutput(), is(output));
        assertThat(result.getErrors(), is(errors));
    }

    @Test
    public void testMerge() {
        Cmd.Result emptyResult = new Cmd.Result(cmd);
        assertThat(emptyResult.merge(), is(not(nullValue())));

        List<String> output = Collections.singletonList("output");
        List<String> errors = Collections.singletonList("errors");
        Cmd.Result result = new Cmd.Result(cmd, Cmd.ExitCode.SHELL_DIED, output, errors);
        assertThat(result.merge(), contains("output", "errors"));
    }
}
