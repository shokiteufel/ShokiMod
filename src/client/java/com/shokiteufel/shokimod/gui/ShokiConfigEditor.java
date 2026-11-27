package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.Update263Scope;

import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.observer.GetSetter;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Das Einstellungsfenster mit Reitern, die nicht in der Liste stehen.
 *
 * "Mob Visuals" ist vollstaendig vorhanden und aenderbar, taucht links aber nicht
 * auf. Sichtbar wird er erst, wenn im Suchfeld genau das Codewort steht - dann und
 * nur dann gelten alle seine Einstellungen als Suchtreffer. Beim Codewort zaehlt
 * Gross- und Kleinschreibung.
 *
 * "ikohS" (ShokiMods eigene Funktionen: Funny Map, The Mist, Stern-Mobs) ist ebenso verborgen und geht mit demselben Codewort auf.
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

    /** Das Suchwort der Protokolle: Der Reiter "Logs" - und mit ihm der Reiter der Fehlersuche, denn auch der schreibt ins Log */
    private static final String LOG_WORD = "log";

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
            hidden("mobVisuals", ShokiConfigEditor::isSecret),
            // ikohS (ShokiMods eigene Funktionen) ist ebenso verborgen und geht mit demselben Codewort auf
            hidden("ikohS", ShokiConfigEditor::isSecret),
            hidden("bugHunt", typed -> BUG_WORD.equalsIgnoreCase(typed.trim()) || LOG_WORD.equalsIgnoreCase(typed.trim())),
            hidden("logs", typed -> LOG_WORD.equalsIgnoreCase(typed.trim())));

    /**
     * Das Codewort-Ergebnis des letzten Suchtexts. Das Menue fragt je Bild an gut zwanzig Stellen, ob ein verborgener Reiter offen ist; jedes Mal SHA-256 zu rechnen
     * (mit 32 Formatierungen fuer den Hex-Text) war der teuerste Teil davon. Der Text aendert sich nur beim Tippen.
     */
    private record SecretCheck(String text, boolean match) {
    }

    private static volatile SecretCheck lastSecret = new SecretCheck(null, false);

    /** Steht im Suchfeld das Codewort? Das Ergebnis wird fuer denselben Text gemerkt */
    static boolean isSecret(String typed) {
        SecretCheck last = lastSecret;
        if (typed.equals(last.text())) return last.match();
        boolean match = SECRET_SHA256.equals(sha256(typed.trim()));
        lastSecret = new SecretCheck(typed, match);
        return match;
    }

    /** MoulConfigs Liste der Eintraege, die gerade zu sehen sind (privat). Null, wenn sie anders heisst: dann bleiben die Eintraege der Liste von 26.3 stehen */
    private static final Field VISIBLE_OPTIONS = findVisibleOptions();

    /** Stand des 26.3-Schalters beim letzten Aufbau der Eintraege: aendert er sich, werden sie neu aufgebaut */
    private boolean appliedUpdate263 = true;

    /** Ist der Konstruktor durch? Davor ruft die Oberklasse {@link #updateSearchResults(boolean)} schon, mitten im Aufbau ihrer Tabellen */
    private boolean constructed = false;

    /** Der Rohtext des Suchfelds. Null, wenn MoulConfig ihn nicht mehr so ablegt */
    private final GetSetter<String> searchText;

    /** So lange muss der Suchtext unveraendert stehen, bevor er auf die Platte geschrieben wird */
    private static final long SEARCH_SAVE_DELAY_MS = 600L;

    /** Der Reiter, der zuletzt gespeichert wurde - damit nur bei einem Wechsel geschrieben wird */
    private String lastSaved;

    /** Der Suchtext, der zuletzt gespeichert wurde, der, der gerade tippend wartet, und seit wann */
    private String savedSearch = "";
    private String waitingSearch = "";
    private long waitingSince = 0L;

    public ShokiConfigEditor(MoulConfigProcessor<ModConfig> processor) {
        super(processor);
        this.searchText = findSearchField();
        setSearchFunction((editor, search) -> {
            Hidden hidden = hiddenOf(editor.getOption().getCategory());
            if (hidden != null) return unlocked(hidden);
            return editor.fulfillsSearch(search);
        });
        this.lastSaved = getSelectedCategory();
        this.savedSearch = typed();
        this.waitingSearch = savedSearch;
        // Die Oberklasse hat die Eintraege schon einmal aufgebaut, aber vor diesem Konstruktor und womoeglich vor ihren Nachschlagetabellen: noch einmal
        this.constructed = true;
        this.appliedUpdate263 = FeatureGate.update263();
        hideUpdate263Options();
    }

    /**
     * Mit dem 26.3-Schalter aus: die einzelnen Eintraege von 26.3 (in Reitern, die auch Aelteres enthalten) nehmen wir aus der Liste der sichtbaren.
     *
     * MoulConfig fragt bei leerem Suchfeld gar nicht erst, welche Eintraege passen: Es nimmt alle. Darum setzt der Suchfilter hier nicht an, sondern das
     * Ergebnis: Aus MoulConfigs Menge der sichtbaren Eintraege fallen die heraus, die {@link Update263Scope} nennt. Ganze Reiter nimmt
     * {@link #getCurrentlyVisibleCategories()} heraus. Die Einstellungen selbst bleiben unberuehrt.
     */
    @Override
    public void updateSearchResults(boolean reset) {
        super.updateSearchResults(reset);
        hideUpdate263Options();
    }

    @SuppressWarnings("unchecked")
    private void hideUpdate263Options() {
        if (FeatureGate.update263() || VISIBLE_OPTIONS == null) return;
        try {
            Set<ProcessedOption> visible = (Set<ProcessedOption>) VISIBLE_OPTIONS.get(this);
            if (visible == null) return;
            visible.removeAll(Update263Scope.optionsToHide(getAllOptions(), this::getOptionFromField));
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Mitten im Aufbau der Oberklasse fehlen die Nachschlagetabellen noch; der Konstruktor ruft es danach noch einmal. Danach ist es ein echter Fehler
            if (constructed) ShokiMod.LOGGER.warn("Could not hide the 26.3 entries of the settings.", e);
        }
    }

    private static Field findVisibleOptions() {
        try {
            Field field = MoulConfigEditor.class.getDeclaredField("currentlyVisibleOptions");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ShokiMod.LOGGER.warn("MoulConfig's list of visible options not readable, the 26.3 entries stay in the menu.", e);
            return null;
        }
    }

    /**
     * Jedes Bild: Hat der 26.3-Schalter gewechselt, werden die Eintraege neu aufgebaut - sonst ginge das Ausblenden (und Zurueckholen) erst beim naechsten Tippen
     * in der Suche.
     */
    void refreshForUpdate263() {
        boolean on = FeatureGate.update263();
        if (on == appliedUpdate263) return;
        appliedUpdate263 = on;
        updateSearchResults();
    }

    /**
     * Das Fenster so aufschlagen, wie es beim letzten Mal war: erst der Suchtext, dann der Reiter.
     *
     * Die Reihenfolge zaehlt. Die verborgenen Reiter (Mob Visuals, ikohS, Bug hunt, Logs) sind nur zu sehen, solange das
     * Suchfeld ihr Wort enthaelt - der Text muss also im Feld stehen, bevor der Reiter gewaehlt wird. Ein
     * verborgener Reiter kommt nur zurueck, wenn er dann auch offen ist; sonst waere der gemerkte ein leerer.
     * Ist die Kennung unbekannt (der Reiter wurde umbenannt oder entfernt), bleibt es beim ersten.
     */
    public void restoreLast() {
        restoreSearch();
        restoreTab();
    }

    private void restoreSearch() {
        String text = ModConfig.INSTANCE.lastSearch;
        if (searchText != null && text != null && !text.isEmpty()) {
            try {
                // Erst der Rohtext, den auch die verborgenen Reiter lesen, dann die Suche, die die Liste filtert
                searchText.set(text);
                search(text);
            } catch (RuntimeException e) {
                ShokiMod.LOGGER.warn("Could not restore the config search '{}'", text, e);
            }
        }
        savedSearch = typed();
        waitingSearch = savedSearch;
    }

    private void restoreTab() {
        String id = ModConfig.INSTANCE.lastTab;
        if (id != null && !id.isBlank()) {
            try {
                ProcessedCategory category = getAllCategories().get(id);
                // Ein Reiter von 26.3 kommt nicht zurueck, solange der Schalter aus ist (er steht dann nicht in der Liste)
                if (category != null && !Update263Scope.categoryHidden(category.getIdentifier(), category.getParentCategoryId())) {
                    Hidden hidden = hiddenOf(category);
                    if (hidden == null || unlocked(hidden)) setSelectedCategory(category);
                }
            } catch (RuntimeException e) {
                ShokiMod.LOGGER.warn("Could not reopen config tab '{}'", id, e);
            }
        }
        lastSaved = getSelectedCategory();
    }

    /**
     * Jedes Bild: Hat sich der Reiter geaendert, wird er gemerkt.
     *
     * Gemerkt wird beim Wechseln und nicht erst beim Schliessen: Das Fenster hat mehrere Wege zu (Esc, ein Klick
     * daneben, ModMenu, der HUD-Editor), und ein Weg, der den Merker uebergeht, wuerde den Reiter still vergessen.
     * Ein Vergleich je Bild kostet nichts; geschrieben wird nur beim Wechsel.
     */
    @Override
    public void render() {
        refreshForUpdate263();
        super.render();
        rememberTab();
        rememberSearch();
    }

    void rememberTab() {
        String now = getSelectedCategory();
        if (now == null || now.equals(lastSaved)) return;
        lastSaved = now;
        // Auch ein verborgener Reiter wird gemerkt: Er ist nur offen, solange das Suchfeld sein Wort enthaelt, und
        // das wird mitgemerkt. Fehlt das Wort beim naechsten Mal, kommt er nicht zurueck (siehe restoreTab)
        ModConfig.INSTANCE.lastTab = now;
        ModConfig.INSTANCE.lastSearch = typed();
        savedSearch = typed();
        waitingSearch = savedSearch;
        ModConfig.INSTANCE.saveNow();
    }

    /**
     * Jedes Bild: Hat sich der Suchtext geaendert, wird er gemerkt.
     *
     * Im Speicher gleich - dann findet ihn ein Fenster, das sofort wieder aufgemacht wird -, auf die Platte erst,
     * wenn er eine Weile unveraendert steht. Sonst wuerde jeder Buchstabe die ganze Einstellungsdatei neu schreiben.
     * Ein Wechsel des Reiters schreibt ihn ohnehin mit (rememberTab).
     */
    void rememberSearch() {
        if (searchText == null) return;
        String now = typed();
        if (now.equals(savedSearch)) {
            // Auch ein Tippen, das wieder auf den gespeicherten Text zurueckfuehrt, zaehlt
            waitingSearch = now;
            ModConfig.INSTANCE.lastSearch = now;
            return;
        }
        long time = System.currentTimeMillis();
        if (!now.equals(waitingSearch)) {
            waitingSearch = now;
            waitingSince = time;
            ModConfig.INSTANCE.lastSearch = now;
        }
        if (time - waitingSince >= SEARCH_SAVE_DELAY_MS) {
            savedSearch = now;
            ModConfig.INSTANCE.saveNow();
        }
    }

    /** Der Rohtext des Suchfelds, nie null */
    private String typed() {
        if (searchText == null) return "";
        String typed = searchText.get();
        return typed == null ? "" : typed;
    }

    @Override
    public LinkedHashMap<String, ProcessedCategory> getCurrentlyVisibleCategories() {
        LinkedHashMap<String, ProcessedCategory> visible = super.getCurrentlyVisibleCategories();
        visible.entrySet().removeIf(entry -> {
            // Mit dem 26.3-Schalter aus: die Reiter von 26.3 (und ihre Unter-Reiter) fehlen, das Menue sieht aus wie vor 2.4.10
            ProcessedCategory category = entry.getValue();
            if (Update263Scope.categoryHidden(category.getIdentifier(), category.getParentCategoryId())) return true;
            Hidden hidden = hiddenOf(category);
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
                if (Update263Scope.categoryHidden(kategorie.getIdentifier(), kategorie.getParentCategoryId())) continue;
                String name = kategorie.getDisplayName().getText();
                if (name != null && name.trim().equalsIgnoreCase(text.trim())) {
                    setSelectedCategory(kategorie);
                    return;
                }
            }
            // Kein genauer Treffer: dann wenigstens die erste der gefilterten Liste (ein ausgeblendeter Reiter von 26.3 zaehlt nicht)
            for (ProcessedCategory kategorie : getCurrentlySearchedCategories().values()) {
                if (Update263Scope.categoryHidden(kategorie.getIdentifier(), kategorie.getParentCategoryId())) continue;
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
