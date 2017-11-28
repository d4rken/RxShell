package eu.darken.rxshell.shell;

import android.annotation.SuppressLint;

import java.io.IOException;
import java.io.Reader;

import eu.darken.rxshell.extra.ApiWrap;


public class LineReader {
    private final char[] lineSeparator;

    public LineReader() {
        lineSeparator = getLineSeparator().toCharArray();
    }

    public LineReader(String lineSeparator) {
        this.lineSeparator = lineSeparator.toCharArray();
    }

    @SuppressLint("NewApi")
    public static String getLineSeparator() {
        if (ApiWrap.hasKitKat()) return System.lineSeparator();
        else return System.getProperty("line.separator", "\n");
    }

    public String readLine(Reader reader) throws IOException {
        char curChar;
        int val;
        StringBuilder sb = new StringBuilder(40);
        while ((val = reader.read()) != -1) {
            curChar = (char) val;
            if (curChar == '\n' && lineSeparator.length == 1 && curChar == lineSeparator[0]) {
                return sb.toString();
            } else if (curChar == '\r' && lineSeparator.length == 1 && curChar == lineSeparator[0]) {
                return sb.toString();
            } else if (curChar == '\n' && lineSeparator.length == 2 && curChar == lineSeparator[1]) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == lineSeparator[0]) {
                    sb.deleteCharAt(sb.length() - 1);
                    return sb.toString();
                }
            }
            sb.append(curChar);
        }
        if (sb.length() == 0) return null;
        return sb.toString();
    }
}
