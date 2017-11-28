package eu.darken.rxshell.process;


import java.io.IOException;

public interface ProcessFactory {
    Process start(String... commands) throws IOException;
}
