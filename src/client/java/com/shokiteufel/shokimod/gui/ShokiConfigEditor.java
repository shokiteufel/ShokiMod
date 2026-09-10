package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;

/**
 * Das Einstellungsfenster mit einem Reiter, der nicht in der Liste steht.
 *
 * "Mob Visuals" ist vollstaendig vorhanden und aenderbar, taucht links aber nicht
 * auf. Sichtbar wird er erst, wenn im Suchfeld genau das Codewort steht - dann und
 * nur dann gelten alle seine Einstellungen als Suchtreffer.
 *
 * Gross- und Kleinschreibung zaehlt. MoulConfig sucht selbst in Kleinbuchstaben,
 * deshalb wird der Rohtext des Suchfelds gelesen und nicht der Suchbegriff, den die
 * Suchfunktion bekommt.
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

    /** Das Feld in {@link ModConfig}, dessen Reiter verborgen bleibt */
    private static final String HIDDEN_FIELD = "mobVisuals";

    /**
     * MoulConfig fuehrt Kategorien unter {@code Field.toString()}. Derselbe Aufruf
     * hier liefert denselben Schluessel - und wirft, sobald das Feld umbenannt wird.
     * Dann bleibt der Reiter verborgen statt still wieder aufzutauchen.
     */
    private static final String HIDDEN_ID = hiddenId();

    /** Der Rohtext des Suchfelds. Null, wenn MoulConfig ihn nicht mehr so ablegt */
    private final GetSetter<String> searchText;

    public ShokiConfigEditor(MoulConfigProcessor<ModConfig> processor) {
        super(processor);
        this.searchText = findSearchField();
        setSearchFunction((editor, search) -> {
            if (isHidden(editor.getOption().getCategory())) return secretTyped();
            return editor.fulfillsSearch(search);
        });
    }

    @Override
    public LinkedHashMap<String, ProcessedCategory> getCurrentlyVisibleCategories() {
        LinkedHashMap<String, ProcessedCategory> visible = super.getCurrentlyVisibleCategories();
        if (!secretTyped()) visible.entrySet().removeIf(entry -> isHidden(entry.getValue()));
        return visible;
    }

    /** Der Reiter selbst und jeder Unterreiter darunter - beide haengen am selben Codewort */
    private static boolean isHidden(ProcessedCategory category) {
        if (HIDDEN_ID == null || category == null) return false;
        return HIDDEN_ID.equals(category.getIdentifier()) || HIDDEN_ID.equals(category.getParentCategoryId());
    }

    private boolean secretTyped() {
        if (searchText == null) return false;
        String typed = searchText.get();
        if (typed == null) return false;
        return SECRET_SHA256.equals(sha256(typed.trim()));
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

    private static String hiddenId() {
        try {
            return ModConfig.class.getField(HIDDEN_FIELD).toString();
        } catch (NoSuchFieldException e) {
            ShokiMod.LOGGER.warn("Hidden config category '{}' no longer exists.", HIDDEN_FIELD);
            return null;
        }
    }

    /**
     * Das Suchfeld vorbelegen, damit gleich die richtige Ecke dasteht.
     *
     * MoulConfig filtert die Kategorien nach dem, was im Suchfeld steht - wer aus dem
     * Kasten-Editor heraus "Einstellungen" waehlt, landet damit direkt bei seiner
     * Funktion statt auf der ersten Seite.
     */
    public void preset(String text) {
        if (text == null || text.isBlank()) return;
        GetSetter<String> feld = findSearchField();
        if (feld != null) feld.set(text);
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
