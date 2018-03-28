package eu.darken.rxshell.root;


import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import io.reactivex.Single;
import testhelper.BaseTest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class SuBinaryTest extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock RxCmdShell.Session session;

    @Test
    public void testCall_normal() {
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.OK)));
        final SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.UNKNOWN));
    }

    @Test
    public void testCall_fallback() {
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().size() == 1) {
                return Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.PROBLEM));

            } else {
                return Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.OK));
            }
        });
        final SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.UNKNOWN));
    }

    @Test
    public void testParse() {
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> {
            Cmd cmd = invocation.getArgument(0);
            if (cmd.getCommands().get(0).contains("command -v")) {
                return Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.OK, Collections.singletonList("/some/path"), new ArrayList<>()));
            } else {
                return Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.OK, Collections.singletonList("16 me.phh.superuser cm-su"), new ArrayList<>()));
            }
        });
        final SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.SE_SUPERUSER));
        assertThat(suBinary.getPath(), is("/some/path"));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("me.phh.superuser cm-su"));
        assertThat(suBinary.getRaw(), contains("16 me.phh.superuser cm-su"));
    }

    @Test
    public void testUnknown_rawValue() {
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.OK, Arrays.asList("123 unknown binary", "the cake is a lie"), new ArrayList<>())));
        final SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.UNKNOWN));
        assertThat(suBinary.getVersion(), is(nullValue()));
        assertThat(suBinary.getExtra(), is(nullValue()));
        assertThat(suBinary.getRaw().get(0), is("123 unknown binary"));
        assertThat(suBinary.getRaw().get(1), is("the cake is a lie"));
        assertThat(suBinary.getRaw().size(), is(2));
    }

    @Test
    public void testEqualsHash() {
        SuBinary binary1 = new SuBinary(SuBinary.Type.SE_SUPERUSER, "/su/bin/su", "16", "me.phh.superuser cm-su", new ArrayList<>());
        SuBinary binary2 = new SuBinary(SuBinary.Type.SE_SUPERUSER, "/su/bin/su", "16", "me.phh.superuser cm-su", new ArrayList<>());
        SuBinary binary3 = new SuBinary(SuBinary.Type.UNKNOWN, null, null, null, null);

        assertThat(binary1, is(binary2));
        assertThat(binary2, is(not(binary3)));

        assertThat(binary1.hashCode(), is(binary2.hashCode()));
        assertThat(binary2.hashCode(), is(not(binary3.hashCode())));
    }

    void fakeOutput(String output) {
        when(session.submit(any(Cmd.class))).thenAnswer(invocation -> Single.just(new Cmd.Result(invocation.getArgument(0), Cmd.ExitCode.OK, Collections.singletonList(output), new ArrayList<>())));
    }

    @Test
    public void testDetection_supersu() {
        fakeOutput("2.25:SUPERSU");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.CHAINFIRE_SUPERSU));
        assertThat(suBinary.getVersion(), is("2.25"));
        assertThat(suBinary.getExtra(), is("SUPERSU"));
    }

    @Test
    public void testDetection_superuser_koush() {
        fakeOutput("16 com.koushikdutta.superuser");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.KOUSH_SUPERUSER));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("com.koushikdutta.superuser"));

        fakeOutput("16 com.thirdparty.superuser");
        suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.KOUSH_SUPERUSER));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("com.thirdparty.superuser"));
    }

    @Test
    public void testDetection_kinguser() {
        fakeOutput("3.43:kinguser_su");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.KINGUSER));
        assertThat(suBinary.getVersion(), is("3.43"));
        assertThat(suBinary.getExtra(), is("kinguser_su"));
    }

    @Test
    public void testDetection_kingouser() {
        fakeOutput("13 com.kingouser.com");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.KINGOUSER));
        assertThat(suBinary.getVersion(), is("13"));
        assertThat(suBinary.getExtra(), is("com.kingouser.com"));

        fakeOutput("kingo 141");
        suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.KINGOUSER));
        assertThat(suBinary.getVersion(), is("141"));
        assertThat(suBinary.getExtra(), is(nullValue()));
    }

    @Test
    public void testDetection_cyanogen() {
        fakeOutput("16 com.android.settings");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.CYANOGENMOD));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("com.android.settings"));
    }

    @Test
    public void testDetection_chainsDD() {
        fakeOutput("3.3");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.CHAINSDD_SUPERUSER));
        assertThat(suBinary.getVersion(), is("3.3"));
        assertThat(suBinary.getExtra(), is(""));

        fakeOutput("3.1l");
        suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.CHAINSDD_SUPERUSER));
        assertThat(suBinary.getVersion(), is("3.1"));
        assertThat(suBinary.getExtra(), is("l"));

        fakeOutput("2.3.1-abcdefgh");
        suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.CHAINSDD_SUPERUSER));
        assertThat(suBinary.getVersion(), is("2.3.1"));
        assertThat(suBinary.getExtra(), is("-abcdefgh"));
    }

    @Test
    public void testDetection_vroot() {
        fakeOutput("11 com.mgyun.test");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.VROOT));
        assertThat(suBinary.getVersion(), is("11"));
        assertThat(suBinary.getExtra(), is("com.mgyun.test"));
    }

    @Test
    public void testDetection_venomsu() {
        fakeOutput("Venom SuperUser v21");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.VENOMSU));
        assertThat(suBinary.getVersion(), is("v21"));
        assertThat(suBinary.getExtra(), is(nullValue()));
    }

    @Test
    public void testDetection_qihoo() {
        fakeOutput("360.cn es 1.6.0.6");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.QIHOO_360));
        assertThat(suBinary.getVersion(), is("1.6.0.6"));
        assertThat(suBinary.getExtra(), is(nullValue()));
    }

    @Test
    public void testDetection_miui() {
        fakeOutput("15 com.lbe.security.miui");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.MIUI));
        assertThat(suBinary.getVersion(), is("15"));
        assertThat(suBinary.getExtra(), is("com.lbe.security.miui"));

        fakeOutput("15 com.miui.uac");
        suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.MIUI));
        assertThat(suBinary.getVersion(), is("15"));
        assertThat(suBinary.getExtra(), is("com.miui.uac"));
    }

    @Test
    public void testDetection_BaiduEasyRoot() {
        fakeOutput("15 com.baidu.easyroot");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.BAIDU_EASYROOT));
        assertThat(suBinary.getVersion(), is("15"));
        assertThat(suBinary.getExtra(), is("com.baidu.easyroot"));
    }

    @Test
    public void testDetection_dianxinos() {
        fakeOutput("26 com.dianxinos.superuser");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.DIANXINOSSUPERUSER));
        assertThat(suBinary.getVersion(), is("26"));
        assertThat(suBinary.getExtra(), is("com.dianxinos.superuser"));
    }

    @Test
    public void testDetection_BaiyiEasyRoot() {
        fakeOutput("16 com.baiyi_mobile.easyroot");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.BAIYI_MOBILE_EASYROOT));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("com.baiyi_mobile.easyroot"));
    }

    @Test
    public void testDetection_tencent() {
        fakeOutput("16 com.tencent.qrom.appmanager");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.TENCENT_APPMANAGER));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("com.tencent.qrom.appmanager"));
    }

    @Test
    public void testDetection_phh() {
        fakeOutput("16 me.phh.superuser cm-su");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.SE_SUPERUSER));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("me.phh.superuser cm-su"));
    }

    @Test
    public void testDetection_magisk() {
        fakeOutput("16.0:MAGISKSU (topjohnwu)");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.MAGISKSU));
        assertThat(suBinary.getVersion(), is("16.0"));
        assertThat(suBinary.getExtra(), is(nullValue()));

        fakeOutput("16.1 (180311):MAGISKSU (topjohnwu)");
        suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.MAGISKSU));
        assertThat(suBinary.getVersion(), is("16.1 (180311)"));
        assertThat(suBinary.getExtra(), is(nullValue()));
    }

    @Test
    public void testDetection_genymotion() {
        fakeOutput("16 com.genymotion.superuser");
        SuBinary suBinary = new SuBinary.Builder().session(session).build().blockingGet();
        assertThat(suBinary.getType(), is(SuBinary.Type.GENYMOTION));
        assertThat(suBinary.getVersion(), is("16"));
        assertThat(suBinary.getExtra(), is("com.genymotion.superuser"));
    }
}
