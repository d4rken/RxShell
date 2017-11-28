package testtools;


import java.io.IOException;
import java.io.OutputStream;

import timber.log.Timber;

public class MockOutputStream extends OutputStream {
    private final Listener listener;
    private final Object dataSync = new Object();
    private StringBuilder data = new StringBuilder();
    private boolean isOpen = true;
    private StringBuffer stringBuffer = new StringBuffer();
    private IOException closeException;

    public MockOutputStream(Listener listener) {this.listener = listener;}

    @Override
    public void write(int i) throws IOException {
        synchronized (dataSync) {
            Character c = (char) i;
            data.append(c);
            stringBuffer.append((char) i);
            if (c == '\n') {
                final String line = data.toString();
                Timber.d("Line: %s", line);
                if (listener != null) listener.onNewLine(line);
                data = new StringBuilder();
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        Timber.d("close() called");
        if (closeException != null) {
            isOpen = false;
            IOException e = closeException;
            closeException = null;
            throw e;
        }

        if (!isOpen) return;
        isOpen = false;

        if (listener != null) listener.onClose();
    }

    public void setExceptionOnClose(IOException closeException) {
        this.closeException = closeException;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public StringBuffer getData() {
        return stringBuffer;
    }

    public interface Listener {
        void onNewLine(String line);

        void onClose();
    }
}
