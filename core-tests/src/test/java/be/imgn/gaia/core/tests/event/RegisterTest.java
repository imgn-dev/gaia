package be.imgn.gaia.core.tests.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.core.tests.event.Listeners.BaseListener;
import be.imgn.gaia.core.tests.event.Listeners.ExtendingListener;
import be.imgn.gaia.core.tests.event.Listeners.GenericListener;
import be.imgn.gaia.core.tests.event.Listeners.ImplementingListener;
import be.imgn.gaia.core.tests.event.Listeners.OrderListener;
import be.imgn.gaia.core.tests.event.Listeners.Outer;
import be.imgn.gaia.core.tests.event.Listeners.OverridingListener;
import be.imgn.gaia.core.tests.event.Listeners.QuietListener;
import be.imgn.gaia.core.tests.event.Listeners.RecordListener;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link EventBus#register(Object)} on the module path, with the listeners of {@link Listeners}, whose
 * generated indexes this module provides.
 */
class RegisterTest {

    private final EventBus bus = EventBus.create();

    @Test
    void subscribesTheMethodsOfAListenerInTheOrderTheyAreDeclared() {
        var listener = new OrderListener();

        bus.register(listener);
        bus.publish(new OrderPlaced(1));
        bus.publish(new OrderCancelled(2));

        assertThat(listener.received())
                .containsExactly("placed 1", "placed too 1", "any OrderPlaced[id=1]", "any OrderCancelled[id=2]");
    }

    @Test
    void subscribesTheMethodsOfSuperclassesFirst() {
        var listener = new ExtendingListener();

        bus.register(listener);
        bus.publish(new OrderPlaced(1));

        assertThat(listener.received()).containsExactly("base 1", "extending 1");
    }

    @Test
    void subscribesTheMethodsOfSuperclassesOfAClassWithoutIndex() {
        var listener = new QuietListener();

        bus.register(listener);
        bus.publish(new OrderPlaced(1));

        assertThat(listener.received()).containsExactly("base 1");
    }

    @Test
    void deliversToAMethodThatOverridesASubscribeMethod() {
        var listener = new OverridingListener();

        bus.register(listener);
        bus.publish(new OrderPlaced(1));

        assertThat(listener.received()).containsExactly("overriding 1");
    }

    @Test
    void subscribesGenericAndInnerClasses() {
        var generic = new GenericListener<String>();
        var outer = new Outer<String>();

        bus.register(generic);
        bus.register(outer.new Inner());
        bus.publish(new OrderPlaced(1));

        assertThat(generic.received()).containsExactly("generic 1");
        assertThat(outer.received()).containsExactly("inner 1");
    }

    @Test
    void subscribesTheMethodsOfRecordsAndAbstractMethods() {
        var record = new RecordListener(new ArrayList<>());
        var implementing = new ImplementingListener();

        bus.register(record);
        bus.register(implementing);
        bus.publish(new OrderPlaced(1));

        assertThat(record.received()).containsExactly("record 1");
        assertThat(implementing.received()).containsExactly("implementing 1");
    }

    @Test
    void unsubscribesAllTheMethodsOnClose() {
        var listener = new OrderListener();

        bus.register(listener).close();
        bus.publish(new OrderPlaced(1));

        assertThat(listener.received()).isEmpty();
    }

    @Test
    void subscribesTheMethodsOfTheClassesAnAnonymousClassExtends() {
        var listener = new BaseListener() {};

        bus.register(listener);
        bus.publish(new OrderPlaced(1));

        assertThat(listener.received()).containsExactly("base 1");
    }

    @Test
    void rejectsAListenerWithoutIndex() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bus.register(new Object()))
                .withMessage("java.lang.Object has no @Subscribe methods, or was not compiled with gaia-processor");
    }
}
