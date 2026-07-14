package wtf.opal.event.registry;

import wtf.opal.event.listener.ListenerMethod;
import wtf.opal.event.subscriber.IEventSubscriber;
import wtf.opal.event.subscriber.Subscribe;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public final class EventRegistry {

    private final Map<Class<?>, List<ListenerMethod>> subscriberMap = new HashMap<>();

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private void subscribe(final Object instance, final Class<?> clazzOwner) {
        final IEventSubscriber listener = (IEventSubscriber) instance;
        for (final Method method : clazzOwner.getDeclaredMethods()) {
            final Subscribe subscribe = method.getDeclaredAnnotation(Subscribe.class);
            if (subscribe == null || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            final Class<?> type = method.getParameterTypes()[0];
            final MethodType methodType = MethodType.methodType(void.class, type);
            try {
                final MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazzOwner, LOOKUP);

                final MethodHandle methodHandle = privateLookup.findVirtual(
                        clazzOwner,
                        method.getName(),
                        methodType
                );

                final CallSite site = new ConstantCallSite(methodHandle);
                final ListenerMethod listenerMethod = new ListenerMethod(subscribe.priority(), site, listener);
                subscriberMap.computeIfAbsent(type, x -> new ArrayList<>()).add(listenerMethod);
            } catch (IllegalAccessException | NoSuchMethodException e) {
                throw new RuntimeException("Error subscribing event: " + method.getName(), e);
            }
        }
    }

    public void subscribe(final Object subscriber) {
        this.subscribe(subscriber, subscriber.getClass());

        Class<?> parent = subscriber.getClass().getSuperclass();
        while(parent != Object.class) {
            this.subscribe(subscriber, parent);
            parent = parent.getSuperclass();
        }

        this.sortSubscribers();
    }

    private void sortSubscribers() {
        for (final List<ListenerMethod> callsiteList : subscriberMap.values()) {
            callsiteList.sort(Comparator.comparingInt(ListenerMethod::getPriority));
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void dispatch(final Object event) {
        final List<ListenerMethod> listenerMethods = subscriberMap.get(event.getClass());
        if (listenerMethods != null) {
            for (int i = 0; i < listenerMethods.size(); i++) {
                final ListenerMethod listenerMethod = listenerMethods.get(i);
                if (listenerMethod.invoke(event)) {
                    break;
                }
            }
        }
    }

}
