package eu.darken.rxshell.cmd;

import android.support.annotation.Nullable;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;


public class ErrorHarvester extends Harvester<Harvester.Crop> {

    public ErrorHarvester(Publisher<String> source, Cmd cmd) {
        super(source, cmd);
    }

    @Override
    public Publisher<Crop> apply(Flowable<String> upstream) {
        return new ErrorHarvester(upstream, cmd);
    }

    @Override
    protected void subscribeActual(Subscriber<? super Crop> actual) {
        source.subscribe(new ErrorSub(actual, cmd));
    }

    static class ErrorSub extends BaseSub<Crop> {
        private static final String TAG = Harvester.TAG + ":Error";
        private final Cmd cmd;

        ErrorSub(Subscriber<? super ErrorHarvester.Crop> customer, Cmd cmd) {
            super(TAG, customer, cmd.isErrorBufferEnabled() ? new ArrayList<>() : null, cmd.getErrorProcessor());
            this.cmd = cmd;
        }

        @Override
        public boolean parse(String line) {
            String contentPart = line;

            final int markerIndex = line.indexOf(cmd.getMarker());
            if (markerIndex == 0) contentPart = null;
            else if (markerIndex > 0) contentPart = line.substring(0, markerIndex - 1);

            if (contentPart != null) {
                publishParsed(contentPart);
            }

            return markerIndex >= 0;
        }

        @Override
        Crop buildCropHarvest(@Nullable List<String> buffer, boolean isComplete) {
            return new Crop(buffer, isComplete);
        }
    }
}
