package com.aresstack.corenth.proasteion.emporion;

/**
 * Generic harbor operation result with a success/failure state.
 */
public final class HarborResult<T> {

    private final T value;
    private final String errorMessage;

    private HarborResult(T value, String errorMessage) {
        this.value = value;
        this.errorMessage = errorMessage;
    }

    public static <T> HarborResult<T> success(T value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return new HarborResult<T>(value, null);
    }

    public static <T> HarborResult<T> failure(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("errorMessage must not be null or blank");
        }
        return new HarborResult<T>(null, errorMessage);
    }

    public boolean isSuccess() {
        return errorMessage == null;
    }

    public T value() {
        return value;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
