package com.shokiteufel.shokimod.equipment;

import com.google.gson.JsonElement;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Gedaechtnis der Equipment-Plaetze: je Profil und je Satz (gewoehnlich, Rift, Safari) vier Gegenstaende - im Speicher fertig gelesen, in der Datei als JSON.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyblockInventoryScreen}). Von dort stammt die Regel: Das Equipment ist ein Zwischenspeicher, der beim Oeffnen
 * von /equipment gefuellt wird, je Profil getrennt ist und den Neustart ueberlebt. Die Umsetzung ist eigene Arbeit: Java, eine Datei fuer alle Profile
 * ({@link EquipmentFile}), die ein Profil erst dann in Gegenstaende verwandelt, wenn es gebraucht wird (dann ist die Verbindung und damit die Registrierung da);
 * bis dahin bleibt sein JSON unberuehrt und kommt beim Speichern unveraendert zurueck.
 *
 * Alles laeuft im Faden des Spiels. {@link #get} kommt in jedem Bild und legt nichts an. Geschrieben wird nicht bei jeder Aenderung, sondern wenn der Aufrufer
 * {@link #saveIfDirty} ruft (beim Schliessen des Fensters und beim Beenden): Aendert sich die Beschreibung eines Stuecks bei jedem Blick, wuerde sonst alle halbe
 * Sekunde geschrieben.
 */
public final class EquipmentStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShokiModEquipment");
    /** Das Profil, solange Hypixel die Kennung noch nicht genannt hat */
    public static final String UNKNOWN_PROFILE = "unknown";
    private static final int SETS = EquipmentSet.values().length;

    private final Path file;
    /** Was in der Datei steht (und seit dem Speichern geaendert wurde), je Profil in der Reihenfolge ihrer letzten Aenderung */
    private final Map<String, Map<EquipmentSet, List<JsonElement>>> raw;
    /** Die Profile, die schon in Gegenstaende verwandelt sind: je Satz ein Feld von vier */
    private final Map<String, ItemStack[][]> decoded = new HashMap<>();
    private boolean dirty = false;
    private boolean warned = false;

    /** Das Gedaechtnis aus dieser Datei; gibt es sie nicht oder ist sie unlesbar, faengt es leer an */
    public EquipmentStore(Path file) {
        this.file = file;
        this.raw = EquipmentFile.parse(readText(file));
    }

    private String readText(Path path) {
        try {
            return path != null && Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException | RuntimeException e) {
            warnOnce("could not read " + path + ": " + e);
            return "";
        }
    }

    /** Die Kennung, unter der ein Profil in der Datei steht: ohne Namen "unknown" (die Kennungen kommen klein geschrieben aus {@code PetState.profileId}) */
    public static String key(String profile) {
        return profile == null || profile.isBlank() ? UNKNOWN_PROFILE : profile;
    }

    private static ItemStack[][] blankStacks() {
        ItemStack[][] sets = new ItemStack[SETS][EquipmentRules.SLOTS];
        for (ItemStack[] set : sets) java.util.Arrays.fill(set, ItemStack.EMPTY);
        return sets;
    }

    /** Die Antwort ohne Registrierung: leer, fuer alle dasselbe Feld (es wird nie veraendert: {@link #put} schreibt nur mit Registrierung) */
    private static final ItemStack[][] BLANK = blankStacks();

    /**
     * Das Profil in Gegenstaende verwandeln (einmal) - oder, ohne Registrierung, vier mal vier leere Plaetze, ohne das Profil als fertig zu merken.
     * Der Rueckgabewert ist nie null.
     */
    private ItemStack[][] stacksOf(String profile, HolderLookup.Provider lookup) {
        ItemStack[][] known = decoded.get(profile);
        if (known != null) return known;
        if (lookup == null) return BLANK;
        ItemStack[][] sets = blankStacks();
        Map<EquipmentSet, List<JsonElement>> saved = raw.get(profile);
        if (saved != null) {
            for (Map.Entry<EquipmentSet, List<JsonElement>> entry : saved.entrySet()) {
                List<JsonElement> items = entry.getValue();
                for (int i = 0; i < EquipmentRules.SLOTS && i < items.size(); i++) {
                    sets[entry.getKey().ordinal()][i] = EquipmentCodec.decode(items.get(i), lookup);
                }
            }
        }
        decoded.put(profile, sets);
        return sets;
    }

    /**
     * Die vier Gegenstaende eines Satzes - nie null, und kein Platz ist null (ein leerer ist {@link ItemStack#EMPTY}). Das Feld gehoert dem Gedaechtnis:
     * Wer es liest, aendert es nicht. Ohne Registrierung (noch keine Verbindung) vier leere.
     */
    public ItemStack[] get(String profile, EquipmentSet set, HolderLookup.Provider lookup) {
        return stacksOf(key(profile), lookup)[set.ordinal()];
    }

    /**
     * Die vier Gegenstaende eines Satzes festhalten.
     *
     * @return ob sich etwas geaendert hat (ein Platz hat einen anderen Gegenstand oder andere Komponenten als vorher); nur dann wird das Profil zum Speichern vorgemerkt
     */
    public boolean put(String profile, EquipmentSet set, ItemStack[] items, HolderLookup.Provider lookup) {
        if (items == null || items.length < EquipmentRules.SLOTS || lookup == null) return false;
        String key = key(profile);
        ItemStack[] now = stacksOf(key, lookup)[set.ordinal()];
        boolean changed = false;
        for (int i = 0; i < EquipmentRules.SLOTS; i++) {
            ItemStack next = items[i] == null ? ItemStack.EMPTY : items[i];
            if (!ItemStack.isSameItemSameComponents(now[i], next)) changed = true;
        }
        if (!changed) return false;
        for (int i = 0; i < EquipmentRules.SLOTS; i++) {
            // Eine Kopie: Der Gegenstand im Fenster gehoert dem Fenster und aendert sich mit ihm
            now[i] = items[i] == null || items[i].isEmpty() ? ItemStack.EMPTY : items[i].copy();
        }
        remember(key, set, now, lookup);
        return true;
    }

    /** Alle vier Plaetze eines Satzes leeren (der Satz wurde im Kleiderschrank abgenommen); gibt zurueck, ob vorher etwas darin stand */
    public boolean clear(String profile, EquipmentSet set, HolderLookup.Provider lookup) {
        ItemStack[] none = new ItemStack[EquipmentRules.SLOTS];
        java.util.Arrays.fill(none, ItemStack.EMPTY);
        return put(profile, set, none, lookup);
    }

    /** Den Satz ins JSON schreiben und das Profil ans Ende der Reihe stellen (das zuletzt benutzte bleibt, wenn die Datei zu viele Profile hat) */
    private void remember(String key, EquipmentSet set, ItemStack[] stacks, HolderLookup.Provider lookup) {
        Map<EquipmentSet, List<JsonElement>> sets = raw.remove(key);
        if (sets == null) sets = new EnumMap<>(EquipmentSet.class);
        List<JsonElement> items = new ArrayList<>(EquipmentRules.SLOTS);
        for (int i = 0; i < EquipmentRules.SLOTS; i++) items.add(EquipmentCodec.encode(stacks[i], lookup));
        sets.put(set, items);
        raw.put(key, sets);
        dirty = true;
    }

    /** Ist etwas zu schreiben? */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Die Datei schreiben, wenn sich etwas geaendert hat - nie direkt hinein, sondern erst in eine Nebendatei und dann in einem Zug an die Stelle der alten.
     * Schlaegt es fehl, bleibt es vorgemerkt und wird beim naechsten Mal wieder versucht.
     */
    public void saveIfDirty() {
        if (!dirty || file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, EquipmentFile.write(raw), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException | RuntimeException e) {
            warnOnce("could not write " + file + ": " + e);
        }
    }

    private void warnOnce(String text) {
        if (warned) return;
        warned = true;
        LOGGER.warn("[Equipment] {}", text);
    }
}
