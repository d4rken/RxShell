package eu.darken.rxshell.cmd;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.darken.rxshell.extra.RXSDebug;
import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.processors.FlowableProcessor;
import timber.log.Timber;

public class Harvester extends Flowable<Harvester.Batch> implements FlowableTransformer<String, Harvester.Batch> {
    private static final String TAG = "RXS:Harvester";

    enum Type {
        OUTPUT, ERROR
    }

    private final Publisher<String> source;
    private final Cmd cmd;
    private final Type type;

    public static class Batch {
        public final Integer exitCode;
        public final List<String> buffer;

        public Batch(Integer exitCode, List<String> buffer) {
            this.exitCode = exitCode;
            this.buffer = buffer;
        }
    }

    public Harvester(Publisher<String> source, Cmd cmd, Type type) {
        this.source = source;
        this.cmd = cmd;
        this.type = type;
    }

    @Override
    protected void subscribeActual(Subscriber<? super Batch> actual) {
        if (type == Type.OUTPUT) source.subscribe(new OutputHarvester(actual, cmd));
        else if (type == Type.ERROR) source.subscribe(new ErrorHarvester(actual, cmd));
        else throw new IllegalArgumentException("Type is " + type);
    }

    @Override
    public Publisher<Batch> apply(Flowable<String> upstream) {
        return new Harvester(upstream, cmd, type);
    }

    private static abstract class BaseHarvester implements Subscriber<String>, Subscription {
        final String tag;
        final Cmd cmd;
        final Subscriber<? super Batch> customer;
        final List<String> buffer;
        final FlowableProcessor<String> processor;
        Integer exitCode;
        Subscription subscription;

        BaseHarvester(String tag, Subscriber<? super Harvester.Batch> customer, Cmd cmd) {
            this.tag = tag;
            this.customer = customer;
            this.cmd = cmd;
            if (this instanceof OutputHarvester && cmd.isOutputBufferEnabled() || this instanceof ErrorHarvester && cmd.isErrorBufferEnabled()) {
                buffer = new ArrayList<>();
            } else {
                buffer = null;
            }
            if (this instanceof OutputHarvester) {
                processor = cmd.getOutputProcessor();
            } else if (this instanceof ErrorHarvester) {
                processor = cmd.getErrorProcessor();
            } else {
                throw new RuntimeException();
            }
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            if (SubscriptionHelper.validate(this.subscription, subscription)) {
                this.subscription = subscription;
                customer.onSubscribe(this);
            }
        }

        public abstract boolean parse(String line);

        void publishParsed(String contentPart) {
            if (buffer != null) buffer.add(contentPart);
            if (processor != null) processor.onNext(contentPart);
        }

        @Override
        public void onNext(String line) {
            if (parse(line)) {
                subscription.cancel();
                customer.onNext(new Batch(exitCode, buffer));
                customer.onComplete();
                if (processor != null) processor.onComplete();
            }
        }

        @Override
        public void onError(Throwable e) {
            if (RXSDebug.isDebug()) Timber.tag(tag).v("onError(%s)", e.toString());
            customer.onError(e);
            if (processor != null) processor.onError(e);
        }

        @Override
        public void onComplete() {
            if (RXSDebug.isDebug()) Timber.tag(tag).v("onComplete()");
            onError(new IOException("Upstream completed prematurely."));
        }

        @Override
        public void request(long n) {
            if (RXSDebug.isDebug()) Timber.tag(tag).v("request(%d)", n);
            subscription.request(n);
        }

        @Override
        public void cancel() {
            if (RXSDebug.isDebug()) Timber.tag(tag).v("cancel()");
            subscription.cancel();
        }
    }

    private static class ErrorHarvester extends BaseHarvester {
        private static final String TAG = Harvester.TAG + ":Error";

        ErrorHarvester(Subscriber<? super Batch> customer, Cmd cmd) {
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
                if (RXSDebug.isDebug()) Timber.tag(TAG).w(contentPart);
            }

            return markerIndex >= 0;
        }
    }

    private static class OutputHarvester extends BaseHarvester {
        private static final String TAG = Harvester.TAG + ":Output";

        OutputHarvester(Subscriber<? super Batch> customer, Cmd cmd) {
            super(TAG, customer, cmd);
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
                if (RXSDebug.isDebug()) Timber.tag(TAG).i(line);
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
    }

    public static class Factory {
        public Harvester create(Publisher<String> source, Cmd cmd, Type type) {
            return new Harvester(source, cmd, type);
        }
    }
}