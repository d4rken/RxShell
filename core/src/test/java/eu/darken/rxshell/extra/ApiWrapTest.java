package eu.darken.rxshell.extra;

import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(MockitoJUnitRunner.class)
public class ApiWrapTest extends BaseTest {

    @Test
    public void testDefault() {
        ApiWrap.setSDKInt(55);
        assertThat(ApiWrap.getCurrentSDKInt(), is(55));
    }

    @Test
    public void testJellyBeanMR2() {
        ApiWrap.setSDKInt(0);
        assertThat(ApiWrap.hasJellyBeanMR2(), is(false));
        ApiWrap.setSDKInt(Build.VERSION_CODES.JELLY_BEAN_MR2);
        assertThat(ApiWrap.hasJellyBeanMR2(), is(true));
        ApiWrap.setSDKInt(Build.VERSION_CODES.KITKAT);
        assertThat(ApiWrap.hasJellyBeanMR2(), is(true));
    }

    @Test
    public void testKitKat() {
        ApiWrap.setSDKInt(0);
        assertThat(ApiWrap.hasKitKat(), is(false));
        ApiWrap.setSDKInt(Build.VERSION_CODES.KITKAT);
        assertThat(ApiWrap.hasKitKat(), is(true));
        ApiWrap.setSDKInt(Build.VERSION_CODES.KITKAT_WATCH);
        assertThat(ApiWrap.hasKitKat(), is(true));
    }


}
