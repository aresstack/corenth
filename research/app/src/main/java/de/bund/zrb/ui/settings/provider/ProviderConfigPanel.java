package de.bund.zrb.ui.settings.provider;

import de.bund.zrb.model.AiProvider;

import javax.swing.*;
import java.awt.*;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Wiederverwendbares Provider-Konfigurationspanel.
 *
 * <p>Listet im Dropdown exakt die gleichen Provider auf wie der allgemeine AI-Tab
 * ({@code AiSettingsPanel}). Pro Provider wird ein Karten-Panel erzeugt, das
 * ausschließlich die für die übergebenen {@link Facet}s relevanten Eingabefelder
 * rendert — inklusive aller für diese Facets notwendigen Credentials/Auth-Felder
 * dieses Providers. Felder zu anderen Facets erscheinen nicht.</p>
 *
 * <p>Lade- und Speicherlogik erfolgt über eine flache {@code Map<String,String>}.
 * Die Schlüssel entsprechen 1:1 dem Schema, das auch der allgemeine AI-Tab nutzt
 * (z.&nbsp;B. {@code ollama.url}, {@code cloud.apikey}, {@code cloud.model.embeddings}).</p>
 *
 * <p>Implementiert über {@link ProviderDef} + {@link ProviderCardRenderer} — die
 * Provider-spezifische Logik steckt komplett in den Datendefinitionen unter
 * {@link ProviderDefinitions}.</p>
 */
public class ProviderConfigPanel extends JPanel {

    private final Set<Facet> facets;
    private final BooleanSupplier useProxySupplier;
    private final JComboBox<AiProvider> providerCombo;
    private final JPanel cardsPanel;
    private final CardLayout cardLayout;
    private final Map<AiProvider, ProviderCardRenderer.RenderedCard> cards =
            new LinkedHashMap<AiProvider, ProviderCardRenderer.RenderedCard>();

    public ProviderConfigPanel(Set<Facet> facets) {
        this(facets, null);
    }

    /**
     * Wie {@link #ProviderConfigPanel(Set)}, akzeptiert aber zusätzlich einen
     * {@code useProxySupplier}, der beim Klick auf die 🔄- und 🧪-Buttons der
     * Provider-Karten ausgewertet wird. Liefert er {@code false}, werden die
     * HTTP-Calls direkt (ohne Proxy) ausgeführt. Wird typischerweise von einer
     * per-Tab-Checkbox gespeist.
     */
    public ProviderConfigPanel(Set<Facet> facets, BooleanSupplier useProxySupplier) {
        super(new BorderLayout(0, 6));
        this.facets = EnumSet.copyOf(facets);
        this.useProxySupplier = useProxySupplier;

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.add(new JLabel("Provider:"));
        providerCombo = new JComboBox<AiProvider>();
        providerCombo.addItem(AiProvider.DISABLED);
        providerCombo.addItem(AiProvider.OLLAMA);
        providerCombo.addItem(AiProvider.CLOUD);
        providerCombo.addItem(AiProvider.PRIVATE_CLOUD);
        providerCombo.addItem(AiProvider.LOCAL_AI);
        providerCombo.addItem(AiProvider.LLAMA_CPP_SERVER);
        providerCombo.addItem(AiProvider.ONNX_RUNTIME);
        header.add(providerCombo);
        add(header, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);

        // DISABLED: schlichte Info-Card ohne Bindings.
        JPanel disabled = new JPanel(new BorderLayout());
        JLabel info = new JLabel("Provider ist deaktiviert.");
        info.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        disabled.add(info, BorderLayout.NORTH);
        cardsPanel.add(disabled, AiProvider.DISABLED.name());

        registerCard(ProviderDefinitions.ollama());
        registerCard(ProviderDefinitions.cloud());
        registerCard(ProviderDefinitions.privateCloud());
        registerCard(ProviderDefinitions.localAi());
        registerCard(ProviderDefinitions.llamaCpp());
        registerCard(ProviderDefinitions.onnx());

        add(cardsPanel, BorderLayout.CENTER);

        providerCombo.addActionListener(e -> {
            AiProvider sel = (AiProvider) providerCombo.getSelectedItem();
            if (sel != null) cardLayout.show(cardsPanel, sel.name());
        });
    }

    private void registerCard(ProviderDef def) {
        ProviderCardRenderer.RenderedCard card = ProviderCardRenderer.render(def, facets, useProxySupplier);
        cards.put(def.provider, card);
        cardsPanel.add(card.getPanel(), def.provider.name());
    }

    /** Lädt Provider-Auswahl und alle facet-relevanten Felder aus der Map. */
    public void loadFromConfig(Map<String, String> config) {
        if (config == null) config = new HashMap<String, String>();
        AiProvider provider;
        try {
            provider = AiProvider.valueOf(config.getOrDefault("provider", "OLLAMA"));
        } catch (IllegalArgumentException e) {
            provider = AiProvider.OLLAMA;
        }
        providerCombo.setSelectedItem(provider);
        cardLayout.show(cardsPanel, provider.name());
        for (ProviderCardRenderer.RenderedCard c : cards.values()) c.load(config);
    }

    /**
     * Schreibt Provider-Auswahl und alle facet-relevanten Felder in eine NEUE Map.
     * Es werden nur Schlüssel geschrieben, die tatsächlich zu Feldern dieses Panels gehören.
     * Vorhandene Werte für andere Facets/Felder bleiben durch den Aufrufer unverändert.
     */
    public Map<String, String> saveToConfig() {
        Map<String, String> out = new HashMap<String, String>();
        AiProvider provider = getSelectedProvider();
        out.put("provider", provider != null ? provider.name() : AiProvider.OLLAMA.name());
        for (ProviderCardRenderer.RenderedCard c : cards.values()) c.save(out);
        return out;
    }

    public AiProvider getSelectedProvider() {
        return (AiProvider) providerCombo.getSelectedItem();
    }
}
