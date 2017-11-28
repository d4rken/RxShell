package testtools;

import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import eu.darken.rxshell.process.RxProcess;
import eu.darken.rxshell.shell.LineReader;
import timber.log.Timber;


public class MockProcess extends Process {
    final List<CmdListener> cmdListeners = new ArrayList<>();
    final MockInputStream dataStream;
    final MockInputStream errorStream;
    final MockOutputStream cmdStream;
    final LinkedBlockingQueue<String> cmdLines = new LinkedBlockingQueue<>();
    final LinkedBlockingQueue<String> processorQueue = new LinkedBlockingQueue<>();
    volatile Integer exitCode = null;
    volatile boolean isDestroyed = false;
    final CountDownLatch exitLatch = new CountDownLatch(1);
    final Thread processor;

    public MockProcess() throws IOException {
        dataStream = new MockInputStream();
        errorStream = new MockInputStream();
        cmdStream = new MockOutputStream(new MockOutputStream.Listener() {
            @Override
            public void onNewLine(String line) {
                cmdLines.add(line);
                processorQueue.add(line);
            }

            @Override
            public void onClose() {
                Timber.v("CmdStream: onClose()");
            }
        });

        processor = new Thread(() -> {
            // We keep processing while there is more input or could be more input
            while (cmdStream.isOpen() || processorQueue.size() > 0) {
//                Timber.v("CommandProcessor: isInputOpen=%b, queued='%s", cmdStream.isOpen(), processorQueue);
                String line = null;
                try {
                    line = processorQueue.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) { Timber.e(e); }
                if (line == null) continue;

                boolean alreadyProcessed = false;
                for (CmdListener l : cmdListeners) {
                    if (l.onNewCmd(line)) alreadyProcessed = true;
                }

                if (!alreadyProcessed) {
                    if (line.endsWith(" $?" + LineReader.getLineSeparator())) {

                        // By default we assume all commands exit OK
                        final String[] split = line.split(" ");
                        printData(split[1] + " " + 0);

                    } else if (line.endsWith(" >&2" + LineReader.getLineSeparator())) {

                        final String[] split = line.split(" ");
                        printError(split[1]);

                    } else if (line.startsWith("sleep")) {

                        final String[] split = line.replace(LineReader.getLineSeparator(), "").split(" ");
                        long delay = Long.parseLong(split[1]);
                        Timber.v("Sleeping for %d", delay);
                        try { Thread.sleep(delay); } catch (InterruptedException e) { e.printStackTrace(); }

                    } else if (line.startsWith("echo")) {

                        final String[] split = line.replace(LineReader.getLineSeparator(), "").split(" ");
                        printData(split[1]);

                    } else if (line.startsWith("error")) {

                        final String[] split = line.replace(LineReader.getLineSeparator(), "").split(" ");
                        printError(split[1]);

                    } else if (line.startsWith("exit")) {
                        try {
                            cmdStream.close();
                        } catch (IOException e) {
                            Timber.e(e);
                        }
                    }
                }

            }

            if (exitCode == null && !isDestroyed) exitCode = 0;

            // The processor isn't running anymore so no more output/errors
            try {
                dataStream.close();
                errorStream.close();
            } catch (IOException e) { Timber.e(e); }

            exitLatch.countDown();
            Timber.v("Processor finished.");
        });
        processor.start();
    }

    @Nullable
    public synchronized String getLastCommandRaw() {
        return cmdLines.poll();
    }

    @Override
    public synchronized OutputStream getOutputStream() {
        return cmdStream;
    }

    @Override
    public synchronized InputStream getInputStream() {
        return dataStream;
    }

    @Override
    public synchronized InputStream getErrorStream() {
        return errorStream;
    }

    @Override
    public int waitFor() throws InterruptedException {
        Timber.v("waitFor()");
        exitLatch.await();
        return exitCode;
    }

    @Override
    public synchronized int exitValue() {
        if (exitCode == null || exitLatch.getCount() > 0) throw new IllegalThreadStateException();
        else return exitCode;
    }

    @Override
    public boolean isAlive() {
        return exitLatch.getCount() > 0;
    }

    @Override
    public void destroy() {
        synchronized (this) {
            if (isDestroyed) return;
            isDestroyed = true;
            Timber.v("destroy()");
        }

        if (exitCode == null) exitCode = RxProcess.ExitCode.PROBLEM;
        try {
            cmdStream.close();
            dataStream.close();
            errorStream.close();
        } catch (IOException e) {
            Timber.e(e);
        }
        exitLatch.countDown();
    }

    @Override
    public Process destroyForcibly() {
        destroy();
        return this;
    }

    public void printData(String output) {
        dataStream.queue(output + LineReader.getLineSeparator());
    }

    public void printError(String error) {
        errorStream.queue(error + LineReader.getLineSeparator());
    }

    public void addCmdListener(CmdListener listener) {
        cmdListeners.add(listener);
    }

    public interface CmdListener {
        boolean onNewCmd(String line);
    }
}
