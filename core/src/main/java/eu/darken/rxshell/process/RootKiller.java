package eu.darken.rxshell.process;

import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.darken.rxshell.extra.RXSDebug;
import eu.darken.rxshell.shell.LineReader;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

public class RootKiller implements ProcessKiller {
    private static final String TAG = "RXS:RootKiller";
    private static final Pattern PID_PATTERN = Pattern.compile("^.+?pid=(\\d+).+?$");
    private static final Pattern SPACES_PATTERN = Pattern.compile("\\s+");
    private final ProcessFactory processFactory;

    public RootKiller(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    public boolean kill(Process process) {
        if (RXSDebug.isDebug()) Timber.tag(TAG).d("kill(%s)", process);
        if (!ProcessHelper.isAlive(process)) {
            if (RXSDebug.isDebug()) Timber.tag(TAG).d("Process is no longer alive, skipping kill.");
            return true;
        }
        // stupid method for getting the pid, but it actually works
        Matcher matcher = PID_PATTERN.matcher(process.toString());
        if (!matcher.matches()) {
            if (RXSDebug.isDebug()) Timber.tag(TAG).e("Can't find PID for %s", process);
            return false;
        }
        int pid = Integer.parseInt(matcher.group(1));
        List<Integer> allRelatedPids = getAllPids(pid);

        if (RXSDebug.isDebug()) Timber.tag(TAG).d("Related pids: %s", allRelatedPids);

        if (allRelatedPids != null && destroyPids(allRelatedPids)) {
            return true;
        } else {
            if (RXSDebug.isDebug()) Timber.tag(TAG).w("Couldn't destroy process via root shell, trying Process.destroy()");
            process.destroy();
            return false;
        }
    }

    static Single<List<String>> makeMiniHarvester(InputStream inputStream) {
        return Observable
                .create((ObservableOnSubscribe<String>) emitter -> {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    LineReader lineReader = new LineReader();
                    String line;
                    try {
                        while ((line = lineReader.readLine(reader)) != null && !emitter.isDisposed()) {
                            emitter.onNext(line);
                        }
                    } catch (IOException e) {
                        if (RXSDebug.isDebug()) Timber.tag(TAG).d("MiniHarvester read error: %s", e.getMessage());
                    } finally {
                        emitter.onComplete();
                    }
                })
                .doOnEach(n -> { if (RXSDebug.isDebug()) Timber.tag(TAG).v("miniHarvesters:doOnEach %s", n); })
                .subscribeOn(Schedulers.io())
                .toList()
                .onErrorReturnItem(new ArrayList<>())
                .cache();
    }

    // use 'ps' to get this pid and all pids that are related to it (e.g. spawned by it)
    @Nullable
    private List<Integer> getAllPids(final int parentPid) {
        final List<Integer> result = new ArrayList<>();
        result.add(parentPid);
        Process process = null;
        try {
            process = processFactory.start("su");
            OutputStreamWriter os = new OutputStreamWriter(process.getOutputStream());

            Single<List<String>> errorsHarvester = makeMiniHarvester(process.getErrorStream());
            errorsHarvester.subscribe();

            Single<List<String>> outputHarvester = makeMiniHarvester(process.getInputStream());
            outputHarvester.subscribe();

            os.write("ps" + LineReader.getLineSeparator());
            os.write("exit" + LineReader.getLineSeparator());
            os.flush();
            os.close();

            int exitcode = process.waitFor();

            errorsHarvester.blockingGet();
            final List<String> output = outputHarvester.blockingGet();

            if (RXSDebug.isDebug()) Timber.tag(TAG).d("getAllPids() exitcode: %d", exitcode);

            if (exitcode == RxProcess.ExitCode.OK) {
                for (String s : output) {
                    if (output.indexOf(s) == 0) continue; // SKIP title row
                    String[] line = SPACES_PATTERN.split(s);
                    if (line.length >= 3) {
                        try {
                            if (parentPid == Integer.parseInt(line[2])) result.add(Integer.parseInt(line[1]));
                        } catch (NumberFormatException e) {
                            Timber.tag(TAG).w(e, "getAllPids(parentPid=%d) parse failure: %s", parentPid, line);
                        }
                    }
                }
            }

        } catch (InterruptedException interrupt) {
            Timber.tag(TAG).w(interrupt, "Interrupted!");
            return null;
        } catch (IOException e) {
            Timber.tag(TAG).w(e, "IOException, IOException, pipe broke?");
            return null;
        } finally {
            if (process != null) process.destroy();
        }
        return result;
    }

    private boolean destroyPids(List<Integer> pids) {
        Process process = null;
        try {
            process = processFactory.start("su");

            OutputStreamWriter outputStream = new OutputStreamWriter(process.getOutputStream());

            makeMiniHarvester(process.getErrorStream()).subscribe();
            makeMiniHarvester(process.getInputStream()).subscribe();

            for (Integer p : pids) outputStream.write("kill " + p + LineReader.getLineSeparator());

            outputStream.write("exit" + LineReader.getLineSeparator());
            outputStream.flush();
            outputStream.close();

            int exitcode = process.waitFor();

            if (RXSDebug.isDebug()) Timber.tag(TAG).d("destroyPids(pids=%s) exitcode: %d", pids, exitcode);
            return exitcode == RxProcess.ExitCode.OK;
        } catch (InterruptedException interrupt) {
            Timber.tag(TAG).w("destroyPids(pids=%s) Interrupted!", pids);
            return false;
        } catch (IOException e) {
            Timber.tag(TAG).w("destroyPids(pids=%s) IOException, command failed? not found?", pids);
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
