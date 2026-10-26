package com.shokiteufel.shokimod.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die Pets, die das Pet-Menue gezeigt hat - mit ihrer genauen Erfahrung.
 *
 * Jedes Pet-Item im Menue traegt "petInfo" mit Kennung (uniqueId) und Erfahrung auf die Nachkommastelle genau. Die Tab-Liste nennt die
 * Erfahrung des aktiven Pets ebenfalls (ueber der Hoechststufe "+171,772.5 XP", darunter "33,915/179.7k XP"): Rechnet man sie auf die
 * gesamte Erfahrung um, passt sie auf das Pet im Menue auf die Stelle genau - im Protokoll vom 26.10.2026: Tab 25.353.230 + 171.772,5 =
 * 25.525.002,5, Menue 25.525.002,46. Damit laesst sich auch von mehreren gleichnamigen Pets (drei Ammonites, 13 Skeleton Horses) sagen,
 * welches draussen ist, wenn weder die Tab-Liste (zeigt kein Item) noch eine Chatzeile ohne Mauszeiger-Text es verraten.
 *
 * Gemerkt wird je Pet: sein Schluessel (Name, Skin, Item - siehe PetIdentity), die Erfahrung beim letzten Blick ins Menue und die Seltenheit.
 * Ein Pet sammelt nur Erfahrung: Das aktive hat inzwischen mehr als im Menue gestanden, kein Pet weniger. Gesucht wird deshalb das Pet
 * derselben Art, dessen gemerkte Erfahrung am knappsten unter der gelesenen liegt.
 *
 * Die Datei (pet-book.json im Ordner shokimod der Einstellungen) liegt in {@link Disk}, damit die Rechnung ohne Spiel geprueft werden kann.
 */
public final class PetBook {

    /** So viel darf ein Pet seit dem letzten Blick ins Menue zugelegt haben, damit es noch als dasselbe gilt: mindestens 10 Mio., sonst ein Fuenftel */
    private static final double MIN_GAIN = 10_000_000d;
    private static final double GAIN_SHARE = 0.20d;
    /** Rundung: Die Tab-Liste nennt eine Nachkommastelle, die Rechnung kommt auf zwei */
    private static final double TOLERANCE = 2.0d;

    /** Ein Pet, wie das Menue es zuletzt zeigte */
    public record Entry(String uid, String key, double exp, String tier) {
    }

    /** Ein Treffer und wie viel Erfahrung das Pet seit dem gemerkten Stand dazubekam */
    public record Match(Entry entry, double gain) {
    }

    private static final Map<String, Entry> pets = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile boolean dirty = false;
    private static volatile boolean persist = true;
    private static volatile long lastSave = 0L;

    private PetBook() {
    }

    /** Fuer die Pruefung ohne Spiel: nichts lesen, nichts schreiben */
    public static void setPersist(boolean on) {
        persist = on;
        if (!on) loaded = true;
    }

    public static void clearForTest() {
        pets.clear();
        dirty = false;
    }

    public static int size() {
        load();
        return pets.size();
    }

    /**
     * Ein Pet aus dem Menue festhalten.
     *
     * @param uid  die Kennung aus petInfo (uniqueId, sonst uuid)
     * @param key  der Schluessel (Name, Skin, Item)
     * @param exp  die genaue Erfahrung
     * @param tier die Seltenheit wie in petInfo (LEGENDARY ...), oder leer
     */
    public static void observe(String uid, String key, double exp, String tier) {
        if (uid == null || uid.isBlank() || key == null || key.isBlank() || exp < 0) return;
        load();
        Entry neu = new Entry(uid, PetIdentity.canon(key), exp, tier == null ? "" : tier.toUpperCase(Locale.ROOT));
        Entry vorher = pets.put(uid, neu);
        if (vorher == null || !vorher.key().equals(neu.key()) || !vorher.tier().equals(neu.tier())
                || Math.abs(vorher.exp() - neu.exp()) > 0.5d) {
            dirty = true;
            save(vorher == null || !vorher.key().equals(neu.key()));
        }
    }

    /**
     * Welches Pet dieser Art hat diese gesamte Erfahrung?
     *
     * @param rarity die Seltenheit aus der Tab-Liste (Farbe des Namens), oder leer - dann zaehlt sie nicht
     * @return das Pet, dessen gemerkte Erfahrung am knappsten unter {@code totalExp} liegt, oder null
     */
    public static Match match(String name, boolean skin, String rarity, double totalExp) {
        if (name == null || name.isBlank() || totalExp <= 0) return null;
        load();
        String base = PetIdentity.baseKey(name, skin);
        double bound = Math.max(MIN_GAIN, totalExp * GAIN_SHARE);
        Entry best = null;
        double bestGain = Double.MAX_VALUE;
        for (Entry e : pets.values()) {
            if (!PetIdentity.baseKey(PetIdentity.nameOf(e.key()), PetIdentity.skinOf(e.key())).equals(base)) continue;
            if (rarity != null && !rarity.isBlank() && !e.tier().isBlank() && !e.tier().equalsIgnoreCase(rarity)) continue;
            double gain = totalExp - e.exp();
            if (gain < -TOLERANCE || gain > bound) continue;
            if (gain < bestGain) {
                bestGain = gain;
                best = e;
            }
        }
        return best == null ? null : new Match(best, Math.max(0d, bestGain));
    }

    // ---- Datei ----

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (persist) Disk.load(pets);
    }

    /**
     * Schreiben: sofort bei einem neuen Pet, sonst hoechstens alle dreissig Sekunden - das Menue wird zweimal je Sekunde gelesen,
     * und die Erfahrung des aktiven Pets aendert sich mit jedem Blick.
     */
    private static synchronized void save(boolean now) {
        if (!persist || !dirty) return;
        long time = System.currentTimeMillis();
        if (!now && time - lastSave < 30_000L) return;
        lastSave = time;
        dirty = false;
        Disk.save(pets);
    }

    /** Die Datei - eigene Klasse, damit Gson und der Fabric-Loader erst geladen werden, wenn wirklich gelesen oder geschrieben wird */
    static final class Disk {
        private static final String FILE = "pet-book.json";

        private Disk() {
        }

        private static java.nio.file.Path path() {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("shokimod").resolve(FILE);
        }

        static void load(Map<String, Entry> into) {
            try {
                java.nio.file.Path file = path();
                if (!java.nio.file.Files.isRegularFile(file)) return;
                com.google.gson.JsonObject root = new com.google.gson.Gson().fromJson(
                        java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
                if (root == null) return;
                for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    com.google.gson.JsonObject o = entry.getValue().getAsJsonObject();
                    if (!o.has("key") || !o.has("exp")) continue;
                    into.put(entry.getKey(), new Entry(entry.getKey(), o.get("key").getAsString(), o.get("exp").getAsDouble(),
                            o.has("tier") ? o.get("tier").getAsString() : ""));
                }
            } catch (Exception | LinkageError e) {
                warn("Could not read remembered pets: " + e);
            }
        }

        static void save(Map<String, Entry> from) {
            try {
                com.google.gson.JsonObject root = new com.google.gson.JsonObject();
                for (Entry e : from.values()) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("key", e.key());
                    o.addProperty("exp", e.exp());
                    if (!e.tier().isBlank()) o.addProperty("tier", e.tier());
                    root.add(e.uid(), o);
                }
                java.nio.file.Path file = path();
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, new com.google.gson.Gson().toJson(root), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception | LinkageError e) {
                warn("Could not save pets: " + e);
            }
        }

        private static void warn(String text) {
            try {
                com.shokiteufel.shokimod.ShokiMod.LOGGER.warn("[ShokiMod] {}", text);
            } catch (LinkageError ignored) {
                // ohne Spiel (Pruefung) gibt es den Logger nicht
            }
        }
    }
}
