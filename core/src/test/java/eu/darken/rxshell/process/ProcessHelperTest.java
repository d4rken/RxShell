package eu.darken.rxshell.process;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import eu.darken.rxshell.extra.ApiWrap;
import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProcessHelperTest extends BaseTest {
    @Mock Process process;

    @Test
    public void testIsAlive_legacy() {
        ApiWrap.setSDKInt(16);
        doThrow(new IllegalThreadStateException()).when(process).exitValue();
        assertThat(ProcessHelper.isAlive(process), is(true));

        doReturn(0).when(process).exitValue();
        assertThat(ProcessHelper.isAlive(process), is(false));
    }

    @Test
    public void testIsAlive_oreo() {
        ApiWrap.setSDKInt(26);
        when(process.isAlive()).thenReturn(true);
        assertThat(ProcessHelper.isAlive(process), is(true));

        when(process.isAlive()).thenReturn(false);
        assertThat(ProcessHelper.isAlive(process), is(false));
    }

}
