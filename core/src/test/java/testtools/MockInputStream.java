package testtools;


import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import timber.log.Timber;

public class MockInputStream extends InputStream {
    private final PipedInputStream reader;
    private final PipedOutputStream writer;
    private boolean isOpen = true;

    public MockInputStream() throws IOException {
        writer = new PipedOutputStream();
        reader = new PipedInputStream(writer);
    }

    public void queue(String data) {
        try {
            writer.write(data.getBytes());
            writer.flush();
            Timber.v("Written&Flushed: %s", data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int read() throws IOException {
        return reader.read();
    }

    @Override
    public int read(@NonNull byte[] b) throws IOException {
        return reader.read(b);
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        return reader.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return reader.skip(n);
    }

    @Override
    public int available() throws IOException {
        return reader.available();
    }

    @Override
    public void mark(int readlimit) {
        reader.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        reader.reset();
    }

    @Override
    public boolean markSupported() {
        return reader.markSupported();
    }

    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() throws IOException {
        Timber.d("close() called");
        if (!isOpen) return;
        isOpen = false;
        reader.close();
        writer.flush();
        writer.close();
    }
}
