package eu.darken.rxshell.cmd;

import android.support.annotation.Nullable;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import timber.log.Timber;

public class OutputHarvester extends Harvester<OutputHarvester.Crop> {
    public static class Crop extends Harvester.Crop {
        final Integer exitCode;

        public Crop(@Nullable List<String> buffer, @Nullable Integer exitCode, boolean isComplete) {
            super(buffer, isComplete);
            this.exitCode = exitCode;
        }
    }

    public OutputHarvester(Publisher<String> source, Cmd cmd) {
        super(source, cmd);
    }

    @Override
    protected void subscribeActual(Subscriber<? super Crop> actual) {
        final OutputSub harvester = new OutputSub(actual, cmd);
        source.subscribe(harvester);
    }

    @Override
    public Publisher<Crop> apply(Flowable<String> upstream) {
        return new OutputHarvester(upstream, cmd);
    }

    static class OutputSub extends BaseSub<Crop> {
        private static final String TAG = Harvester.TAG + ":Output";
        private final Cmd cmd;
        int exitCode = Cmd.ExitCode.INITIAL;

        OutputSub(Subscriber<? super Crop> customer, Cmd cmd) {
            super(TAG, customer, cmd.isOutputBufferEnabled() ? new ArrayList<>() : null, cmd.getOutputProcessor());
            this.cmd = cmd;
        }

        @Override
        public boolean parse(String line) {
            String contentPart = line;
            String markerPart = null;

            final int markerIndex = line.indexOf(cmd.getMarker());
            if (markerIndex == 0) {
                contentPart = null;
                markerPart = line;
            } else if (markerIndex > 0) {
                contentPart = line.substring(0, markerIndex);
                markerPart = line.substring(markerIndex);
            }

            if (contentPart != null) {
                publishParsed(contentPart);
            }

            if (markerPart != null) {
                try {
                    exitCode = Integer.valueOf(markerPart.substring(cmd.getMarker().length() + 1), 10);
                } catch (Exception e) {
                    Timber.tag(TAG).e(e);
                    exitCode = Cmd.ExitCode.EXCEPTION;
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        Crop buildCropHarvest(@Nullable List<String> buffer, boolean isComplete) {
            return new Crop(buffer, exitCode, isComplete);
        }
    }
}
