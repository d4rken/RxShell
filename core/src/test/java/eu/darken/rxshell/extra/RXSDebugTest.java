package eu.darken.rxshell.extra;


import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.Test;

import eu.darken.rxshell.BuildConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RXSDebugTest {

    @Test
    public void test() {
        MatcherAssert.assertThat(RXSDebug.isDebug(), Is.is(BuildConfig.DEBUG));
        RXSDebug.setDebug(false);
        assertThat(RXSDebug.isDebug(), is(false));
        RXSDebug.setDebug(true);
        assertThat(RXSDebug.isDebug(), is(true));
    }

    @Test
    public void testProcessCallbacks() {
        RXSDebug.ProcessCallback callback = new RXSDebug.ProcessCallback() {
            @Override
            public void onProcessStart(Process process) {

            }

            @Override
            public void onProcessEnd(Process process) {

            }
        };
        assertThat(RXSDebug.CALLBACKS.isEmpty(), is(true));
        RXSDebug.addCallback(callback);
        RXSDebug.addCallback(callback);
        assertThat(RXSDebug.CALLBACKS.size(), is(1));
        RXSDebug.removeCallback(callback);
        assertThat(RXSDebug.CALLBACKS.size(), is(0));
    }
}
