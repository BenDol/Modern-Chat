package com.chatimproved.util;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reflection helpers for locating annotations on proxied types and full hierarchies.
 */
public final class ReflectionUtil
{
    private ReflectionUtil() {}

    /**
     * Find the first annotation of the given type on the object's class hierarchy.
     * Works with JDK proxies: it inspects all implemented interfaces too.
     */
    public static <A extends Annotation> A findAnnotation(Object instance, Class<A> annotationClass)
    {
        if (instance == null) return null;
        return findAnnotation(instance.getClass(), annotationClass);
    }

    /**
     * Find the first annotation of the given type on the class hierarchy:
     * class → superclasses → all interfaces (recursively).
     * For repeatable annotations, returns the first occurrence.
     */
    public static <A extends Annotation> A findAnnotation(Class<?> type, Class<A> annotationClass)
    {
        if (type == null || annotationClass == null) return null;

        final Set<Class<?>> visited = new HashSet<>();
        final Deque<Class<?>> stack = new ArrayDeque<>();
        stack.push(type);

        while (!stack.isEmpty())
        {
            final Class<?> c = stack.pop();
            if (c == null || !visited.add(c)) continue;

            // Direct lookup (includes @Inherited on superclasses)
            A ann = c.getAnnotation(annotationClass);
            if (ann == null)
            {
                // Check repeatable containers too
                A[] all = c.getAnnotationsByType(annotationClass);
                if (all.length > 0) ann = all[0];
            }
            if (ann != null) return ann;

            // Traverse upwards and sideways
            stack.push(c.getSuperclass());
            for (Class<?> iface : c.getInterfaces())
            {
                stack.push(iface);
            }
        }
        return null;
    }

    /**
     * Collect all occurrences of an annotation across the entire hierarchy.
     * Useful if you want every repeatable instance or you care about multiple interfaces.
     */
    public static <A extends Annotation> List<A> collectAnnotations(Class<?> type, Class<A> annotationClass)
    {
        if (type == null || annotationClass == null) return Collections.emptyList();

        final List<A> out = new ArrayList<>();
        final Set<Class<?>> visited = new HashSet<>();
        final Deque<Class<?>> stack = new ArrayDeque<>();
        stack.push(type);

        while (!stack.isEmpty())
        {
            final Class<?> c = stack.pop();
            if (c == null || !visited.add(c)) continue;

            // Gather all (handles repeatables)
            A[] anns = c.getAnnotationsByType(annotationClass);
            if (anns.length > 0) out.addAll(Arrays.asList(anns));

            stack.push(c.getSuperclass());
            for (Class<?> iface : c.getInterfaces())
            {
                stack.push(iface);
            }
        }
        return out;
    }

    /**
     * Convenience: true if the annotation exists anywhere in the hierarchy.
     */
    public static boolean hasAnnotation(Class<?> type, Class<? extends Annotation> annotationClass)
    {
        return findAnnotation(type, annotationClass) != null;
    }
}
