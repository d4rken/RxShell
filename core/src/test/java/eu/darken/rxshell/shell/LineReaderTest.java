package eu.darken.rxshell.shell;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import eu.darken.rxshell.extra.ApiWrap;
import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static testtools.StreamHelper.makeStream;

@RunWith(MockitoJUnitRunner.class)
public class LineReaderTest extends BaseTest {

    @Test
    public void testGetLineSeperator() {
        assertThat(LineReader.getLineSeparator(), is(notNullValue()));
        ApiWrap.setSDKInt(26);
        assertThat(LineReader.getLineSeparator(), is(System.lineSeparator()));
        ApiWrap.setSDKInt(16);
        assertThat(LineReader.getLineSeparator(), is(System.getProperty("line.separator", "\n")));
    }

    @Test
    public void testLineEndings_linux() throws IOException {
        final List<String> output = new ArrayList<>();
        final LineReader reader = new LineReader("\n");
        Reader stream = new BufferedReader(new InputStreamReader(makeStream("line1\r\nline2\r\nli\rne\n\n")));
        String line;
        while ((line = reader.readLine(stream)) != null) {
            output.add(line);
        }
        assertThat(output.size(), is(4));
        assertThat(output.get(0), is("line1\r"));
        assertThat(output.get(1), is("line2\r"));
        assertThat(output.get(2), is("li\rne"));
        assertThat(output.get(3), is(""));
    }

    @Test
    public void testLineEndings_windows() throws IOException {
        final List<String> output = new ArrayList<>();
        final LineReader reader = new LineReader("\r\n");
        Reader stream = new BufferedReader(new InputStreamReader(makeStream("line1\r\nline2\r\n\r\n")));
        String line;
        while ((line = reader.readLine(stream)) != null) {
            output.add(line);
        }
        assertThat(output.size(), is(3));
        assertThat(output.get(0), is("line1"));
        assertThat(output.get(1), is("line2"));
        assertThat(output.get(2), is(""));
    }

    @Test
    public void testLineEndings_legacy() throws IOException {
        final List<String> output = new ArrayList<>();
        final LineReader reader = new LineReader("\r");
        Reader stream = new BufferedReader(new InputStreamReader(makeStream("line1\n\rline2\n\rli\nne\r\r")));
        String line;
        while ((line = reader.readLine(stream)) != null) {
            output.add(line);
        }
        assertThat(output.size(), is(4));
        assertThat(output.get(0), is("line1\n"));
        assertThat(output.get(1), is("line2\n"));
        assertThat(output.get(2), is("li\nne"));
        assertThat(output.get(3), is(""));
    }
}
