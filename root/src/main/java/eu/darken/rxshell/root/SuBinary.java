package eu.darken.rxshell.root;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.darken.rxshell.cmd.Cmd;
import eu.darken.rxshell.cmd.RxCmdShell;
import io.reactivex.Single;
import timber.log.Timber;


public class SuBinary {
    public enum Type {
        CHAINFIRE_SUPERSU,
        KOUSH_SUPERUSER,
        KINGUSER,
        VROOT,
        /**
         * Has weird exitcode 255 despite being successful.
         */
        KINGOUSER,
        MIUI,
        VENOMSU,
        CYANOGENMOD,
        CHAINSDD_SUPERUSER,
        BAIDU_EASYROOT,
        QIHOO_360,
        DIANXINOSSUPERUSER,
        BAIYI_MOBILE_EASYROOT,
        TENCENT_APPMANAGER,
        SE_SUPERUSER,
        MAGISKSU,
        GENYMOTION,
        UNKNOWN,
        NONE
    }

    private final Type type;
    private final String path;
    private final String extra;
    private final String version;
    private final List<String> raw;

    public SuBinary(Type type, @Nullable String path, @Nullable String version, @Nullable String extra, List<String> raw) {
        this.type = type;
        this.path = path;
        this.version = version;
        this.extra = extra;
        this.raw = raw;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @Nullable
    public String getPath() {
        return path;
    }

    public String getExtra() {
        return extra;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    public List<String> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "SuBinary(type=%s, path=%s, version=%s, extra=%s, raw=%s)", type, path, version, extra, raw);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SuBinary suBinary = (SuBinary) o;

        if (type != suBinary.type) return false;
        if (path != null ? !path.equals(suBinary.path) : suBinary.path != null) return false;
        if (extra != null ? !extra.equals(suBinary.extra) : suBinary.extra != null) return false;
        if (version != null ? !version.equals(suBinary.version) : suBinary.version != null) return false;
        return raw != null ? raw.equals(suBinary.raw) : suBinary.raw == null;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (extra != null ? extra.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (raw != null ? raw.hashCode() : 0);
        return result;
    }

    public static class Builder {
        static final String TAG = "RXS:Root:SuBinary";
        public static final Map<Pattern, Type> PATTERNMAP;

        static {
            PATTERNMAP = new HashMap<>();
            // Chainfire SU "2.25:SUPERSU"
            PATTERNMAP.put(Pattern.compile("^([0-9\\.]*):(SUPERSU)$"), Type.CHAINFIRE_SUPERSU);
            // Koush SU "16 com.koushikdutta.superuser"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.koushikdutta\\.superuser)$"), Type.KOUSH_SUPERUSER);
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.thirdparty\\.superuser)$"), Type.KOUSH_SUPERUSER);
            // KingUser "3.43:kinguser_su"
            PATTERNMAP.put(Pattern.compile("^([0-9\\.]*):(kinguser_su)$"), Type.KINGUSER);
            // KingoRoot "13 com.kingouser.com"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.kingouser\\.com)$"), Type.KINGOUSER);
            PATTERNMAP.put(Pattern.compile("^(?:kingo)\\W([0-9]+)$"), Type.KINGOUSER);
            // Cyanogen Mod e.g. "16 com.android.settings"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.android\\.settings)$"), Type.CYANOGENMOD);
            // Cyanogen Mod clone e.g. "16 cm-su"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(cm-su)$"), Type.CYANOGENMOD);
            // ChainsDD "3.3" or "3.1l" or "2.3.1-abcdefgh" etc.
            PATTERNMAP.put(Pattern.compile("^(3\\.(?:[3210]))(l?)$"), Type.CHAINSDD_SUPERUSER);
            PATTERNMAP.put(Pattern.compile("^(3\\.0)-(beta2)$"), Type.CHAINSDD_SUPERUSER);
            PATTERNMAP.put(Pattern.compile("^(3\\.1\\.1)(l?)$"), Type.CHAINSDD_SUPERUSER);
            PATTERNMAP.put(Pattern.compile("^(3\\.0\\.3\\.2)(l?)$"), Type.CHAINSDD_SUPERUSER);
            PATTERNMAP.put(Pattern.compile("^(3\\.0\\.(?:[321]))(l?)$"), Type.CHAINSDD_SUPERUSER);
            PATTERNMAP.put(Pattern.compile("^(2.3.(?:[12]))(-[abcdefgh]{1,8})$"), Type.CHAINSDD_SUPERUSER);
            // VROOT "11 com.mgyun.shua.su"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.mgyun\\..+?)$"), Type.VROOT);
            // VenomSU, TEAM Venom "Venom SuperUser v21"
            PATTERNMAP.put(Pattern.compile("^(?:Venom\\WSuperUser)\\W(v[0-9]+)$"), Type.VENOMSU);
            // Qihoo 360 "360.cn es 1.6.0.6" com.qihoo.permmgr
            PATTERNMAP.put(Pattern.compile("^(?:360\\Wcn\\Wes)\\W?([0-9\\.]+)$"), Type.QIHOO_360);
            // MIUI "15 com.lbe.security.miui"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.lbe\\.security\\.miui|com\\.miui\\.uac)$"), Type.MIUI);
            // Baidu Easyroot "15 com.baidu.easyroot"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.baidu\\.easyroot)$"), Type.BAIDU_EASYROOT);
            // Koush SuperUser clone "26 com.dianxinos.superuser"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.dianxinos\\.superuser)$"), Type.DIANXINOSSUPERUSER);
            // Koush SuperUser clone "16 com.baiyi_mobile.easyroot"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.baiyi_mobile\\.easyroot)$"), Type.BAIYI_MOBILE_EASYROOT);
            // CyanogenMod SuperUser clone "16 com.tencent.qrom.appmanager"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.tencent\\.qrom\\.appmanager)$"), Type.TENCENT_APPMANAGER);
            // https://github.com/seSuperuser/Superuser "16 me.phh.superuser cm-su"
            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(me\\.phh\\.superuser.+?)$"), Type.SE_SUPERUSER);
            // https://github.com/topjohnwu/MagiskSU/blob/master/su.h#L81
            PATTERNMAP.put(Pattern.compile("^(.+?):(?:MAGISKSU).*?$"), Type.MAGISKSU);

            PATTERNMAP.put(Pattern.compile("^([0-9]*)\\W(com\\.genymotion\\.superuser)$"), Type.GENYMOTION);
        }

        private RxCmdShell.Session session;

        public Builder() {
        }

        /**
         * If you want to reuse an existing session.
         */
        public Builder session(@Nullable RxCmdShell.Session shellSession) {
            this.session = shellSession;
            return this;
        }

        private Cmd.Result trySession(Cmd.Builder cmdBuilder) {
            if (session != null) return cmdBuilder.execute(session);
            else return cmdBuilder.execute(RxCmdShell.builder().build());
        }

        public Single<SuBinary> build() {
            return Single.create(emitter -> {
                Type type = Type.NONE;
                String path = null;
                String version = null;
                String extra = null;
                final List<String> rawResult = new ArrayList<>();

                Cmd.Result versionResult = trySession(Cmd.builder("su --version"));
                if (versionResult.getExitCode() != Cmd.ExitCode.OK && versionResult.getExitCode() != Cmd.ExitCode.EXCEPTION) {
                    versionResult = Cmd.builder("su --V", "su -version", "su -v", "su -V").timeout(5000).execute(session);
                }

                rawResult.addAll(versionResult.getOutput());

                // Did we hear a faint response?
                if (versionResult.getOutput().size() > 0 || versionResult.getExitCode() == Cmd.ExitCode.OK) {
                    type = Type.UNKNOWN;
                }

                // Who's there?
                for (String line : versionResult.merge()) {
                    for (Map.Entry<Pattern, Type> entry : PATTERNMAP.entrySet()) {
                        Matcher matcher = entry.getKey().matcher(line);
                        if (matcher.matches()) {
                            type = entry.getValue();
                            if (matcher.groupCount() == 1) {
                                version = matcher.group(1);
                            } else if (matcher.groupCount() == 2) {
                                version = matcher.group(1);
                                extra = matcher.group(2);
                            }
                            break;
                        }
                    }

                }

                if (type != Type.NONE) {
                    Cmd.Result pathResult = trySession(Cmd.builder("command -v su"));
                    if (pathResult.getExitCode() == Cmd.ExitCode.OK) {
                        if (pathResult.getOutput().size() == 1) {
                            path = pathResult.getOutput().get(0);
                        } else {
                            Timber.tag(TAG).w("Unexpected su binary path: %s", pathResult.getOutput());
                        }
                    }
                }

                emitter.onSuccess(new SuBinary(type, path, version, extra, rawResult));
            });
        }
    }

}
