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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Welche Shard-Fusionen etwas abwerfen - fertig gerechnet, nur noch abzuholen.
 *
 * Es sind rund 130.000 Kombinationen gegen zweitausend Bazaar-Preise zu pruefen. Das
 * gehoert nicht in den Spieltakt, deshalb rechnet ein Ablaufplan auf GitHub und legt
 * das Ergebnis als kleine Datei ab (tools/shardprofit.py im selben Verzeichnisbaum).
 * Hier wird nur geholt - derselbe Weg wie bei {@link PetProfitData}.
 *
 * Die Rezepte stammen von SkyShards (MIT), die Preise vom Bazaar.
 */
public final class ShardProfitData {

    private static final String ENDPOINT =
            "https://raw.githubusercontent.com/shokiteufel/ShokiMod/data/shardprofit.json";
    private static final String CACHE_FILE = "shardprofit.json";
    /** Gerechnet wird alle zehn Minuten; wer alle fuenf nachsieht, hinkt nie weit hinterher */
    private static final long REFRESH_MILLIS = 5 * 60 * 1000L;
    private static final long RETRY_MILLIS = 2 * 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Gson GSON = new Gson();

    /**
     * Eine Fusion mit den Preisen ihrer Zutaten und ihres Ergebnisses.
     *
     * Gespeichert sind Preise, kein fertiger Gewinn: Fuer Zutaten und Ergebnis gibt es
     * je zwei Wege, und sie lassen sich frei mischen. Zutaten sofort kaufen und das
     * Ergebnis in Ruhe per Auftrag verkaufen ist eine ebenso gueltige Wahl wie jede
     * andere - bei derselben Fusion liegen zwischen dem schlechtesten und dem besten
     * Weg schon einmal zwei Millionen. Welcher gilt, weiss nur der Spieler; deshalb
     * wird hier gerechnet und nicht vorgerechnet.
     */
    public record Row(String result, String resultId, int amount,
                      String first, String second, String firstId, String secondId,
                      String rarity, String category, String area,
                      long firstInstant, long firstOrder,
                      long secondInstant, long secondOrder,
                      long resultInstant, long resultOrder,
                      long best, int volume) {

        /**
         * Was die beiden Zutaten zusammen kosten.
         *
         * @param instant sofort kaufen (teurer, sofort da) statt per Buy Order
         */
        public long cost(boolean instant) {
            return instant ? firstInstant + secondInstant : firstOrder + secondOrder;
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

        /** "2x Galaxy Fish" */
        public String yield() {
            return amount + "x " + result;
        }

        /** "Sun Fish + Sun Fish" */
        public String recipe() {
            return first + " + " + second;
        }
    }

    private static volatile List<Row> rows = List.of();
    private static volatile String updated = "";
    private static volatile long combinations = 0L;
    private static volatile long worthwhile = 0L;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static volatile long attemptedAt = 0L;
    private static volatile long succeededAt = 0L;
    private static volatile String lastError = null;
    private static volatile String origin = "nothing loaded";
    private static volatile boolean cacheRead = false;

    private ShardProfitData() {
    }

    /** Alle Zeilen, beste zuerst. Leer heisst: noch nichts geladen */
    public static List<Row> rows() {
        prefetch();
        return rows;
    }

    /** Die Sparten, die wirklich vorkommen - fuer die Reiter im Fenster */
    public static List<String> categories() {
        Set<String> found = new LinkedHashSet<>();
        for (Row row : rows) {
            if (!row.category().isEmpty()) found.add(row.category());
        }
        List<String> out = new ArrayList<>(found);
        out.sort(String::compareTo);
        return out;
    }

    /** Wie viele Kombinationen dafuer durchgerechnet wurden */
    public static long combinationsChecked() {
        return combinations;
    }

    /** Wie viele davon sich lohnen - auch die, die nicht mehr in die Datei passten */
    public static long worthwhileCount() {
        return worthwhile;
    }

    /** Wie alt der Stand ist, in Worten. Dieselbe Rechnung wie bei den Pets */
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

    public static boolean ready() {
        return !rows.isEmpty();
    }

    /** Holt nach, wenn der Stand alt genug ist - und immer nur einmal gleichzeitig */
    public static void prefetch() {
        if (!cacheRead) {
            cacheRead = true;
            readCache();
        }
        long now = System.currentTimeMillis();
        if (now - attemptedAt < (rows.isEmpty() ? RETRY_MILLIS : REFRESH_MILLIS)) return;
        if (!fetching.compareAndSet(false, true)) return;

        attemptedAt = now;
        Thread worker = new Thread(ShardProfitData::fetch, "ShokiMod shard profit");
        worker.setDaemon(true);
        worker.start();
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        StringBuilder out = new StringBuilder("shard profit: ");
        if (rows.isEmpty()) {
            out.append("EMPTY");
        } else {
            out.append(rows.size()).append(" fusions from ").append(origin)
               .append(" (").append(worthwhile).append(" of ").append(combinations)
               .append(" worthwhile), computed ").append(updated);
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("User-Agent", "ShokiMod")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                fail("HTTP " + response.statusCode());
                return;
            }
            if (parse(response.body(), "network")) {
                succeededAt = System.currentTimeMillis();
                lastError = null;
                writeCache(response.body());
            } else {
                fail("response had no usable entries");
            }
        } catch (IOException | RuntimeException e) {
            fail(e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fetching.set(false);
        }
    }

    private static void fail(String reason) {
        lastError = reason;
        ShokiMod.LOGGER.warn("[ShokiMod] Could not load shard fusion list: {}", reason);
    }

    private static boolean parse(String body, String from) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return false;
        JsonArray list = root.getAsJsonArray("fusionen");
        if (list == null || list.isEmpty()) return false;

        List<Row> parsed = new ArrayList<>(list.size());
        for (JsonElement element : list) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            String ergebnis = string(o, "ergebnis");
            if (ergebnis.isEmpty()) continue;
            parsed.add(new Row(
                    ergebnis,
                    string(o, "ergebnisId"),
                    number(o, "menge").intValue(),
                    string(o, "zutat1"),
                    string(o, "zutat2"),
                    string(o, "zutat1Id"),
                    string(o, "zutat2Id"),
                    string(o, "seltenheit").toUpperCase(Locale.ROOT),
                    string(o, "sparte").toUpperCase(Locale.ROOT),
                    string(o, "gebiet"),
                    number(o, "z1Sofort").longValue(),
                    number(o, "z1Auftrag").longValue(),
                    number(o, "z2Sofort").longValue(),
                    number(o, "z2Auftrag").longValue(),
                    number(o, "ergSofort").longValue(),
                    number(o, "ergAuftrag").longValue(),
                    number(o, "bester").longValue(),
                    number(o, "umsatz").intValue()));
        }
        if (parsed.isEmpty()) return false;

        rows = List.copyOf(parsed);
        updated = string(root, "aktualisiert");
        combinations = number(root, "kombinationen").longValue();
        worthwhile = number(root, "lohnend").longValue();
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

    private static Path cachePath() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("shokimod").resolve(CACHE_FILE);
    }

    private static void readCache() {
        try {
            Path path = cachePath();
            if (!Files.isRegularFile(path)) return;
            parse(Files.readString(path, StandardCharsets.UTF_8), "disk");
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not read cached shard list: {}", e.toString());
        }
    }

    private static void writeCache(String body) {
        try {
            Path path = cachePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not cache shard list: {}", e.toString());
        }
    }
}
