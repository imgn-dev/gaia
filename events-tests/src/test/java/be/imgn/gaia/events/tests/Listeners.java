package be.imgn.gaia.events.tests;

import be.imgn.gaia.events.Subscribe;
import java.util.ArrayList;
import java.util.List;

/** Listeners compiled through gaia-events-processor, each recording what it receives. */
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

    static class Outer<T> extends Recording {
        class Inner {
            @Subscribe
            void onPlaced(OrderPlaced event) {
                record("inner " + event.id());
            }
        }
    }
}
