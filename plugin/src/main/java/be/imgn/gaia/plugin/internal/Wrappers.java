package be.imgn.gaia.plugin.internal;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Wraps an object behind one of its interfaces, so that each call runs with a given context class loader, and gives the
 * caller's back afterwards, whatever the call did with it.
 *
 * <p>A wrapper is equal only to itself, and hides the object it wraps: its class is a proxy class, and its invocation
 * handler is this class, whose fields no other module can read.
 */
final class Wrappers implements InvocationHandler {

    private static final AtomicLong IDS = new AtomicLong();

    // One wrapper per handler: the id of the handler identifies the wrapper.
    private final long id = IDS.getAndIncrement();
    private final Object target;
    private final @Nullable ClassLoader contextClassLoader;

    private Wrappers(Object target, @Nullable ClassLoader contextClassLoader) {
        this.target = target;
        this.contextClassLoader = contextClassLoader;
    }

    /**
     * Wraps an object.
     *
     * @param type the interface the wrapper implements
     * @param target the object it calls
     * @param contextClassLoader the context class loader each call runs with
     * @return the wrapper
     */
    static <T> T wrap(Class<T> type, T target, @Nullable ClassLoader contextClassLoader) {
        var wrapper = Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, new Wrappers(target, contextClassLoader));
        return type.cast(wrapper);
    }

    /**
     * Checks that a type can be wrapped: a public interface of a package exported to this module.
     *
     * @param type the type
     * @param role what the type is for, for the message
     * @throws IllegalArgumentException if the type cannot be wrapped
     */
    static void checkWrappable(Class<?> type, String role) {
        var module = Wrappers.class.getModule();
        checkArgument(
                type.isInterface()
                        && Modifier.isPublic(type.getModifiers())
                        && type.getModule().isExported(type.getPackageName(), module),
                "The %s %s is not a public interface of a package exported to %s",
                role,
                type.getName(),
                module.getName());
    }

    @Override
    public @Nullable Object invoke(Object proxy, Method method, @Nullable Object @Nullable [] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class && !method.getName().equals("toString")) {
            return method.getName().equals("equals")
                    ? isSameWrapper(requireNonNull(args)[0])
                    : (Object) Long.hashCode(id);
        }
        var thread = Thread.currentThread();
        var callers = thread.getContextClassLoader();
        thread.setContextClassLoader(contextClassLoader);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw requireNonNullElse(e.getCause(), e);
        } finally {
            thread.setContextClassLoader(callers);
        }
    }

    private boolean isSameWrapper(@Nullable Object other) {
        return other != null
                && Proxy.isProxyClass(other.getClass())
                && Proxy.getInvocationHandler(other) instanceof Wrappers wrapper
                && wrapper.id == id;
    }
}
