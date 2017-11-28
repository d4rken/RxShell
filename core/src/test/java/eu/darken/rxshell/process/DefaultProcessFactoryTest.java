package eu.darken.rxshell.process;

import org.junit.Test;

import java.io.IOException;

import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class DefaultProcessFactoryTest extends BaseTest {

    @Test
    public void testStart() throws IOException {
        DefaultProcessFactory pf = new DefaultProcessFactory();
        Process process = pf.start("id");
        assertThat(process, is(not(nullValue())));
    }
}
