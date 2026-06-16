package com.aresstack.corenth.proasteion.platform.security.keepassrpc;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.SecretUnavailableException;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Reflection-based bridge for the external KeePassRPC client object.
 * <p>
 * This keeps the Corenth boundary stable while the concrete KeePassRPC client
 * API can evolve independently. The adapter accepts returned objects exposing
 * username/password via common JavaBean-style methods or a {@link Map}.
 */
public final class ReflectiveKeePassRpcSecretLookup implements KeePassRpcSecretLookup {

    private final Object keepPassRpcClient;

    public ReflectiveKeePassRpcSecretLookup(Object keepPassRpcClient) {
        if (keepPassRpcClient == null) {
            throw new IllegalArgumentException("KeePassRPC client must not be null");
        }
        this.keepPassRpcClient = keepPassRpcClient;
    }

    @Override
    public KeePassRpcSecret findSecret(AccessRequest request) throws SecretUnavailableException {
        Object entry = invokeLookup(request);
        return adaptEntry(entry, request);
    }

    private Object invokeLookup(AccessRequest request) throws SecretUnavailableException {
        String refId = request.credentialRef().id();
        Object entry = tryInvoke("findByRef", new Class<?>[]{String.class}, new Object[]{refId});
        if (entry != null) {
            return entry;
        }
        entry = tryInvoke("find", new Class<?>[]{String.class}, new Object[]{refId});
        if (entry != null) {
            return entry;
        }
        entry = tryInvoke("get", new Class<?>[]{String.class}, new Object[]{refId});
        if (entry != null) {
            return entry;
        }
        entry = tryInvoke("resolve", new Class<?>[]{String.class}, new Object[]{refId});
        if (entry != null) {
            return entry;
        }
        entry = tryInvoke("findLogin", new Class<?>[]{String.class, String.class},
                new Object[]{request.targetSystem(), request.principal()});
        if (entry != null) {
            return entry;
        }
        throw new SecretUnavailableException("KeePassRPC client does not expose a supported lookup method");
    }

    private Object tryInvoke(String methodName, Class<?>[] parameterTypes, Object[] arguments)
            throws SecretUnavailableException {
        try {
            Method method = keepPassRpcClient.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(keepPassRpcClient, arguments);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            throw new SecretUnavailableException("KeePassRPC lookup failed", e);
        }
    }

    private KeePassRpcSecret adaptEntry(Object entry, AccessRequest request) throws SecretUnavailableException {
        if (entry instanceof KeePassRpcSecret) {
            return (KeePassRpcSecret) entry;
        }
        if (entry instanceof Map) {
            return adaptMap((Map<?, ?>) entry, request);
        }
        String principal = firstNonEmpty(
                readString(entry, "principal"),
                readString(entry, "username"),
                readString(entry, "user"),
                request.principal());
        char[] secret = readSecret(entry);
        if (secret == null) {
            throw new SecretUnavailableException("KeePassRPC entry does not expose secret material");
        }
        return new KeePassRpcSecret(principal, secret);
    }

    private KeePassRpcSecret adaptMap(Map<?, ?> map, AccessRequest request) throws SecretUnavailableException {
        String principal = firstNonEmpty(
                asString(map.get("principal")),
                asString(map.get("username")),
                asString(map.get("user")),
                request.principal());
        char[] secret = asSecret(map.get("password"));
        if (secret == null) {
            secret = asSecret(map.get("secret"));
        }
        if (secret == null) {
            throw new SecretUnavailableException("KeePassRPC map entry does not contain password or secret");
        }
        return new KeePassRpcSecret(principal, secret);
    }

    private String readString(Object entry, String propertyName) {
        Object value = readProperty(entry, propertyName);
        return asString(value);
    }

    private char[] readSecret(Object entry) {
        char[] secret = asSecret(readProperty(entry, "password"));
        if (secret == null) {
            secret = asSecret(readProperty(entry, "secret"));
        }
        return secret;
    }

    private Object readProperty(Object entry, String propertyName) {
        Object value = tryRead(entry, propertyName);
        if (value != null) {
            return value;
        }
        String getter = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        return tryRead(entry, getter);
    }

    private Object tryRead(Object entry, String methodName) {
        try {
            Method method = entry.getClass().getMethod(methodName);
            return method.invoke(entry);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonEmpty(String first, String second, String third, String fallback) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        if (third != null && !third.isEmpty()) {
            return third;
        }
        return fallback;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    private static char[] asSecret(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof char[]) {
            char[] chars = (char[]) value;
            char[] copy = new char[chars.length];
            System.arraycopy(chars, 0, copy, 0, chars.length);
            return copy;
        }
        return String.valueOf(value).toCharArray();
    }
}
