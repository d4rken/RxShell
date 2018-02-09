package eu.darken.rxshell.extra;

import android.support.annotation.Nullable;

import java.io.IOException;

import eu.darken.rxshell.cmd.RxCmdShell;


public class RxCmdShellHelper {

    public static RxCmdShell.Session blockingOpen(RxCmdShell shell) throws IOException {
        try {
            return shell.open().blockingGet();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else throw e;
        }
    }

    public static RxCmdShell.Session blockingOpen(RxCmdShell.Builder builder) throws IOException {
        return blockingOpen(builder.build());
    }

    public static void blockingCancel(@Nullable RxCmdShell.Session session) throws IOException {
        if (session == null) return;
        try {
            session.cancel().blockingAwait();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else throw e;
        }
    }

    public static Integer blockingClose(@Nullable RxCmdShell.Session session) throws IOException {
        if (session == null) return -1;
        try {
            return session.close().blockingGet();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else throw e;
        }
    }

}

