package be.imgn.gaia.core.tests.event;

import be.imgn.gaia.core.event.OnVirtualThread;
import be.imgn.gaia.core.event.Subscribe;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Listeners compiled through gaia-processor, each recording what it receives. */
final class Listeners {

    private Listeners() {}

    /** Records what a listener receives. */
    abstract static class Recording {
        private final List<String> received = new ArrayList<>();

        final void record(String what) {
            received.add(what);
        }

        final List<String> received() {
            return received;
        }
    }

    static class OrderListener extends Recording {
        @Subscribe
        void onPlaced(OrderPlaced event) {
            record("placed " + event.id());
        }

        @Subscribe
        void onAny(OrderEvent event) {
            record("any " + event);
        }

        @Subscribe
        void onPlacedToo(OrderPlaced event) {
            record("placed too " + event.id());
        }
    }

    /** Extended by listeners with no index of their own, or with one. */
    static class BaseListener extends Recording {
        @Subscribe
        void onPlaced(OrderPlaced event) {
            record("base " + event.id());
        }
    }

    static class QuietListener extends BaseListener {}

    static class OverridingListener extends BaseListener {
        @Override
        void onPlaced(OrderPlaced event) {
            record("overriding " + event.id());
        }
    }

    static class ExtendingListener extends BaseListener {
        @Subscribe
        void onPlacedToo(OrderPlaced event) {
            record("extending " + event.id());
        }
    }

    static class GenericListener<T> extends Recording {
        @Subscribe
        void onPlaced(OrderPlaced event) {
            record("generic " + event.id());
        }
    }

    static class QueuedListener extends Recording {
        @Subscribe
        void onPlaced(OrderPlaced event) {
            record("placed " + event.id());
        }

        @Queued
        void onQueued(OrderPlaced event) {
            record("queued " + event.id());
        }
    }

    static class OtherQueuedListener extends Recording {
        @Queued
        void onCancelled(OrderCancelled event) {
            record("cancelled " + event.id());
        }
    }

    static class FailingQueuedListener {
        @Queued
        void onPlaced(OrderPlaced event) {
            throw new IllegalStateException("failed " + event.id());
        }
    }

    static class VirtualThreadListener {
        private final CountDownLatch delivered = new CountDownLatch(1);
        private volatile boolean onVirtualThread;

        @OnVirtualThread
        void onPlaced(OrderPlaced event) {
            onVirtualThread = Thread.currentThread().isVirtual();
            delivered.countDown();
        }

        /** Returns whether an event was delivered within ten seconds. */
        boolean awaitDelivery() throws InterruptedException {
            return delivered.await(10, TimeUnit.SECONDS);
        }

        boolean onVirtualThread() {
            return onVirtualThread;
        }
    }

    static class FailingVirtualThreadListener {
        @OnVirtualThread
        void onPlaced(OrderPlaced event) {
            throw new IllegalStateException("failed " + event.id());
        }
    }

    static class Outer<T> extends Recording {
        class Inner {
            @Subscribe
            void onPlaced(OrderPlaced event) {
                record("inner " + event.id());
            }
        }
    }
}
