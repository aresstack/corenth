package de.zrb.bund.api;

/**
 * Auxiliary "Small-LLM" service for short, bounded text-generation tasks.
 *
 * <p>Der Summarizer ist ein <b>separater, üblicherweise kleiner und lokaler</b>
 * Sprachmodell-Service neben dem Haupt-Chat-Provider. Er ist auf
 * <em>extrem kurze Anfragen mit harten Token-Limits</em> ausgelegt
 * (typische Antwort: 1 Satz, 1 Label, 1 Bullet-Liste mit 3 Punkten) und damit
 * billig in CPU/Latenz/Cost.</p>
 *
 * <h3>Verwendungsempfehlungen</h3>
 * <ul>
 *   <li><b>Mermaid/UML in {@code SplitPreviewTab}</b> — statt
 *       „5 Statements"-Platzhalter eine inhaltliche Kurzbeschreibung
 *       („Validierung der Eingabe + Mapping") als Knoten-Label.</li>
 *   <li><b>RAG-Indexierung</b> — Chunk-Titel für Suchergebnisse
 *       (statt der ersten 80 Zeichen).</li>
 *   <li><b>Datei-Browser-Tooltips</b> — 1-Satz-Beschreibung großer Dateien.</li>
 *   <li><b>Chat-Sitzungen</b> — Auto-Titel für gespeicherte Chats
 *       (statt „Chat 2026-05-18 14:23").</li>
 *   <li><b>Commit-/Job-Nachrichten</b> — kurze Beschreibung beim JES-Upload.</li>
 *   <li><b>„Was steht in dieser Spalte?"</b> in Datentabellen — automatische
 *       Inferenz semantischer Spalten-Bezeichner aus Beispiel-Werten.</li>
 * </ul>
 *
 * <h3>Delegationsmodell</h3>
 * <p>Wenn der Summarizer in den Einstellungen <em>nicht</em> aktiviert ist,
 * werden Aufrufe an den aktiven Chat-Provider delegiert (mit demselben System-
 * Prompt + Max-Token-Limit, damit das Output-Format konstant bleibt). Damit
 * funktioniert die API immer — der Nutzer schaltet den dedizierten Mini-LLM
 * nur bei Bedarf an, um Kosten/Latenz weiter zu drücken.</p>
 *
 * <h3>Implementierungs-Hinweise</h3>
 * <ul>
 *   <li>Aufrufer sollten <b>nie</b> mit langen Texten arbeiten — der Service
 *       erzwingt kurze Antworten, aber lange Eingaben sollten vorher
 *       gekürzt/chunked werden (typ. ≤ 2 KB Eingabetext).</li>
 *   <li>Aufrufe sind <b>synchron, blockierend</b> — wenn aus dem EDT
 *       aufgerufen, vorher in einen Worker auslagern.</li>
 *   <li>Bei Fehlern (Timeout, kein Provider konfiguriert, Service deaktiviert)
 *       liefert die Implementierung den <em>Fallback-String</em> aus
 *       {@link SummarizeOptions#fallback()} — niemals null.</li>
 * </ul>
 */
public interface SummarizerService {

    /**
     * Kurz-Zusammenfassung eines Textes nach den Vorgaben in {@code opts}.
     *
     * @param text    Eingabetext (Code, Logzeilen, Beschreibung, …).
     *                Wird intern auf eine sichere Maximallänge gekappt.
     * @param opts    Stil, harte Längen-Limits, Fallback-Text.
     * @return        Niemals {@code null}. Bei Fehler oder deaktiviertem Service
     *                wird {@link SummarizeOptions#fallback()} zurückgegeben.
     */
    String summarize(String text, SummarizeOptions opts);

    /**
     * Generischer „Quick-Task" — frei wählbarer System-Prompt für Aufgaben
     * jenseits klassischer Zusammenfassung (klassifizieren, beschriften,
     * benennen, …). Output wird ebenfalls hart auf {@code maxTokens} begrenzt.
     *
     * @param systemPrompt   Rolle/Aufgabe (z.&nbsp;B. „Du bist ein Sprach-Klassifizierer …")
     * @param userText       Eingabetext
     * @param maxTokens      hartes Token-Limit (typ. 16–128)
     * @return Antwort des Modells, oder Leer-String bei Fehler.
     */
    String quickTask(String systemPrompt, String userText, int maxTokens);

    /**
     * Asynchrone Variante von {@link #summarize(String, SummarizeOptions)}.
     * <p>Aufrufer können den zurückgegebenen Task aus Hintergrund-Threads
     * heraus auf das Ergebnis warten lassen ({@link Runnable#run() callback.run()}
     * wird auf dem EDT aufgerufen, falls verfügbar).</p>
     *
     * @param text     Eingabetext
     * @param opts     Zusammenfassungs-Optionen
     * @param callback wird mit dem Ergebnis aufgerufen (UI-Thread, falls Swing aktiv)
     */
    void summarizeAsync(String text, SummarizeOptions opts, java.util.function.Consumer<String> callback);

    /**
     * @return {@code true}, wenn ein dedizierter Mini-LLM aktiv ist;
     *         {@code false}, wenn an den Haupt-Chat-Provider delegiert wird
     *         (oder gar kein Provider konfiguriert ist).
     */
    boolean isDedicated();

    /**
     * @return {@code true}, wenn der Service Zusammenfassungen in UML/Mermaid-
     *         Diagrammen erzeugen darf (separate UI-Checkbox in den KI-Einstellungen).
     *         Bei {@code false} liefert {@link #summarize(String, SummarizeOptions)}
     *         für UML-Aufrufe direkt den Fallback ohne Provider-Anfrage.
     */
    boolean isUmlSummarizationEnabled();
}

