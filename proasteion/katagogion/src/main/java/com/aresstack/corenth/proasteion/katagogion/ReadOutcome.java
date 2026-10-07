package com.aresstack.corenth.proasteion.katagogion;

/** Immutable outcome of a {@link MediatedReading}: content, a denial, or an unavailability. */
public final class ReadOutcome {

    /** The kind of outcome. */
    public enum Status {
        CONTENT,
        DENIED,
        UNAVAILABLE
    }

    private final Status status;
    private final String text;
    private final String contentType;
    private final String message;

    private ReadOutcome(Status status, String text, String contentType, String message) {
        this.status = status;
        this.text = text;
        this.contentType = contentType;
        this.message = message;
    }

    /** Create an outcome carrying the resource text. */
    public static ReadOutcome content(String text, String contentType) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        return new ReadOutcome(Status.CONTENT, text, Names.nullToEmpty(contentType), "");
    }

    /** Create an outcome stating that the mediation withheld the content. */
    public static ReadOutcome denied(String reason) {
        return new ReadOutcome(Status.DENIED, null, null, Names.nullToEmpty(reason));
    }

    /** Create an outcome stating that the content could not be obtained. */
    public static ReadOutcome unavailable(String message) {
        return new ReadOutcome(Status.UNAVAILABLE, null, null, Names.nullToEmpty(message));
    }

    public Status status() {
        return status;
    }

    /** Return the text for {@link Status#CONTENT}, otherwise {@code null}. */
    public String text() {
        return text;
    }

    /** Return the content type for {@link Status#CONTENT}, otherwise {@code null}. */
    public String contentType() {
        return contentType;
    }

    /** Return the denial or unavailability message; empty for content. */
    public String message() {
        return message;
    }
}
