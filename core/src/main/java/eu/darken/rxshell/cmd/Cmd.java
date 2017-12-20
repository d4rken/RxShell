package eu.darken.rxshell.cmd;

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import eu.darken.rxshell.process.RxProcess;
import io.reactivex.Single;
import io.reactivex.processors.FlowableProcessor;

public class Cmd {
    public static class ExitCode extends RxProcess.ExitCode {
        public static final int INITIAL = -99;
        public static final int SHELL_DIED = -3;
        public static final int TIMEOUT = -2;
        public static final int EXCEPTION = -1;
    }

    private final String marker = UUID.randomUUID().toString();
    private final List<String> commands;
    private final long timeout;
    private final FlowableProcessor<String> outputProcessor;
    private final FlowableProcessor<String> errorProcessor;
    private final boolean useOutputBuffer;
    private final boolean useErrorBuffer;

    @SuppressWarnings("unused")
    private Cmd() throws InstantiationException {
        throw new InstantiationException("Use the builder()!");
    }

    Cmd(Builder builder) {
        commands = builder.commands;
        timeout = builder.timeout;
        useOutputBuffer = builder.outputBuffer;
        useErrorBuffer = builder.errorBuffer;
        outputProcessor = builder.outputProcessor;
        errorProcessor = builder.errorProcessor;
    }

    public FlowableProcessor<String> getOutputProcessor() {
        return outputProcessor;
    }

    public FlowableProcessor<String> getErrorProcessor() {
        return errorProcessor;
    }

    public List<String> getCommands() {
        return commands;
    }

    public String getMarker() {
        return marker;
    }

    public long getTimeout() {
        return timeout;
    }

    public boolean isOutputBufferEnabled() {
        return useOutputBuffer;
    }

    public boolean isErrorBufferEnabled() {
        return useErrorBuffer;
    }

    @Override
    public String toString() {
        return "Cmd(timeout=" + timeout + ", commands=" + commands + ")";
    }

    public static class Result {
        private final Cmd cmd;
        private final int exitCode;
        @Nullable private final List<String> output;
        @Nullable private final List<String> errors;

        public Result(Cmd cmd) {
            this(cmd, Cmd.ExitCode.INITIAL);
        }

        public Result(Cmd cmd, int exitCode) {
            this(cmd, exitCode, null, null);
        }

        public Result(Cmd cmd, int exitCode, @Nullable List<String> output, @Nullable List<String> errors) {
            this.cmd = cmd;
            this.exitCode = exitCode;
            this.output = output;
            this.errors = errors;
        }

        public Cmd getCmd() {
            return cmd;
        }

        /**
         * The last exitcode emitted by the executed commands.
         * <p>Think:
         * {@code
         * YOUR_COMMAND;YOUR_COMMAND;echo $?
         * }
         * <p>For convenience {@link ExitCode}
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * Your command's output.
         * <p>The shell processes' {@code STDOUT} during the execution your commands.
         * <p>Maybe null depending on {@link Builder#outputBuffer(boolean)}
         */
        @Nullable
        public List<String> getOutput() {
            return output;
        }

        /**
         * Your command's errors.
         * <p>The shell processes' {@code STDERR} during the execution your commands.
         * <p>Maybe null depending on {@link Builder#errorBuffer(boolean)} (boolean)}
         */
        @Nullable
        public List<String> getErrors() {
            return errors;
        }

        public Collection<String> merge() {
            List<String> merged = new ArrayList<>();
            if (getOutput() != null) merged.addAll(getOutput());
            if (getErrors() != null) merged.addAll(getErrors());
            return merged;
        }

        @Override
        public String toString() {
            return "Cmd.Result(cmd=" + cmd + ", exitcode=" + getExitCode() + ", output.size()=" + (output != null ? output.size() : null) + ", errors.size()=" + (errors != null ? errors.size() : null) + ")";
        }
    }

    public static Builder builder(String... commands) {
        return new Builder().input(commands);
    }

    public static Builder builder(Collection<String> commands) {
        return new Builder().input(commands);
    }

    public static Builder from(Cmd source) {
        return new Builder(source);
    }

    public static class Builder {
        final List<String> commands = new ArrayList<>();
        FlowableProcessor<String> outputProcessor;
        FlowableProcessor<String> errorProcessor;
        long timeout = 0;
        boolean outputBuffer = true;
        boolean errorBuffer = true;

        public Builder() {
        }

        public Builder(Cmd source) {
            input(source.getCommands());
            outputBuffer(source.isOutputBufferEnabled());
            errorBuffer(source.isErrorBufferEnabled());
            outputProcessor(source.getOutputProcessor());
            errorProcessor(source.getErrorProcessor());
            timeout(source.getTimeout());
        }

        public Builder input(String... commands) {
            this.commands.addAll(Arrays.asList(commands));
            return this;
        }

        public Builder input(Collection<String> commands) {
            this.commands.addAll(commands);
            return this;
        }

        public Builder outputBuffer(boolean enabled) {
            this.outputBuffer = enabled;
            return this;
        }

        public Builder errorBuffer(boolean enabled) {
            this.errorBuffer = enabled;
            return this;
        }

        public Builder outputProcessor(FlowableProcessor<String> outputProcessor) {
            this.outputProcessor = outputProcessor;
            return this;
        }

        public Builder errorProcessor(FlowableProcessor<String> errorProcessor) {
            this.errorProcessor = errorProcessor;
            return this;
        }

        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Cmd build() {
            if (commands.isEmpty()) throw new IllegalArgumentException("Trying to create a Command without commands.");
            return new Cmd(this);
        }

        public Single<Result> submit(RxCmdShell.Session session) {
            return session.submit(build());
        }

        public Result execute(RxCmdShell.Session session) {
            return submit(session).blockingGet();
        }

        public Single<Result> submit(RxCmdShell shell) {
            final Cmd cmd = build();
            return shell.isAlive().flatMap(wasAlive ->
                    shell.open().flatMap(session ->
                            session.submit(cmd).flatMap(result -> {
                                if (!wasAlive) return session.close().map(integer -> result);
                                else return Single.just(result);
                            })));
        }

        public Result execute(RxCmdShell shell) {
            return submit(shell).blockingGet();
        }
    }

}
