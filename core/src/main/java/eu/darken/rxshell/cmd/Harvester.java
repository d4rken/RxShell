package eu.darken.rxshell.cmd;

import android.support.annotation.Nullable;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
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
        @Nullable final List<String> buffer;
        final boolean isComplete;

        public Crop(@Nullable List<String> buffer, boolean isComplete) {
            this.buffer = buffer;
            this.isComplete = isComplete;
        }
    }

    public Harvester(Publisher<String> source, Cmd cmd) {
        this.source = source;
        this.cmd = cmd;
    }

    static abstract class BaseSub<T extends Crop> implements Subscriber<String>, Subscription {
        private final String tag;
        private final Subscriber<? super T> customer;
        private final FlowableProcessor<String> processor;
        private final List<String> buffer;
        private volatile boolean isDone = false;
        Subscription subscription;

        BaseSub(String tag, Subscriber<? super T> customer, @Nullable List<String> buffer, @Nullable FlowableProcessor<String> processor) {
            this.tag = tag;
            this.customer = customer;
            this.processor = processor;
            this.buffer = buffer;
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

        abstract T buildCropHarvest(@Nullable List<String> buffer, boolean complete);

        void endHarvest(boolean isComplete) {
            if (RXSDebug.isDebug()) Timber.tag(tag).d("endHarvest(isComplete=%b, isDone=%b)", isComplete, isDone);

            if (isDone) return;
            isDone = true;

            subscription.cancel();

            customer.onNext(buildCropHarvest(buffer, isComplete));
            customer.onComplete();

            if (processor != null) {
                if (isComplete) processor.onComplete();
                else processor.onError(new IOException("Upstream completed prematurely."));
            }
        }

        @Override
        public void onNext(String line) {
            if (RXSDebug.isDebug()) Timber.tag(tag).v(line);
            if (parse(line)) endHarvest(true);
        }

        @Override
        public void onError(Throwable e) {
            if (RXSDebug.isDebug()) Timber.tag(tag).v("onError(%s)", e.toString());
            endHarvest(false);
        }

        @Override
        public void onComplete() {
            if (RXSDebug.isDebug()) Timber.tag(tag).v("onComplete()");
            endHarvest(false);
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