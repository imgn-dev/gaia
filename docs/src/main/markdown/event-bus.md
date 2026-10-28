# Event bus

`gaia-core` has an event bus, in `be.imgn.gaia.core.event`. It delivers events to consumers, and to the `@Subscribe`
methods of listeners through code that the `gaia-processor` annotation processor generates. No reflection is
involved: the module of a listener opens nothing to the bus.

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-core</artifactId>
</dependency>
```

The processor goes on the annotation processor path, never on the class path:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>be.imgn.gaia</groupId>
        <artifactId>gaia-processor</artifactId>
        <version>${gaia.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

## Subscribing

An [`EventBus`](api:be.imgn.gaia.core.event.EventBus) delivers each event to the subscribers of the event's class and of
all its supertypes. Any object is an event: records and sealed hierarchies need nothing special.

```java
EventBus bus = EventBus.create();
Subscription subscription = bus.subscribe(OrderPlaced.class, order -> ship(order));
bus.publish(new OrderPlaced(42));
subscription.close();
```

Closing the [`Subscription`](api:be.imgn.gaia.core.event.Subscription) unsubscribes: the bus holds its subscribers
strongly until then.

## Listeners

The methods of a listener annotated [`@Subscribe`](api:be.imgn.gaia.core.event.Subscribe) receive the events of the type
of their parameter:

```java
class OrderListener {
    @Subscribe
    void onPlaced(OrderPlaced event) {
        ...
    }

    @Subscribe
    void onAny(OrderEvent event) {
        ...
    }
}

Subscription subscription = bus.register(new OrderListener());
```

For each class with such methods, the processor generates an index that calls them, named after the class:
`com.example.$$OrderListener$Subscribers` for `com.example.OrderListener`, `$$Outer$Inner$Subscribers` for a nested
class. `register` finds the indexes of the listener's class and of the classes it extends with `ServiceLoader`, and
subscribes the methods of a superclass before those of its subclasses, and those of one class in the order they are
declared. A method that overrides a `@Subscribe` method receives its events without the annotation, and so does one
that overrides a method with a [subscribing annotation](#subscribing-annotations).

The processor reports an error for a method that the generated code cannot call: one that is private, or in a private
nested class; static; in an interface; without exactly one parameter, or whose parameter is primitive, an array, a
type variable, a parameterized type or a type the package of the class cannot access; that returns a value; that
declares checked exceptions; or that overrides a `@Subscribe` method, or one with a subscribing annotation, which
would deliver each event twice.

javac hands annotation processors no method of a local or anonymous class, so the processor cannot see `@Subscribe`
there: registering an instance of such a class subscribes the methods of the classes it extends only. A listener
whose class and superclasses have no index is rejected with an `IllegalArgumentException`.

## Delivery

An event reaches the subscribers of its class first, then those of its supertypes, a type always before its own
supertypes and `Object` last, and the subscribers of one type in the order they subscribed.

By default, the bus delivers an event on the thread that publishes it, before `publish` returns. An event that a
subscriber publishes to the same bus waits until the current one has reached all its subscribers, so that events
arrive in the order they were published. With an executor, the bus hands it one task per event instead, which
delivers the event to its subscribers in order, while separate events may be delivered concurrently:

```java
EventBus bus = EventBus.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
```

An exception thrown by a subscriber goes to the exception handler, and the event goes on to its other subscribers.
The default handler logs it at `ERROR` with `System.Logger`; `newBuilder().exceptionHandler((event, exception) -> ...)`
replaces it. An `Error` propagates.

## Subscribing annotations

An annotation marked [`@SubscribingAnnotation`](api:be.imgn.gaia.core.event.SubscribingAnnotation) subscribes the
methods it annotates, as `@Subscribe` does, and has their events delivered through the executor it names, such as on
the event dispatch thread of Swing:

```java
@SubscribingAnnotation(executor = EdtExecutor.class)
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface OnEdt {}

public final class EdtExecutor implements Executor {
    @Override
    public void execute(Runnable task) {
        EventQueue.invokeLater(task);
    }
}
```

A listener then needs only the annotation, and the bus nothing more:

```java
class Window {
    @OnEdt
    void onPlaced(OrderPlaced event) {
        ...
    }
}

EventBus bus = EventBus.create();
bus.register(new Window());
```

The generated index calls `registrar.subscribe(OrderPlaced.class, OnEdt.class, EdtExecutor::new, l::onPlaced)`. The
first time a bus registers a method with the annotation, it creates the executor, and shares it among all the
listeners it registers. `newBuilder().executor(OnEdt.class, Runnable::run)` gives the bus another executor for the
annotation instead, such as in tests, and the bus then never creates the one the annotation names.

During the delivery of an event, the bus hands the executor a task that calls the method, and goes on to the other
subscribers without waiting for it. The tasks are handed over in the order the events are delivered; whether they run
in that order depends on the executor. A task does not call the method if its subscription was closed meanwhile. An
exception thrown by the method, or by the executor when it is handed the task, goes to the exception handler.

The processor checks these methods as it checks `@Subscribe` ones, and reports a method with more than one subscribing
annotation, `@Subscribe` included. It also reports what keeps the generated code from referring to the annotation and
creating the executor: the executor must be a class, neither abstract nor an inner class, with a constructor without
parameters, all accessible from the package of the listener, and exported to its module. On the declaration of the
annotation, it checks the executor from wherever the annotation can be used, and reports a retention of `SOURCE`: the
annotation must be kept in class files, so that the processor sees it on the methods of classes compiled before.

`gaia-core` has one, [`@OnVirtualThread`](api:be.imgn.gaia.core.event.OnVirtualThread), which delivers each event on
a new virtual thread with [`VirtualThreadExecutor`](api:be.imgn.gaia.core.event.VirtualThreadExecutor). The events then
reach the method in no particular order, and the method can run on several threads at once: the listener must be
thread-safe.

A module that declares such an annotation exports the package of the executor, and requires `be.imgn.gaia.core`
transitively, as javac's `exports` lint asks: the annotation of an exported type is part of its API.

## On the module path

The processor lists the indexes it generates in `META-INF/services`, which `ServiceLoader` reads on the class path.
On the module path, the module of the listeners must declare them with `provides`, which a processor cannot write:
the `register-services` goal of the [Maven plugin](maven-plugin.md) adds the directives to `module-info.class` after
compilation. The module requires `be.imgn.gaia.core`, and needs to open or export nothing to it.
