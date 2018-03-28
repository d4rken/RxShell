package eu.darken.rxshell.cmd;

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
        private final List<String> output;
        private final List<String> errors;

        public Result(Cmd cmd) {
            this(cmd, Cmd.ExitCode.INITIAL);
        }

        public Result(Cmd cmd, int exitCode) {
            this(cmd, exitCode, new ArrayList<>(), new ArrayList<>());
        }

        public Result(Cmd cmd, int exitCode, @Nullable List<String> output, @Nullable List<String> errors) {
            this.cmd = cmd;
            this.exitCode = exitCode;
            this.output = output;
            this.errors = errors;
        }

        /**
         * The {@link Cmd} that was executed.
         */
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
         * <p>Maybe {@code null} depending on {@link Builder#outputBuffer(boolean)}
         */
        public List<String> getOutput() {
            return output;
        }

        /**
         * Your command's errors.
         * <p>The shell processes' {@code STDERR} during the execution your commands.
         * <p>Maybe {@code null} depending on {@link Builder#errorBuffer(boolean)} (boolean)}
         */
        public List<String> getErrors() {
            return errors;
        }

        /**
         * Merges {@link #getOutput()} and {@link #getErrors()}.
         * <p>Output first, then errors.
         */
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

        /**
         * The commands you want to execute.
         */
        public Builder input(String... commands) {
            this.commands.addAll(Arrays.asList(commands));
            return this;
        }

        /**
         * @see #input(String...)
         */
        public Builder input(Collection<String> commands) {
            this.commands.addAll(commands);
            return this;
        }

        /**
         * Whether the output should be stored and returned to you in {@link Result} after the command finished.
         * <p>If you run into memory issues you can combine this with {@link #outputProcessor(FlowableProcessor)} to process output "on-the-fly".
         * <p>If this is set to {@code false} {@link Result#getOutput()} will return {@code null}.
         *
         * @param enabled defaults to {@code true}
         */
        public Builder outputBuffer(boolean enabled) {
            this.outputBuffer = enabled;
            return this;
        }

        /**
         * @param enabled defaults to {@code true}
         * @see #outputBuffer(boolean)
         */
        public Builder errorBuffer(boolean enabled) {
            this.errorBuffer = enabled;
            return this;
        }

        /**
         * The shell will call {@link FlowableProcessor#onNext(Object)} on this for each line of {@code STDOUT}.
         * <p>Mind the backpressure!
         * Blocking this processor can block the {@code STDOUT} {@link Harvester}
         * Blocking the harvester can block the shell process if buffers run full.
         * <p>The processor emits {@code onComplete} if the command finishes normally and emit an error otherwise.
         * <p>Note: This does NOT emit control sequences used internally by {@link RxCmdShell}
         *
         * @param outputProcessor the processor to use
         */
        public Builder outputProcessor(FlowableProcessor<String> outputProcessor) {
            this.outputProcessor = outputProcessor;
            return this;
        }

        /**
         * @see #outputProcessor(FlowableProcessor)
         */
        public Builder errorProcessor(FlowableProcessor<String> errorProcessor) {
            this.errorProcessor = errorProcessor;
            return this;
        }

        /**
         * A timeout for this command. If the timeout is reached the whole {@link RxCmdShell.Session} is forcibly killed.
         * <p>A command that timed out returns {@link Cmd.ExitCode#TIMEOUT} from {@link Result#getExitCode()}.
         *
         * @param timeout in milliseconds
         */
        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds the command.
         */
        public Cmd build() {
            if (commands.isEmpty()) throw new IllegalArgumentException("Trying to create a Command without commands.");
            return new Cmd(this);
        }

        /**
         * Convenience method for {@link RxCmdShell.Session#submit(Cmd)}
         * <p> Submission happens on subscription.
         *
         * @param session the session to use.
         * @return a {@link Single} that will emit a {@link Cmd.Result} when the command has terminated.
         */
        public Single<Result> submit(RxCmdShell.Session session) {
            return session.submit(build());
        }

        /**
         * Convenience method for {@link #submit(RxCmdShell.Session)} using {@link Single#blockingGet()}
         */
        public Result execute(RxCmdShell.Session session) {
            return submit(session).blockingGet();
        }

        /**
         * This is a convenience method for single-shot execution.
         * <p>It's behavior depends on {@link RxCmdShell#isAlive()}.
         * <br>If the shell is alive, then the existing session is used and kept open.
         * <br>If the shell wasn't alive, a new session is created and closed after the command has terminated.
         *
         * @param shell the {@link RxCmdShell} to use.
         * @return a {@link Single} that will emit a {@link Cmd.Result} when the command has terminated.
         */
        public Single<Result> submit(RxCmdShell shell) {
            final Cmd cmd = build();
            return shell.isAlive().flatMap(wasAlive ->
                    shell.open().flatMap(session ->
                            session.submit(cmd).flatMap(result -> {
                                if (!wasAlive) return session.close().map(integer -> result);
                                else return Single.just(result);
                            })));
        }

        /**
         * Convenience method for {@link #submit(RxCmdShell)} using {@link Single#blockingGet()}
         * <br>If a shell can't be opened {@link ExitCode#EXCEPTION} will be returned and an error message.
         */
        public Result execute(RxCmdShell shell) {
            return submit(shell)
                    .onErrorReturn(err -> {
                        final Cmd cmd = Builder.this.build();

                        List<String> oBuffer = cmd.useOutputBuffer ? new ArrayList<>() : null;
                        List<String> eBuffer = cmd.useErrorBuffer ? Collections.singletonList(err.toString()) : null;

                        if (cmd.outputProcessor != null) {
                            cmd.outputProcessor.onComplete();
                        }
                        if (cmd.errorProcessor != null) {
                            cmd.errorProcessor.onNext(err.toString());
                            cmd.errorProcessor.onComplete();
                        }
                        return new Result(cmd, ExitCode.EXCEPTION, oBuffer, eBuffer);
                    })
                    .blockingGet();
        }
    }

}
