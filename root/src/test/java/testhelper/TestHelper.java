package testhelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.plugins.RxJavaPlugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class TestHelper {
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static List<Throwable> trackPluginErrors() {
        final List<Throwable> list = Collections.synchronizedList(new ArrayList<Throwable>());
        RxJavaPlugins.setErrorHandler(list::add);
        return list;
    }

    public static void assertTODO() {
        assertThat("TODO", true, is(false));
    }
}
