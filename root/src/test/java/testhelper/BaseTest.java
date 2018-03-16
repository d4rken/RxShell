package testhelper;


import org.junit.After;
import org.junit.Before;

import java.util.List;

import timber.log.Timber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class BaseTest {
    List<Throwable> uncaughtExceptions;

    @Before
    public void setup() throws Exception {
        uncaughtExceptions = TestHelper.trackPluginErrors();
        Timber.plant(new JUnitTree());
    }

    @After
    public void tearDown() {
        TestHelper.sleep(100);
        for (Throwable t : uncaughtExceptions) t.printStackTrace();
        assertThat(uncaughtExceptions.toString(), uncaughtExceptions.isEmpty(), is(true));
        Timber.uprootAll();
    }

    public List<Throwable> getUncaughtExceptions() {
        return uncaughtExceptions;
    }
}
