package de.zrb.bund.api;

/**
 * Optionen für einen {@link SummarizerService#summarize(String, SummarizeOptions)}-Aufruf.
 *
 * <p>Fabrik-Methoden mit verbreiteten Profilen:</p>
 * <ul>
 *   <li>{@link #label(int)}        — 1 Substantiv-Phrase (z.&nbsp;B. „Validierung der Eingabe")</li>
 *   <li>{@link #sentence(int)}     — 1 vollständiger Satz</li>
 *   <li>{@link #bullets(int,int)}  — bis zu N kurze Bullet-Points</li>
 * </ul>
 *
 * <p>Alle Profile setzen einen Stil-Hinweis im User-Prompt und ein hartes
 * Token-Limit am API-Aufruf. Bei Fehler/Deaktiviertem-Service wird
 * {@link #fallback()} ausgeliefert (typischerweise das, was der Aufrufer
 * sonst eingesetzt hätte — z.&nbsp;B. "5 Anweisungen" für Mermaid).</p>
 */
public final class SummarizeOptions {

    /** Zusammenfassungs-Stil. */
    public enum Style {
        /** Eine kompakte Substantiv-Phrase / ein Knoten-Label. */
        LABEL,
        /** Ein vollständiger Satz. */
        SENTENCE,
        /** Mehrere kurze Bullet-Points (max. {@link #maxBullets()}). */
        BULLETS
    }

    private final Style style;
    private final int maxChars;
    private final int maxTokens;
    private final int maxBullets;
    private final String fallback;
    private final String purposeHint;

    private SummarizeOptions(Style style, int maxChars, int maxTokens,
                             int maxBullets, String fallback, String purposeHint) {
        this.style = style;
        this.maxChars = maxChars;
        this.maxTokens = maxTokens;
        this.maxBullets = maxBullets;
        this.fallback = fallback != null ? fallback : "";
        this.purposeHint = purposeHint;
    }

    public Style style()       { return style; }
    public int maxChars()      { return maxChars; }
    public int maxTokens()     { return maxTokens; }
    public int maxBullets()    { return maxBullets; }
    public String fallback()   { return fallback; }
    /** Optionaler Hinweis an den Summarizer, was die Eingabe fachlich beschreibt. */
    public String purposeHint(){ return purposeHint; }

    public SummarizeOptions withFallback(String fb) {
        return new SummarizeOptions(style, maxChars, maxTokens, maxBullets, fb, purposeHint);
    }

    public SummarizeOptions withPurpose(String hint) {
        return new SummarizeOptions(style, maxChars, maxTokens, maxBullets, fallback, hint);
    }

    // ── Fabriken ───────────────────────────────────────────────────

    /** 1 Substantiv-Phrase, max. {@code maxChars} Zeichen, Token-Cap ~32. */
    public static SummarizeOptions label(int maxChars) {
        return new SummarizeOptions(Style.LABEL, maxChars, 32, 1, "", null);
    }

    /** 1 vollständiger Satz mit ungefähr {@code maxWords} Wörtern. */
    public static SummarizeOptions sentence(int maxWords) {
        int chars = Math.max(60, maxWords * 7);
        int tokens = Math.max(32, maxWords * 2);
        return new SummarizeOptions(Style.SENTENCE, chars, tokens, 1, "", null);
    }

    /** Bis zu {@code n} Bullet-Points, je {@code maxWordsPerBullet} Wörter lang. */
    public static SummarizeOptions bullets(int n, int maxWordsPerBullet) {
        int chars = n * Math.max(40, maxWordsPerBullet * 7);
        int tokens = n * Math.max(20, maxWordsPerBullet * 2);
        return new SummarizeOptions(Style.BULLETS, chars, tokens, n, "", null);
    }
}

