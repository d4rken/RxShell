package eu.darken.rxshell.cmd;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import eu.darken.rxshell.extra.RXSDebug;
import io.reactivex.Flowable;
import timber.log.Timber;


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

        ErrorSub(Subscriber<? super Crop> customer, Cmd cmd) {
            super(TAG, customer, cmd);
        }

        @Override
        public boolean parse(String line) {
            String contentPart = line;

            final int markerIndex = line.indexOf(cmd.getMarker());
            if (markerIndex == 0) contentPart = null;
            else if (markerIndex > 0) contentPart = line.substring(0, markerIndex - 1);

            if (contentPart != null) {
                publishParsed(contentPart);
                if (RXSDebug.isDebug()) Timber.tag(TAG).d(contentPart);
            }

            return markerIndex >= 0;
        }

        @Override
        Crop buildHarvest() {
            return new Crop(buffer);
        }
    }
}
