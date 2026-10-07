package com.aresstack.corenth.astu.propylaea.cobol;

import java.util.Locale;

/**
 * One lexical token of a COBOL program in reference format, with its source position.
 */
final class CobolToken {

    enum Type {
        /** COBOL word, number or picture string. */
        WORD,
        /** Content of an alphanumeric literal, with doubled delimiters collapsed. */
        LITERAL,
        /** Separator period: a period followed by a blank or the end of the line. */
        PERIOD,
        /** A parenthesis. */
        SYMBOL
    }

    private final Type type;
    private final String text;
    private final int line;
    private final int column;

    CobolToken(Type type, String text, int line, int column) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
    }

    Type type() {
        return type;
    }

    String text() {
        return text;
    }

    int line() {
        return line;
    }

    /** Returns the 1-based column in the card image. */
    int column() {
        return column;
    }

    boolean isWord() {
        return type == Type.WORD;
    }

    boolean isLiteral() {
        return type == Type.LITERAL;
    }

    boolean isPeriod() {
        return type == Type.PERIOD;
    }

    boolean isSymbol(char symbol) {
        return type == Type.SYMBOL && text.charAt(0) == symbol;
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
        return type + "(" + text + ")@" + line + ":" + column;
    }
}
