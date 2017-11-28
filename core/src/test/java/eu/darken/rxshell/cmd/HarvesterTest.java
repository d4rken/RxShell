package eu.darken.rxshell.cmd;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.processors.PublishProcessor;
import io.reactivex.processors.ReplayProcessor;
import io.reactivex.subscribers.TestSubscriber;
import testtools.BaseTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HarvesterTest extends BaseTest {
    @Mock Cmd cmd;
    PublishProcessor<String> publisher;
    Harvester.Factory harvesterFactory;

    @Override
    public void setup() throws Exception {
        super.setup();
        harvesterFactory = new Harvester.Factory();
        publisher = PublishProcessor.create();
    }

    @Test
    public void testCommandCompletion_output() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext(uuid + " 255");

        testSubscriber.assertValueCount(1).assertComplete();

        Harvester.Batch batch = testSubscriber.values().get(0);
        assertThat(batch.exitCode, is(255));
    }

    @Test
    public void testCommandCompletion_errors() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext(uuid);

        testSubscriber.assertValueCount(1).assertComplete();

        Harvester.Batch batch = testSubscriber.values().get(0);
        assertThat(batch.exitCode, is(nullValue()));
    }

    @Test
    public void testBuffers_output() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);
        when(cmd.isOutputBufferEnabled()).thenReturn(true);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext("some-output");
        publisher.onNext(uuid + " 255");

        testSubscriber.assertValueCount(1).assertComplete();

        Harvester.Batch batch = testSubscriber.values().get(0);
        assertThat(batch.buffer.size(), is(1));
        assertThat(batch.buffer, Matchers.contains("some-output"));
    }

    @Test
    public void testBuffers_error() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);
        when(cmd.isErrorBufferEnabled()).thenReturn(true);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext("some-errors");
        publisher.onNext(uuid + " 255");

        testSubscriber.assertValueCount(1).assertComplete();

        Harvester.Batch batch = testSubscriber.values().get(0);
        assertThat(batch.buffer.size(), is(1));
        assertThat(batch.buffer, Matchers.contains("some-errors"));
    }

    @Test
    public void testUpstreamPrematureCompletion_output() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);
        when(cmd.isOutputBufferEnabled()).thenReturn(true);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext("some-output");
        publisher.onComplete();

        testSubscriber.assertValueCount(0).assertError(IOException.class);
    }

    @Test
    public void testUpstreamPrematureCompletion_errors() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);
        when(cmd.isErrorBufferEnabled()).thenReturn(true);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext("some-errors");
        publisher.onComplete();

        testSubscriber.assertValueCount(0).assertError(IOException.class);
    }

    @Test
    public void testUpstreamTerminated_output() {
        publisher.onComplete();
        publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT)).test().assertTerminated().assertError(IOException.class);

        publisher = PublishProcessor.create();
        publisher.onError(new InterruptedException());
        publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT)).test().assertTerminated().assertError(InterruptedException.class);
    }

    @Test
    public void testUpstreamTerminated_error() {
        publisher.onComplete();
        publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR)).test().assertTerminated().assertError(IOException.class);

        publisher = PublishProcessor.create();
        publisher.onError(new InterruptedException());
        publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR)).test().assertTerminated().assertError(InterruptedException.class);
    }

    @Test
    public void testDownstreamCancel_output() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        TestSubscriber<Harvester.Batch> testSubscriber = publisher
                .doOnCancel(latch::countDown)
                .compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT))
                .test();
        testSubscriber.assertNotTerminated();

        testSubscriber.dispose();

        assertThat(latch.await(1, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testDownstreamCancel_errors() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        TestSubscriber<Harvester.Batch> testSubscriber = publisher
                .doOnCancel(latch::countDown)
                .compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR))
                .test();
        testSubscriber.assertNotTerminated();

        testSubscriber.dispose();

        assertThat(latch.await(1, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testProcessors_output() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);
        ReplayProcessor<String> processor = ReplayProcessor.create();
        when(cmd.getOutputProcessor()).thenReturn(processor);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT)).test();

        publisher.onNext("some-output");
        publisher.onNext(uuid + " 255");

        processor.test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).assertValue("some-output");
        Harvester.Batch batch = testSubscriber.awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);
        assertThat(batch.exitCode, is(255));
        assertThat(batch.buffer, is(nullValue()));
    }

    @Test
    public void testProcessors_errors() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);
        ReplayProcessor<String> processor = ReplayProcessor.create();
        when(cmd.getErrorProcessor()).thenReturn(processor);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR)).test();

        publisher.onNext("some-errors");
        publisher.onNext(uuid);

        processor.test().awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).assertValue("some-errors");
        Harvester.Batch batch = testSubscriber.awaitDone(1, TimeUnit.SECONDS).assertNoTimeout().assertValueCount(1).values().get(0);
        assertThat(batch.buffer, is(nullValue()));
        assertThat(batch.exitCode, is(nullValue()));
    }

    @Test
    public void testBadMarker_output() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.OUTPUT)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext(uuid + " &/()");

        testSubscriber.awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        Harvester.Batch batch = testSubscriber.values().get(0);
        assertThat(batch.exitCode, is(Cmd.ExitCode.EXCEPTION));
    }

    @Test
    public void testBadMarker_errors() {
        String uuid = UUID.randomUUID().toString();
        when(cmd.getMarker()).thenReturn(uuid);

        TestSubscriber<Harvester.Batch> testSubscriber = publisher.compose(harvesterFactory.create(publisher, cmd, Harvester.Type.ERROR)).test();
        testSubscriber.assertNotTerminated();

        publisher.onNext(uuid + " §$%&");

        testSubscriber.awaitDone(1, TimeUnit.SECONDS).assertNoTimeout();
        Harvester.Batch batch = testSubscriber.values().get(0);
        assertThat(batch.exitCode, is(nullValue()));
    }
}
