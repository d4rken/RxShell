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

public abstract class Harvester<T extends Harvester.Crop> extends Flowable<T> implements FlowableTransformer<String, T> {
    static final String TAG = "RXS:Harvester";

    final Publisher<String> source;
    final Cmd cmd;

    public static class Crop {
        public final List<String> buffer;

        public Crop(List<String> buffer) {
            this.buffer = buffer;
        }
    }

    public Harvester(Publisher<String> source, Cmd cmd) {
        this.source = source;
        this.cmd = cmd;
    }

    static abstract class BaseSub<T extends Crop> implements Subscriber<String>, Subscription {
        final String tag;
        final Cmd cmd;
        final Subscriber<? super T> customer;
        final List<String> buffer;
        final FlowableProcessor<String> processor;
        Integer exitCode;
        Subscription subscription;

        BaseSub(String tag, Subscriber<? super T> customer, Cmd cmd) {
            this.tag = tag;
            this.customer = customer;
            this.cmd = cmd;
            if (this instanceof OutputHarvester.OutputSub && cmd.isOutputBufferEnabled() || this instanceof ErrorHarvester.ErrorSub && cmd.isErrorBufferEnabled()) {
                buffer = new ArrayList<>();
            } else {
                buffer = null;
            }
            if (this instanceof OutputHarvester.OutputSub) {
                processor = cmd.getOutputProcessor();
            } else if (this instanceof ErrorHarvester.ErrorSub) {
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

        abstract boolean parse(String line);

        void publishParsed(String contentPart) {
            if (buffer != null) buffer.add(contentPart);
            if (processor != null) processor.onNext(contentPart);
        }

        abstract T buildHarvest();

        @Override
        public void onNext(String line) {
            if (RXSDebug.isDebug()) Timber.tag(tag).v(line);
            if (parse(line)) {
                subscription.cancel();
                customer.onNext(buildHarvest());
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

    public static class Factory {
        public OutputHarvester forOutput(Publisher<String> source, Cmd cmd) {
            return new OutputHarvester(source, cmd);
        }

        public ErrorHarvester forError(Publisher<String> source, Cmd cmd) {
            return new ErrorHarvester(source, cmd);
        }
    }
}