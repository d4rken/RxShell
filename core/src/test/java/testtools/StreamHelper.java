package testtools;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

public class StreamHelper {
    public static InputStream makeStream(String string) {
        return new ByteArrayInputStream(string.getBytes(Charset.defaultCharset()));
    }
}
