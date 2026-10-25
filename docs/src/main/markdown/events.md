# Events

`gaia-events` is an event bus. It delivers events to consumers, and to the `@Subscribe` methods of listeners through
code that the `gaia-events-processor` annotation processor generates. No reflection is involved: the module of a
listener opens nothing to the bus.

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-events</artifactId>
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
        <artifactId>gaia-events-processor</artifactId>
        <version>${gaia.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

## Subscribing

An [`EventBus`](api:be.imgn.gaia.events.EventBus) delivers each event to the subscribers of the event's class and of
all its supertypes. Any object is an event: records and sealed hierarchies need nothing special.

```java
EventBus bus = EventBus.create();
Subscription subscription = bus.subscribe(OrderPlaced.class, order -> ship(order));
bus.publish(new OrderPlaced(42));
subscription.close();
```

Closing the [`Subscription`](api:be.imgn.gaia.events.Subscription) unsubscribes: the bus holds its subscribers
strongly until then.

## Listeners

The methods of a listener annotated [`@Subscribe`](api:be.imgn.gaia.events.Subscribe) receive the events of the type
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
declared. A method that overrides a `@Subscribe` method receives its events without the annotation.

The processor reports an error for a method that the generated code cannot call: one that is private, or in a private
nested class; static; in an interface; without exactly one parameter, or whose parameter is primitive, an array, a
type variable, a parameterized type or a type the package of the class cannot access; that returns a value; that
declares checked exceptions; or that overrides a `@Subscribe` method, which would deliver each event twice.

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

## On the module path

The processor lists the indexes it generates in `META-INF/services`, which `ServiceLoader` reads on the class path.
On the module path, the module of the listeners must declare them with `provides`, which a processor cannot write:
the `register-services` goal of the [Maven plugin](maven-plugin.md) adds the directives to `module-info.class` after
compilation. The module requires `be.imgn.gaia.events`, and needs to open or export nothing to it.
