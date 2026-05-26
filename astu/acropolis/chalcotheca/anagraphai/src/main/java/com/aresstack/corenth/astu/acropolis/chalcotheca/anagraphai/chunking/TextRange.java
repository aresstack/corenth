package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking;

public final class TextRange {
    private final int start;
    private final int end;

    public TextRange(int start, int end) {
        if (start < 0) throw new IllegalArgumentException("start must not be negative");
        if (end < start) throw new IllegalArgumentException("end must not be less than start");
        this.start = start;
        this.end = end;
    }

    public int start() { return start; }
    public int end() { return end; }
    public int length() { return end - start; }

    @Override
    public String toString() {
        return "TextRange[" + start + ", " + end + ")";
    }
}
