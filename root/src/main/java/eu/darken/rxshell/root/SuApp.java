package eu.darken.rxshell.root;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.reactivex.Single;
import timber.log.Timber;


public class SuApp {
    private final SuBinary.Type type;
    @Nullable private final String packageName;
    @Nullable private final String versionName;
    @Nullable private final Integer versionCode;
    @Nullable private final String apkPath;

    public SuApp(SuBinary.Type type, @Nullable String pkg, @Nullable String versionName, @Nullable Integer versionCode, @Nullable String apkPath) {
        this.type = type;
        this.packageName = pkg;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.apkPath = apkPath;
    }

    public SuBinary.Type getType() {
        return type;
    }

    @Nullable
    public String getPackageName() {
        return packageName;
    }

    @Nullable
    public String getVersionName() {
        return versionName;
    }

    @Nullable
    public Integer getVersionCode() {
        return versionCode;
    }

    @Nullable
    public String getApkPath() {
        return apkPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SuApp suApp = (SuApp) o;

        if (type != suApp.type) return false;
        if (packageName != null ? !packageName.equals(suApp.packageName) : suApp.packageName != null) return false;
        if (versionName != null ? !versionName.equals(suApp.versionName) : suApp.versionName != null) return false;
        if (versionCode != null ? !versionCode.equals(suApp.versionCode) : suApp.versionCode != null) return false;
        return apkPath != null ? apkPath.equals(suApp.apkPath) : suApp.apkPath == null;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (packageName != null ? packageName.hashCode() : 0);
        result = 31 * result + (versionName != null ? versionName.hashCode() : 0);
        result = 31 * result + (versionCode != null ? versionCode.hashCode() : 0);
        result = 31 * result + (apkPath != null ? apkPath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "SuApp(packageName=%s, versionName=%s, versionCode=%d, path=%s)", packageName, versionName, versionCode, apkPath);
    }

    public static class Builder {
        static final String TAG = "RXS:Root:SuApp";
        public static final Map<SuBinary.Type, String[]> SUAPPS;

        static {
            SUAPPS = new HashMap<>();
            SUAPPS.put(SuBinary.Type.CHAINFIRE_SUPERSU, new String[]{"eu.chainfire.supersu"});
            SUAPPS.put(SuBinary.Type.KOUSH_SUPERUSER, new String[]{"com.koushikdutta.superuser"});
            SUAPPS.put(SuBinary.Type.CHAINSDD_SUPERUSER, new String[]{"com.noshufou.android.su"});
            SUAPPS.put(SuBinary.Type.KINGUSER, new String[]{"com.kingroot.kinguser"});
            SUAPPS.put(SuBinary.Type.VROOT, new String[]{"com.mgyun.shua.su", "com.mgyun.superuser"});
            SUAPPS.put(SuBinary.Type.VENOMSU, new String[]{"com.m0narx.su"});
            SUAPPS.put(SuBinary.Type.KINGOUSER, new String[]{"com.kingouser.com"});
            SUAPPS.put(SuBinary.Type.MIUI, new String[]{"com.miui.uac", "com.lbe.security.miui"});
            SUAPPS.put(SuBinary.Type.CYANOGENMOD, new String[]{"com.android.settings"});
            SUAPPS.put(SuBinary.Type.QIHOO_360, new String[]{"com.qihoo.permmgr", "com.qihoo.permroot"});
            SUAPPS.put(SuBinary.Type.BAIDU_EASYROOT, new String[]{"com.baidu.easyroot"});
            SUAPPS.put(SuBinary.Type.DIANXINOSSUPERUSER, new String[]{"com.dianxinos.superuser"});
            SUAPPS.put(SuBinary.Type.BAIYI_MOBILE_EASYROOT, new String[]{"com.baiyi_mobile.easyroot"});
            SUAPPS.put(SuBinary.Type.TENCENT_APPMANAGER, new String[]{"com.tencent.qrom.appmanager"});
            SUAPPS.put(SuBinary.Type.SE_SUPERUSER, new String[]{"me.phh.superuser"});
            SUAPPS.put(SuBinary.Type.MAGISKSU, new String[]{"com.topjohnwu.magisk"});
            SUAPPS.put(SuBinary.Type.GENYMOTION, new String[]{"com.genymotion.superuser"});
        }

        private final PackageManager packageManager;

        public Builder(PackageManager packageManager) {
            this.packageManager = packageManager;
        }

        public Single<SuApp> build(SuBinary suBinary) {
            return Single.create(emitter -> {
                final SuBinary.Type type = suBinary.getType();
                String packageName = null;
                String versionName = null;
                Integer versionCode = null;
                String apkPath = null;

                if (type == SuBinary.Type.UNKNOWN || type == SuBinary.Type.NONE) {
                    Timber.tag(TAG).d("Unknown or non existent su binary. Can't determine SuApp.");
                } else {
                    String[] suAppPackages = SUAPPS.get(type);
                    PackageInfo pkgInfo = null;
                    if (suAppPackages != null) {
                        for (String pkg : suAppPackages) {
                            try {
                                pkgInfo = packageManager.getPackageInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
                                break;
                            } catch (PackageManager.NameNotFoundException ignore) { }
                        }
                    }
                    if (pkgInfo != null) {
                        packageName = pkgInfo.packageName;
                        versionName = pkgInfo.versionName;
                        versionCode = pkgInfo.versionCode;
                        if (pkgInfo.applicationInfo != null) apkPath = pkgInfo.applicationInfo.sourceDir;
                    }
                }

                emitter.onSuccess(new SuApp(type, packageName, versionName, versionCode, apkPath));
            });
        }
    }
}
