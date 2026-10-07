package com.aresstack.corenth.astu.propylaea;

/**
 * Programming language of a source unit.
 *
 * <p>The set is deliberately small: it names the mainframe languages for which Corenth has
 * verified reference evidence. {@link #UNKNOWN} marks a source whose language is neither given
 * nor detectable; no parser handles it.
 */
public enum SourceLanguage {

    /** Software AG Natural (structured mode). */
    NATURAL,

    /** IBM COBOL in fixed reference format. */
    COBOL,

    /** IBM z/OS Job Control Language. */
    JCL,

    /** Language not given and not detected. */
    UNKNOWN
}
