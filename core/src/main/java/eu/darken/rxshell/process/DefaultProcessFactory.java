package eu.darken.rxshell.process;


import java.io.IOException;

public class DefaultProcessFactory implements ProcessFactory {
    @Override
    public Process start(String... commands) throws IOException {
        return new ProcessBuilder(commands).start();
    }
}
