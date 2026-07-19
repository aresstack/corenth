# Exedra Headless-Test-Verifikation — Befund und Restaufgabe

**Stand:** 2026-07-19, geprüft gegen `main` @ `3561932`

## Kernaussage (Korrektur der bisherigen Planung)

Die bisherige Annahme — „Headless-Fix für die Exedra-Swing-Tests als Slice 0 vor jeder weiteren Backend-Arbeit“ — ist empirisch widerlegt. Die `proasteion:exedra`-Testsuite läuft in einer normalen headless Linux-Umgebung bereits vollständig grün:

```text
48 Tests bestanden, 0 fehlgeschlagen, 2 übersprungen (per vorhandenem Guard)
```

Der zuvor in einer ChatGPT-Minimal-Sandbox beobachtete Fehlschlag ist nach dem vorliegenden Befund ein Umgebungsproblem, kein nachgewiesener Code-Defekt. Es gibt daher keinen blockierenden Fix-Slice. Übrig bleibt eine kleine CI-Verifikations- und Dokumentationsaufgabe.

## 1. Verifikationsaufbau

| Aspekt | Wert |
| --- | --- |
| Umgebung | Ubuntu 24 (Container), kein X-Server, `DISPLAY` unset, `GraphicsEnvironment.isHeadless() == true` |
| JDK | OpenJDK 21 (`javac --release 8`, entsprechend Projektvorgabe) |
| fontconfig | vorhanden (`libfontconfig` + 299 Fonts) |
| Testausführung | Produktions- und Testquellen mit `javac` kompiliert; Ausführung über einen minimalen reflektiven JUnit4-Runner (`@Before`/`@Test`/`expected`/`Assume`) |

**Methodische Einschränkung:** Der Lauf ersetzt nicht den Gradle-/JUnit-Originallauf. Für die engere Frage „wirft die verwendete Swing-Nutzung headless?“ ist er aussagekräftig, da alle 55 Testmethoden mit der benötigten Testsemantik ausgeführt wurden. Weitere Lifecycle-Annotationen wie `@After`, `@BeforeClass` oder `@Rule` werden in den Exedra-Tests nicht verwendet.

## 2. Befunde im Einzelnen

### 2.1 Headless-Verhalten der verwendeten Swing-Konstrukte

| Konstrukt | Headless-Verhalten |
| --- | --- |
| `new JLabel(...)`, `new JPanel()`, `new JTabbedPane()`, `addTab(...)` | **OK** — wirft nicht |
| `KeyStroke.getKeyStroke(...)` | **OK** |
| `new JFrame(...)` (Basis von `ShellFrame`) | **wirft `HeadlessException`** |

### 2.2 Guard-Abdeckung der Testklassen

| Testklasse | Tests | Swing-Nutzung | Guard | Headless-Ergebnis |
| --- | ---: | --- | --- | --- |
| `CommandRegistryTest` | 17 | `KeyStroke` | — (nicht nötig) | ✅ pass |
| `ToolWindowRegistryTest` | 17 | `JTabbedPane`, `JLabel` | — (nicht nötig) | ✅ pass |
| `UiEventBusTest` | 9 | keine | — | ✅ pass; ein Test loggt absichtlich eine Exception auf stderr |
| `SettingsCategoryRegistryTest` | 5 | `JPanel` | — (nicht nötig) | ✅ pass |
| `ConfigurableToolbarExecutionTest` | 1 | `JToolBar`-Klick | vorhandener `Assume`-Guard | ⏭ skip |
| `ShellFrameRegisterToolWindowTest` | 1 | `ShellFrame extends JFrame` | vorhandener `Assume`-Guard | ⏭ skip |

Die Guards sitzen an den beiden displaypflichtigen Tests. Zusätzliche Guards an den leichtgewichtigen Tests wären kontraproduktiv, weil sie headless lauffähige Abdeckung entfernen würden.

## 3. Einordnung des früheren Sandbox-Fehlschlags

Der zuvor beobachtete Abbruch bei `proasteion:exedra:test` lässt sich mit diesem Code-Stand in einer normal ausgestatteten headless Linux-Umgebung nicht reproduzieren. Plausible Ursachen für eine Minimal-Sandbox sind:

1. **Fehlende AWT-Nativ-/Font-Pakete**, insbesondere `fontconfig`, `libfreetype6` und ein Basis-Fontpaket wie `fonts-dejavu-core`. Ohne funktionierende Font-Initialisierung können auch leichtgewichtige Swing-Komponenten fehlschlagen. Diagnose:

   ```bash
   ldconfig -p | grep -E 'fontconfig|freetype'
   fc-list | head
   ```

2. **Offline-Gradle-/JUnit-Konstellation** mit unvollständigem Cache oder abweichender JUnit-Konfiguration. Diagnose:

   ```bash
   ./gradlew --offline :proasteion:exedra:test --stacktrace
   ```

Vor Codeänderungen ist zu prüfen, ob der Fehler während der Testausführung oder bereits beim Aufbau der Laufzeitumgebung entsteht.

## 4. Verbleibende Aufgabe

### Goal

Bestätigen, dass die Exedra-Suite in der tatsächlichen CI-Umgebung grün läuft, und die Umgebungsanforderungen für Minimal-Sandboxes dokumentieren — ohne vorsorgliche Änderungen an Test- oder Produktionscode.

### Tasks

1. `:proasteion:exedra:test` auf dem Standard-Linux-CI-Runner ausführen. Erwartung: 48 bestanden, 2 übersprungen.
2. Übersprungene Tests im Gradle-Report sichtbar machen, damit displaypflichtige Tests nicht unbemerkt verschwinden.
3. `CHATGPT_BUILD.md` um die Paketanforderungen und Diagnosehinweise für headless Minimal-Sandboxes ergänzen.
4. Bei einem unerwarteten CI-Fehlschlag zuerst den Stacktrace gegen §3 abgleichen.

### Out of scope / do not

- keine zusätzlichen `Assume`-Guards an leichtgewichtigen Tests
- `java.awt.headless=false` nicht erzwingen
- kein Xvfb als Standardvoraussetzung nur für zwei bewusst displaypflichtige Tests
- keinen Produktionscode ändern, solange kein reproduzierbarer Codefehler vorliegt

### Acceptance criteria

- CI-Lauf ist grün und zeigt die erwarteten Skips sichtbar an.
- `CHATGPT_BUILD.md` dokumentiert die Minimal-Sandbox-Anforderungen.
- Test- oder Produktionscode bleibt unverändert, sofern kein reproduzierbarer Defekt gefunden wird.

## 5. Konsequenz für die Planung

Der frühere „Slice 0“ entfällt als Blocker. Die Arbeit an #10 — zuerst produktive Mediated-Komposition, danach das Run-Modell — kann unmittelbar beginnen. Die CI-Verifikation und die Branch-Aufräumarbeiten sind unabhängige Nebenarbeiten.
