package com.aresstack.corenth.astu.propylaea.natural;

import java.util.Locale;

/**
 * One lexical token of a Natural source line.
 */
final class NaturalToken {

    enum Type {
        /** Keyword, name, number, label or operator run. */
        WORD,
        /** Content of a quoted alphanumeric constant, with doubled quotes collapsed. */
        LITERAL,
        /** A single delimiter character such as {@code (}, {@code )}, {@code =} or {@code ,}. */
        SYMBOL
    }

    private final Type type;
    private final String text;

    NaturalToken(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    Type type() {
        return type;
    }

    String text() {
        return text;
    }

    boolean isWord() {
        return type == Type.WORD;
    }

    boolean isLiteral() {
        return type == Type.LITERAL;
    }

    boolean isSymbol(char symbol) {
        return type == Type.SYMBOL && text.length() == 1 && text.charAt(0) == symbol;
    }

    /** Returns {@code true} if this is a word equal to the keyword, ignoring case. */
    boolean is(String keyword) {
        return type == Type.WORD && text.equalsIgnoreCase(keyword);
    }

    String upper() {
        return text.toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return type + "(" + text + ")";
    }
}
