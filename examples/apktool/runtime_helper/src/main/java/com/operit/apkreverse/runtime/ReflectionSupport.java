package com.operit.apkreverse.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ReflectionSupport {
    private ReflectionSupport() {
    }

    static Object newInstance(String className, Object... args) throws Exception {
        Class<?> type = loadClass(className);
        Constructor<?> constructor = findConstructor(type, args);
        if (constructor == null) {
            throw new NoSuchMethodException("No compatible constructor found for " + className);
        }
        trySetAccessible(constructor);
        return constructor.newInstance(args);
    }

    static Object invoke(Object target, String methodName, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), methodName, args);
        if (method == null) {
            throw new NoSuchMethodException("No compatible method found: " + target.getClass().getName() + "." + methodName);
        }
        trySetAccessible(method);
        return method.invoke(target, args);
    }

    static Object invokeStatic(String className, String methodName, Object... args) throws Exception {
        Class<?> type = loadClass(className);
        Method method = findMethod(type, methodName, args);
        if (method == null) {
            throw new NoSuchMethodException("No compatible static method found: " + className + "." + methodName);
        }
        trySetAccessible(method);
        return method.invoke(null, args);
    }

    static Object getStaticField(String className, String fieldName) throws Exception {
        Class<?> type = loadClass(className);
        Field field = type.getDeclaredField(fieldName);
        trySetAccessible(field);
        return field.get(null);
    }

    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return Class.forName(className, true, contextClassLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        ClassLoader ownClassLoader = ReflectionSupport.class.getClassLoader();
        if (ownClassLoader != null && ownClassLoader != contextClassLoader) {
            try {
                return Class.forName(className, true, ownClassLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        return Class.forName(className);
    }

    private static void trySetAccessible(java.lang.reflect.AccessibleObject object) {
        try {
            object.setAccessible(true);
        } catch (RuntimeException ignored) {
        }
    }

    private static Constructor<?> findConstructor(Class<?> type, Object[] args) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isCompatible(constructor.getParameterTypes(), args)) {
                return constructor;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String methodName, Object[] args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (isCompatible(method.getParameterTypes(), args)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (isCompatible(method.getParameterTypes(), args)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index += 1) {
            Object arg = args[index];
            Class<?> parameterType = wrap(parameterTypes[index]);
            if (arg == null) {
                if (parameterTypes[index].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!parameterType.isAssignableFrom(wrap(arg.getClass()))) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
