package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * Das Einstellungsfenster mit Reitern, die nicht in der Liste stehen.
 *
 * "Mob Visuals" ist vollstaendig vorhanden und aenderbar, taucht links aber nicht
 * auf. Sichtbar wird er erst, wenn im Suchfeld genau das Codewort steht - dann und
 * nur dann gelten alle seine Einstellungen als Suchtreffer. Beim Codewort zaehlt
 * Gross- und Kleinschreibung.
 *
 * "Bug hunt" (die Werkzeuge zum Fehlersuchen) ist ebenso verborgen und kommt bei der
 * Suche nach "bug" - ohne Ruecksicht auf Gross und Klein, denn es ist kein Geheimnis,
 * nur nichts, was man im Alltag braucht.
 *
 * MoulConfig sucht selbst in Kleinbuchstaben, deshalb wird der Rohtext des Suchfelds
 * gelesen und nicht der Suchbegriff, den die Suchfunktion bekommt.
 */
public class ShokiConfigEditor extends MoulConfigEditor<ModConfig> {

    /**
     * Der SHA-256 des Codeworts, nicht das Codewort selbst.
     *
     * Wer den Quelltext oder die Jar liest, findet hier nur den Hash - und aus dem
     * laesst sich das Wort nicht zurueckrechnen. Geprueft wird, indem die Eingabe
     * gehasht und mit diesem Wert verglichen wird.
     */
    private static final String SECRET_SHA256 = "a1fff415837736c26b3f69c1c49ef08667ebc0226cbcb3e24295b986b6d12916";

    /** Das Suchwort der Fehlersuche */
    private static final String BUG_WORD = "bug";

    /**
     * Ein verborgener Reiter: sein Feld in {@link ModConfig} und die Frage, ob das Suchfeld
     * ihn gerade freigibt.
     *
     * MoulConfig fuehrt Kategorien unter {@code Field.toString()}. Derselbe Aufruf
     * hier liefert denselben Schluessel - und wirft, sobald das Feld umbenannt wird.
     * Dann bleibt der Reiter verborgen statt still wieder aufzutauchen.
     */
    private record Hidden(String id, Predicate<String> unlockedBy) {
    }

    private static final List<Hidden> HIDDEN = List.of(
            hidden("mobVisuals", typed -> SECRET_SHA256.equals(sha256(typed.trim()))),
            hidden("bugHunt", typed -> BUG_WORD.equalsIgnoreCase(typed.trim())));

    /** Der Rohtext des Suchfelds. Null, wenn MoulConfig ihn nicht mehr so ablegt */
    private final GetSetter<String> searchText;

    public ShokiConfigEditor(MoulConfigProcessor<ModConfig> processor) {
        super(processor);
        this.searchText = findSearchField();
        setSearchFunction((editor, search) -> {
            Hidden hidden = hiddenOf(editor.getOption().getCategory());
            if (hidden != null) return unlocked(hidden);
            return editor.fulfillsSearch(search);
        });
    }

    @Override
    public LinkedHashMap<String, ProcessedCategory> getCurrentlyVisibleCategories() {
        LinkedHashMap<String, ProcessedCategory> visible = super.getCurrentlyVisibleCategories();
        visible.entrySet().removeIf(entry -> {
            Hidden hidden = hiddenOf(entry.getValue());
            return hidden != null && !unlocked(hidden);
        });
        return visible;
    }

    /** Zu welchem verborgenen Reiter gehoert das? Der Reiter selbst und jeder Unterreiter darunter */
    private static Hidden hiddenOf(ProcessedCategory category) {
        if (category == null) return null;
        for (Hidden hidden : HIDDEN) {
            if (hidden.id() == null) continue;
            if (hidden.id().equals(category.getIdentifier())
                    || hidden.id().equals(category.getParentCategoryId())) {
                return hidden;
            }
        }
        return null;
    }

    private boolean unlocked(Hidden hidden) {
        if (searchText == null) return false;
        String typed = searchText.get();
        if (typed == null) return false;
        return hidden.unlockedBy().test(typed);
    }

    private static String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static Hidden hidden(String field, Predicate<String> unlockedBy) {
        try {
            return new Hidden(ModConfig.class.getField(field).toString(), unlockedBy);
        } catch (NoSuchFieldException e) {
            ShokiMod.LOGGER.warn("Hidden config category '{}' no longer exists.", field);
            return new Hidden(null, typed -> false);
        }
    }

    /**
     * Gleich die richtige Ecke aufschlagen.
     *
     * Zwei Schritte, denn eines allein genuegt nicht: Die Suche blendet aus, was nicht
     * passt, laesst aber die zuletzt gewaehlte Kategorie stehen - und die ist beim
     * Oeffnen die erste. Deshalb wird zusaetzlich die Kategorie ausgewaehlt, deren
     * Anzeigename gesucht wurde.
     */
    public void preset(String text) {
        if (text == null || text.isBlank()) return;
        try {
            search(text);
            for (ProcessedCategory kategorie : getCurrentlySearchedCategories().values()) {
                String name = kategorie.getDisplayName().getText();
                if (name != null && name.trim().equalsIgnoreCase(text.trim())) {
                    setSelectedCategory(kategorie);
                    return;
                }
            }
            // Kein genauer Treffer: dann wenigstens die erste der gefilterten Liste
            for (ProcessedCategory kategorie : getCurrentlySearchedCategories().values()) {
                setSelectedCategory(kategorie);
                return;
            }
        } catch (RuntimeException e) {
            // Die Suche ist Beiwerk - ein Fenster ohne Vorauswahl ist besser als keines
            ShokiMod.LOGGER.warn("Could not preselect config category '{}'", text, e);
        }
    }

    @SuppressWarnings("unchecked")
    private GetSetter<String> findSearchField() {
        try {
            Field field = MoulConfigEditor.class.getDeclaredField("searchFieldContent");
            field.setAccessible(true);
            return (GetSetter<String>) field.get(this);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Ohne den Rohtext laesst sich das Codewort nicht pruefen. Dann bleibt der
            // Reiter verborgen - lieber unerreichbar als versehentlich sichtbar
            ShokiMod.LOGGER.warn("MoulConfig search field not readable, hidden category stays hidden.", e);
            return null;
        }
    }
}
