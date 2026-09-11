package com.shokiteufel.shokimod.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.ShokiMod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Alle Shard-Fusionen, mit Preisen vom Bazaar.
 *
 * <p><b>Warum hier gerechnet wird und nicht auf GitHub:</b> Frueher kam eine fertige
 * Bestenliste mit vierhundert Zeilen. Das war bequem und falsch - wer nach einem
 * bestimmten Shard suchte, fand nichts, weil dessen Fusionen nicht unter den besten
 * vierhundert waren. Abyssal Miner etwa kommt in 1.370 Kombinationen vor und in keiner
 * einzigen davon. Eine Liste, die nur die Spitze kennt, kann die Frage "was mache ich
 * mit diesem Shard" nicht beantworten.
 *
 * <p>Geholt werden deshalb zwei Dateien: die Rezepte einmal (anderthalb Megabyte, sie
 * aendern sich nur bei einem Hypixel-Update) und die Preise oft (dreissig Kilobyte,
 * alle zehn Minuten). Gerechnet wird beim Anzeigen. Das klingt nach viel und ist es
 * nicht: Hunderttausend Multiplikationen kosten weniger als ein einzelnes Bild. Teuer
 * war nie das Rechnen, sondern die dreieinhalb Megabyte Bazaar-Daten - und die bleiben
 * auf GitHub.
 *
 * <p>Die Rezepte liegen als Zahlenfelder, nicht als Objekte: Hunderttausend kleine
 * Objekte waeren zwanzig Megabyte Speicher und eine Aufgabe fuer den Aufraeumer bei
 * jedem Blick in die Liste. Vier int-Felder sind zwei Megabyte und bleiben liegen.
 */
public final class ShardProfitData {

    private static final String BASE =
            "https://raw.githubusercontent.com/shokiteufel/ShokiMod/data/";
    private static final String PRICES_URL = BASE + "shardprices.json";
    private static final String RECIPES_URL = BASE + "shardrecipes.json";
    private static final String PRICES_FILE = "shardprices.json";
    private static final String RECIPES_FILE = "shardrecipes.json";

    private static final long REFRESH_MILLIS = 5 * 60 * 1000L;
    private static final long RETRY_MILLIS = 2 * 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    /** Was ein Shard kostet, bringt und wie er heisst */
    private record Shard(String name, String category, String rarity, int fuseAmount,
                         long instantBuy, long instantSell, int volume, String area) {
    }

    /**
     * Eine Fusion, fertig zum Anzeigen.
     *
     * Entsteht erst beim Filtern und nur fuer die Treffer - alle 128.000 auf einmal
     * als Objekte waeren Verschwendung.
     */
    public record Row(String result, String first, String second, int amount,
                      int firstAmount, int secondAmount,
                      String rarity, String category, String area,
                      long firstInstant, long firstOrder,
                      long secondInstant, long secondOrder,
                      long resultInstant, long resultOrder, int volume) {

        /**
         * Was die beiden Zutaten zusammen kosten.
         *
         * Mal der Stueckzahl, und die ist selten eins: Eine Fusion verschlingt von
         * jeder Zutat meist fuenf Shards, bei manchen zwei.
         *
         * @param instant sofort kaufen (teurer, sofort da) statt per Buy Order
         */
        public long cost(boolean instant) {
            return instant
                    ? (long) firstAmount * firstInstant + (long) secondAmount * secondInstant
                    : (long) firstAmount * firstOrder + (long) secondAmount * secondOrder;
        }

        /**
         * Was das Ergebnis einbringt - die ganze Ausbeute, nicht ein Stueck.
         *
         * @param instant sofort verkaufen (weniger, sofort da) statt per Sell Order
         */
        public long revenue(boolean instant) {
            return amount * (instant ? resultInstant : resultOrder);
        }

        /** Was uebrig bleibt. Die beiden Wege sind unabhaengig voneinander */
        public long profit(boolean instantBuy, boolean instantSell) {
            return revenue(instantSell) - cost(instantBuy);
        }

        /** "5x Sun Fish + 5x Sun Fish" - die Stueckzahl gehoert sichtbar dazu */
        public String recipe() {
            return firstAmount + "x " + first + " + " + secondAmount + "x " + second;
        }

        /** "2x Galaxy Fish" */
        public String yield() {
            return amount + "x " + result;
        }
    }

    // ---- Die Rezepte, als Zahlenfelder ----
    private static volatile String[] codes = new String[0];
    private static volatile Shard[] shards = new Shard[0];
    private static volatile int[] outIndex = new int[0];
    private static volatile int[] outAmount = new int[0];
    private static volatile int[] firstIndex = new int[0];
    private static volatile int[] secondIndex = new int[0];
    private static volatile int count = 0;
    private static volatile String recipeKey = "";

    private static volatile String updated = "";
    private static volatile int minVolume = 1000;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static volatile long attemptedAt = 0L;
    private static volatile long succeededAt = 0L;
    private static volatile String lastError = null;
    private static volatile String origin = "nothing loaded";
    private static volatile boolean cacheRead = false;

    private ShardProfitData() {
    }

    public static boolean ready() {
        return count > 0 && shards.length > 0;
    }

    /** Wie viele Kombinationen vorliegen */
    public static int combinations() {
        return count;
    }

    /** Die Sparten, die vorkommen - fuer die Reiter im Fenster */
    public static List<String> categories() {
        Set<String> found = new LinkedHashSet<>();
        for (Shard s : shards) {
            if (s != null && !s.category().isEmpty()) found.add(s.category());
        }
        List<String> out = new ArrayList<>(found);
        out.sort(String::compareTo);
        return out;
    }

    /** Ab welchem Wochenumsatz ein Preis als belastbar gilt */
    public static int minVolume() {
        return minVolume;
    }

    /**
     * Die Fusionen, die zu den Vorgaben passen - fertig gereiht.
     *
     * Hier laufen alle Kombinationen durch. Ein Durchlauf ueber 128.000 Zahlenpaare
     * dauert im Millisekundenbereich; das Fenster ruft ihn deshalb nur, wenn sich
     * etwas an den Vorgaben geaendert hat, und haelt das Ergebnis fest.
     *
     * @param category  nur diese Sparte, oder leer fuer alle
     * @param search    muss in Zutat, Ergebnis oder beidem vorkommen; leer fuer alle
     * @param owned     Vorrat je Shard-Name, oder null - dann wird nicht geprueft
     * @param onlyGain  nur Fusionen, die auf diesem Weg wirklich etwas abwerfen
     */
    public static List<Row> select(String category, String search,
                                   Map<String, Integer> owned, boolean onlyGain,
                                   boolean instantBuy, boolean instantSell, int limit) {
        prefetch();
        if (!ready()) return List.of();

        String suche = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String sparte = category == null ? "" : category;
        Shard[] tabelle = shards;
        List<Row> out = new ArrayList<>(Math.min(limit, 512));

        for (int i = 0; i < count; i++) {
            Shard ziel = tabelle[outIndex[i]];
            Shard a = tabelle[firstIndex[i]];
            Shard b = tabelle[secondIndex[i]];
            if (ziel == null || a == null || b == null) continue;

            if (!sparte.isEmpty() && !sparte.equals(ziel.category())) continue;
            // Ein Preis ohne Umsatz ist keine Gelegenheit, sondern eine Falle
            if (ziel.volume() < minVolume || a.volume() < minVolume || b.volume() < minVolume) {
                continue;
            }
            if (!suche.isEmpty()
                    && !ziel.name().toLowerCase(Locale.ROOT).contains(suche)
                    && !a.name().toLowerCase(Locale.ROOT).contains(suche)
                    && !b.name().toLowerCase(Locale.ROOT).contains(suche)) {
                continue;
            }
            if (owned != null && !hasEnough(owned, a, b)) continue;

            int menge = outAmount[i];
            long kosten = instantBuy
                    ? (long) a.fuseAmount() * a.instantBuy() + (long) b.fuseAmount() * b.instantBuy()
                    : (long) a.fuseAmount() * a.instantSell() + (long) b.fuseAmount() * b.instantSell();
            long erloes = (long) menge * (instantSell ? ziel.instantSell() : ziel.instantBuy());
            if (onlyGain && erloes - kosten <= 0) continue;

            out.add(new Row(ziel.name(), a.name(), b.name(), menge,
                    a.fuseAmount(), b.fuseAmount(),
                    ziel.rarity(), ziel.category(), ziel.area(),
                    a.instantBuy(), a.instantSell(),
                    b.instantBuy(), b.instantSell(),
                    ziel.instantSell(), ziel.instantBuy(), ziel.volume()));
        }

        out.sort((x, y) -> Long.compare(y.profit(instantBuy, instantSell),
                                        x.profit(instantBuy, instantSell)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /**
     * Reicht der Vorrat fuer beide Zutaten?
     *
     * Der Sonderfall steckt in der Gleichheit: Sind beide Zutaten derselbe Shard,
     * braucht es die Summe. Getrennt geprueft kaeme "ich habe fuenf" bei einer
     * Fusion durch, die zehn verlangt.
     */
    private static boolean hasEnough(Map<String, Integer> owned, Shard a, Shard b) {
        if (a.name().equalsIgnoreCase(b.name())) {
            return have(owned, a.name()) >= a.fuseAmount() + b.fuseAmount();
        }
        return have(owned, a.name()) >= a.fuseAmount()
                && have(owned, b.name()) >= b.fuseAmount();
    }

    private static int have(Map<String, Integer> owned, String name) {
        Integer n = owned.get(name.toLowerCase(Locale.ROOT));
        return n == null ? 0 : n;
    }

    /** Wie alt der Preisstand ist, in Worten */
    public static String age() {
        if (updated.isEmpty()) return "";
        try {
            java.time.Instant stand = java.time.Instant.parse(updated);
            long minuten = java.time.Duration.between(stand, java.time.Instant.now()).toMinutes();
            if (minuten < 1) return "just now";
            if (minuten == 1) return "1 minute ago";
            if (minuten < 60) return minuten + " minutes ago";
            long stunden = minuten / 60;
            return stunden == 1 ? "1 hour ago" : stunden + " hours ago";
        } catch (RuntimeException e) {
            return updated;
        }
    }

    /** Holt nach, wenn der Stand alt genug ist - und immer nur einmal gleichzeitig */
    public static void prefetch() {
        if (!cacheRead) {
            cacheRead = true;
            readCache();
        }
        long now = System.currentTimeMillis();
        if (now - attemptedAt < (ready() ? REFRESH_MILLIS : RETRY_MILLIS)) return;
        if (!fetching.compareAndSet(false, true)) return;

        attemptedAt = now;
        Thread worker = new Thread(ShardProfitData::fetch, "ShokiMod shard fusions");
        worker.setDaemon(true);
        worker.start();
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        StringBuilder out = new StringBuilder("shard fusions: ");
        if (!ready()) {
            out.append("EMPTY");
        } else {
            out.append(count).append(" combinations, ").append(shards.length)
               .append(" shards from ").append(origin)
               .append(", recipes ").append(recipeKey)
               .append(", prices ").append(updated);
        }
        if (succeededAt > 0) out.append(", last success ").append(ago(succeededAt));
        if (attemptedAt > 0) out.append(", last attempt ").append(ago(attemptedAt));
        if (lastError != null) out.append(", last error: ").append(lastError);
        return out.toString();
    }

    private static String ago(long at) {
        long seconds = Math.max(0, (System.currentTimeMillis() - at) / 1000L);
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return (seconds / 60) + "min ago";
        return (seconds / 3600) + "h ago";
    }

    private static void fetch() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            String preise = get(client, PRICES_URL);
            if (preise == null) return;

            JsonObject root = GSON.fromJson(preise, JsonObject.class);
            if (root == null) {
                fail("prices file unreadable");
                return;
            }
            String wanted = string(root, "rezeptKennung");

            // Die Rezepte nur holen, wenn sie sich geaendert haben. Anderthalb
            // Megabyte alle fuenf Minuten waeren Verschwendung fuer eine Datei,
            // die sich nur bei einem Hypixel-Update ruehrt
            if (!wanted.isEmpty() && !wanted.equals(recipeKey)) {
                String rezepte = get(client, RECIPES_URL);
                if (rezepte == null) return;
                if (!parseRecipes(rezepte)) {
                    fail("recipe file had no usable entries");
                    return;
                }
                writeCache(RECIPES_FILE, rezepte);
            }
            if (!parsePrices(preise, "network")) {
                fail("price file had no usable entries");
                return;
            }
            succeededAt = System.currentTimeMillis();
            lastError = null;
            writeCache(PRICES_FILE, preise);
        } catch (IOException | RuntimeException e) {
            fail(e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fetching.set(false);
        }
    }

    private static String get(HttpClient client, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "ShokiMod")
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            fail("HTTP " + response.statusCode() + " for " + url);
            return null;
        }
        return response.body();
    }

    private static void fail(String reason) {
        lastError = reason;
        ShokiMod.LOGGER.warn("[ShokiMod] Could not load shard fusions: {}", reason);
    }

    /** Die Rezepte in die Zahlenfelder schaufeln */
    private static boolean parseRecipes(String body) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return false;
        JsonObject rezepte = root.getAsJsonObject("rezepte");
        if (rezepte == null || rezepte.isEmpty()) return false;

        // Erst zaehlen, dann einmal belegen: Ein wachsendes Feld wuerde die
        // hunderttausend Eintraege mehrfach umkopieren
        int total = 0;
        for (String ergebnis : rezepte.keySet()) {
            JsonObject nachMenge = rezepte.getAsJsonObject(ergebnis);
            for (String menge : nachMenge.keySet()) {
                JsonArray flach = nachMenge.getAsJsonArray(menge);
                if (flach != null) total += flach.size() / 2;
            }
        }
        if (total <= 0) return false;

        Map<String, Integer> nummer = new HashMap<>();
        List<String> liste = new ArrayList<>();
        int[] out = new int[total];
        int[] menge = new int[total];
        int[] erste = new int[total];
        int[] zweite = new int[total];

        int n = 0;
        for (String ergebnis : rezepte.keySet()) {
            int zielNr = index(nummer, liste, ergebnis);
            JsonObject nachMenge = rezepte.getAsJsonObject(ergebnis);
            for (String mengeText : nachMenge.keySet()) {
                int stueck;
                try {
                    stueck = Integer.parseInt(mengeText);
                } catch (NumberFormatException e) {
                    continue;
                }
                JsonArray flach = nachMenge.getAsJsonArray(mengeText);
                if (flach == null) continue;
                for (int i = 0; i + 1 < flach.size() && n < total; i += 2) {
                    out[n] = zielNr;
                    menge[n] = stueck;
                    erste[n] = index(nummer, liste, flach.get(i).getAsString());
                    zweite[n] = index(nummer, liste, flach.get(i + 1).getAsString());
                    n++;
                }
            }
        }

        codes = liste.toArray(new String[0]);
        outIndex = out;
        outAmount = menge;
        firstIndex = erste;
        secondIndex = zweite;
        count = n;
        recipeKey = string(root, "kennung");
        // Die Preistabelle passt jetzt nicht mehr - sie wird gleich neu gesetzt
        shards = new Shard[codes.length];
        return true;
    }

    private static int index(Map<String, Integer> nummer, List<String> liste, String code) {
        Integer vorhanden = nummer.get(code);
        if (vorhanden != null) return vorhanden;
        int neu = liste.size();
        liste.add(code);
        nummer.put(code, neu);
        return neu;
    }

    /** Die Preise auf die Codes legen, die die Rezepte benutzen */
    private static boolean parsePrices(String body, String from) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return false;
        JsonObject liste = root.getAsJsonObject("shards");
        if (liste == null || liste.isEmpty()) return false;
        if (codes.length == 0) return false;

        Shard[] tabelle = new Shard[codes.length];
        int gefunden = 0;
        for (int i = 0; i < codes.length; i++) {
            JsonObject o = liste.getAsJsonObject(codes[i]);
            if (o == null) continue;
            tabelle[i] = new Shard(
                    string(o, "n"),
                    string(o, "s"),
                    string(o, "r"),
                    Math.max(1, number(o, "f").intValue()),
                    number(o, "k").longValue(),
                    number(o, "v").longValue(),
                    number(o, "u").intValue(),
                    string(o, "g"));
            gefunden++;
        }
        if (gefunden == 0) return false;

        shards = tabelle;
        updated = string(root, "aktualisiert");
        int grenze = number(root, "mindestumsatz").intValue();
        if (grenze > 0) minVolume = grenze;
        origin = from;
        return true;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static Number number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsNumber();
    }

    private static Path cachePath(String name) {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("shokimod").resolve(name);
    }

    private static void readCache() {
        try {
            Path rezepte = cachePath(RECIPES_FILE);
            Path preise = cachePath(PRICES_FILE);
            if (!Files.isRegularFile(rezepte) || !Files.isRegularFile(preise)) return;
            if (parseRecipes(Files.readString(rezepte, StandardCharsets.UTF_8))) {
                parsePrices(Files.readString(preise, StandardCharsets.UTF_8), "disk");
            }
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not read cached shard data: {}", e.toString());
        }
    }

    private static void writeCache(String name, String body) {
        try {
            Path path = cachePath(name);
            Files.createDirectories(path.getParent());
            Files.writeString(path, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not cache {}: {}", name, e.toString());
        }
    }
}
