package eu.darken.rxshell.root;


import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.LinkedHashMap;
import java.util.Map;

import testhelper.BaseTest;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class SuAppTest extends BaseTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock PackageManager packageManager;
    @Mock SuBinary suBinary;
    final Map<String, PackageInfo> packageMap = new LinkedHashMap<>();

    @SuppressWarnings("WrongConstant")
    @Before
    public void setup() throws Exception {
        super.setup();
        when(packageManager.getPackageInfo(anyString(), anyInt())).thenAnswer(invocation -> {
            final String pkg = invocation.getArgument(0);
            final int flags = invocation.getArgument(1);
            if (!packageMap.containsKey(pkg)) throw new PackageManager.NameNotFoundException();
            return packageMap.get(pkg);
        });
    }

    public void fakePackage(String pkgName) {
        PackageInfo pkgInfo = new PackageInfo();
        pkgInfo.packageName = pkgName;
        packageMap.put(pkgName, pkgInfo);
    }

    @Test
    public void testDetection_infos() {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "eu.chainfire.supersu";
        packageInfo.versionCode = 1337;
        packageInfo.versionName = "2.0.0";
        ApplicationInfo applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo = applicationInfo;
        applicationInfo.sourceDir = "/test/path";
        packageMap.put(packageInfo.packageName, packageInfo);

        when(suBinary.getType()).thenReturn(SuBinary.Type.CHAINFIRE_SUPERSU);
        final SuApp suApp = new SuApp.Builder(packageManager).build(suBinary).blockingGet();
        assertNotNull(suApp);
        assertThat(suApp.getPackageName(), is("eu.chainfire.supersu"));
        assertThat(suApp.getType(), is(SuBinary.Type.CHAINFIRE_SUPERSU));
        assertThat(suApp.getVersionCode(), is(1337));
        assertThat(suApp.getVersionName(), is("2.0.0"));
        assertThat(suApp.getApkPath(), is("/test/path"));
    }

    @Test
    public void testDetection_match_not_installed() {
        when(suBinary.getType()).thenReturn(SuBinary.Type.CHAINFIRE_SUPERSU);
        final SuApp suApp = new SuApp.Builder(packageManager).build(suBinary).blockingGet();
        assertThat(suApp.getPackageName(), is(nullValue()));
        assertThat(suApp.getType(), is(SuBinary.Type.CHAINFIRE_SUPERSU));
    }

    @Test
    public void testDetection_none() {
        when(suBinary.getType()).thenReturn(SuBinary.Type.NONE);
        final SuApp suApp = new SuApp.Builder(packageManager).build(suBinary).blockingGet();
        assertThat(suApp.getPackageName(), is(nullValue()));
        assertThat(suApp.getType(), is(SuBinary.Type.NONE));
    }

    @Test
    public void testDetection_unknown() {
        when(suBinary.getType()).thenReturn(SuBinary.Type.UNKNOWN);
        final SuApp suApp = new SuApp.Builder(packageManager).build(suBinary).blockingGet();
        assertThat(suApp.getPackageName(), is(nullValue()));
        assertThat(suApp.getType(), is(SuBinary.Type.UNKNOWN));
    }

    @Test
    public void testDetection_supersu() {
        fakePackage("eu.chainfire.supersu");
        when(suBinary.getType()).thenReturn(SuBinary.Type.CHAINFIRE_SUPERSU);
        final SuApp suApp = new SuApp.Builder(packageManager).build(suBinary).blockingGet();
        assertNotNull(suApp);
        assertThat(suApp.getPackageName(), is("eu.chainfire.supersu"));
        assertThat(suApp.getType(), is(SuBinary.Type.CHAINFIRE_SUPERSU));
    }

    @Test
    public void testDetection_koush_superuser() {
        fakePackage("com.koushikdutta.superuser");
        when(suBinary.getType()).thenReturn(SuBinary.Type.KOUSH_SUPERUSER);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.koushikdutta.superuser"));
    }

    @Test
    public void testDetection_chainsdd() {
        fakePackage("com.noshufou.android.su");
        when(suBinary.getType()).thenReturn(SuBinary.Type.CHAINSDD_SUPERUSER);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.noshufou.android.su"));
    }

    @Test
    public void testDetection_kinguser() {
        fakePackage("com.kingroot.kinguser");
        when(suBinary.getType()).thenReturn(SuBinary.Type.KINGUSER);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.kingroot.kinguser"));
    }

    @Test
    public void testDetection_vroot() {
        fakePackage("com.mgyun.shua.su");
        when(suBinary.getType()).thenReturn(SuBinary.Type.VROOT);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.mgyun.shua.su"));

        packageMap.clear();
        fakePackage("com.mgyun.superuser");
        when(suBinary.getType()).thenReturn(SuBinary.Type.VROOT);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.mgyun.superuser"));
    }

    @Test
    public void testDetection_venomsu() {
        fakePackage("com.m0narx.su");
        when(suBinary.getType()).thenReturn(SuBinary.Type.VENOMSU);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.m0narx.su"));
    }

    @Test
    public void testDetection_kingouser() {
        fakePackage("com.kingouser.com");
        when(suBinary.getType()).thenReturn(SuBinary.Type.KINGOUSER);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.kingouser.com"));
    }

    @Test
    public void testDetection_miui() {
        fakePackage("com.miui.uac");
        when(suBinary.getType()).thenReturn(SuBinary.Type.MIUI);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.miui.uac"));

        packageMap.clear();
        fakePackage("com.lbe.security.miui");
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.lbe.security.miui"));
    }

    @Test
    public void testDetection_cyanogen() {
        fakePackage("com.android.settings");
        when(suBinary.getType()).thenReturn(SuBinary.Type.CYANOGENMOD);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.android.settings"));
    }

    @Test
    public void testDetection_qihoo360() {
        fakePackage("com.qihoo.permmgr");
        when(suBinary.getType()).thenReturn(SuBinary.Type.QIHOO_360);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.qihoo.permmgr"));

        packageMap.clear();
        fakePackage("com.qihoo.permroot");
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.qihoo.permroot"));
    }

    @Test
    public void testDetection_baiduEasyRoot() {
        fakePackage("com.baidu.easyroot");
        when(suBinary.getType()).thenReturn(SuBinary.Type.BAIDU_EASYROOT);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.baidu.easyroot"));
    }

    @Test
    public void testDetection_dianxinos() {
        fakePackage("com.dianxinos.superuser");
        when(suBinary.getType()).thenReturn(SuBinary.Type.DIANXINOSSUPERUSER);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.dianxinos.superuser"));
    }

    @Test
    public void testDetection_baiyi() {
        fakePackage("com.baiyi_mobile.easyroot");
        when(suBinary.getType()).thenReturn(SuBinary.Type.BAIYI_MOBILE_EASYROOT);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.baiyi_mobile.easyroot"));
    }

    @Test
    public void testDetection_tencentAppManager() {
        fakePackage("com.tencent.qrom.appmanager");
        when(suBinary.getType()).thenReturn(SuBinary.Type.TENCENT_APPMANAGER);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.tencent.qrom.appmanager"));
    }

    @Test
    public void testDetection_seSuperUser() {
        fakePackage("me.phh.superuser");
        when(suBinary.getType()).thenReturn(SuBinary.Type.SE_SUPERUSER);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("me.phh.superuser"));
    }

    @Test
    public void testDetection_magisk() {
        fakePackage("com.topjohnwu.magisk");
        when(suBinary.getType()).thenReturn(SuBinary.Type.MAGISKSU);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.topjohnwu.magisk"));
    }

    @Test
    public void testDetection_genymotion() {
        fakePackage("com.genymotion.superuser");
        when(suBinary.getType()).thenReturn(SuBinary.Type.GENYMOTION);
        assertThat(new SuApp.Builder(packageManager).build(suBinary).blockingGet().getPackageName(), is("com.genymotion.superuser"));
    }
    
    @Test
    public void testEqualsHash() {
        final SuApp app1 = new SuApp(SuBinary.Type.NONE, "pkg", "vname", 1, "/path");
        final SuApp app2 = new SuApp(SuBinary.Type.NONE, "pkg", "vname", 1, "/path");
        final SuApp app3 = new SuApp(SuBinary.Type.NONE, "", "vname", 1, "/path");

        assertThat(app1, is(app2));
        assertThat(app2, is(not(app3)));

        assertThat(app1.hashCode(), is(app2.hashCode()));
        assertThat(app2.hashCode(), is(not(app3.hashCode())));
    }
}
