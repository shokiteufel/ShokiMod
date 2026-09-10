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
 * Welche Pets sich zu leveln lohnen - fertig gerechnet, nur noch abzuholen.
 *
 * Die Frage braucht das ganze Auktionshaus: rund vierzig Seiten und fuenfunddreissig-
 * tausend Angebote. Das gehoert nicht auf den Rechner eines Spielers, deshalb rechnet
 * ein Ablaufplan auf GitHub alle dreissig Minuten und legt das Ergebnis als kleine
 * Datei ab (tools/petprofit.py im selben Verzeichnisbaum). Hier wird nur geholt.
 *
 * Geholt wird in einem eigenen Thread und hoechstens einmal gleichzeitig; der letzte
 * Stand liegt auf der Platte, damit direkt nach dem Einloggen schon etwas dasteht.
 */
public final class PetProfitData {

    private static final String ENDPOINT =
            "https://raw.githubusercontent.com/shokiteufel/ShokiMod/data/petprofit.json";
    private static final String CACHE_FILE = "petprofit.json";
    /** Neu gerechnet wird alle dreissig Minuten - oefter zu fragen bringt nichts */
    private static final long REFRESH_MILLIS = 15 * 60 * 1000L;
    private static final long RETRY_MILLIS = 2 * 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Gson GSON = new Gson();

    /** Ein Angebot mit dem, was das Hochziehen einbringen wuerde */
    public record Row(String id, String name, String category, String rarity, int level,
                      long price, long targetPrice, int targetLevel, long profit, long xp,
                      double perXp, String auction) {
    }

    /** Ein fertiges Pet auf Hoechststufe, mit dem Preis, den es gerade kostet */
    public record Ready(String id, String name, String category, String rarity,
                        int level, long price, String auction) {
    }

    private static volatile List<Row> rows = List.of();
    private static volatile List<Ready> ready = List.of();
    private static volatile String updated = "";
    private static volatile long auctions = 0L;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static volatile long attemptedAt = 0L;
    private static volatile long succeededAt = 0L;
    private static volatile String lastError = null;
    private static volatile String origin = "nothing loaded";
    private static volatile boolean cacheRead = false;

    private PetProfitData() {
    }

    /** Alle Zeilen, bestes Angebot zuerst. Leer heisst: noch nichts geladen */
    public static List<Row> rows() {
        prefetch();
        return rows;
    }

    /** Die guenstigsten Angebote auf Hoechststufe, billigstes zuerst */
    public static List<Ready> readyPets() {
        prefetch();
        return ready;
    }

    /** Die Sparten, die in den Daten wirklich vorkommen - fuer die Auswahl im Fenster */
    public static List<String> categories() {
        Set<String> found = new LinkedHashSet<>();
        for (Row row : rows) found.add(row.category());
        for (Ready r : ready) found.add(r.category());
        List<String> out = new ArrayList<>(found);
        out.sort(String::compareTo);
        return out;
    }

    /** Wann die Liste gerechnet wurde, wie sie es selbst angibt */
    public static String updatedAt() {
        return updated;
    }

    /** Wie viele Angebote dafuer durchgesehen wurden */
    public static long auctionsSeen() {
        return auctions;
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
        long age = now - attemptedAt;
        if (age < (rows.isEmpty() ? RETRY_MILLIS : REFRESH_MILLIS)) return;
        if (!fetching.compareAndSet(false, true)) return;

        attemptedAt = now;
        Thread worker = new Thread(PetProfitData::fetch, "ShokiMod pet profit");
        worker.setDaemon(true);
        worker.start();
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        StringBuilder out = new StringBuilder("pet profit: ");
        if (rows.isEmpty()) {
            out.append("EMPTY");
        } else {
            out.append(rows.size()).append(" pets from ").append(origin)
               .append(", computed ").append(updated);
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
        ShokiMod.LOGGER.warn("[ShokiMod] Could not load pet profit list: {}", reason);
    }

    private static boolean parse(String body, String from) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return false;
        JsonArray list = root.getAsJsonArray("pets");
        if (list == null || list.isEmpty()) return false;

        List<Row> parsed = new ArrayList<>(list.size());
        for (JsonElement element : list) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            String name = string(o, "name");
            if (name.isEmpty()) continue;
            parsed.add(new Row(
                    string(o, "id"),
                    name,
                    string(o, "sparte").toUpperCase(Locale.ROOT),
                    string(o, "seltenheit").toUpperCase(Locale.ROOT),
                    number(o, "stufe").intValue(),
                    number(o, "preis").longValue(),
                    number(o, "zielpreis").longValue(),
                    number(o, "zielstufe").intValue(),
                    number(o, "gewinn").longValue(),
                    number(o, "xp").longValue(),
                    number(o, "proXp").doubleValue(),
                    string(o, "auktion")));
        }
        if (parsed.isEmpty()) return false;

        parsed.sort((a, b) -> Double.compare(b.perXp(), a.perXp()));
        rows = List.copyOf(parsed);

        // Die zweite Liste: was ein fertiges Exemplar gerade kostet
        List<Ready> fertige = new ArrayList<>();
        JsonArray auchFertig = root.getAsJsonArray("fertige");
        if (auchFertig != null) {
            for (JsonElement element : auchFertig) {
                if (!element.isJsonObject()) continue;
                JsonObject o = element.getAsJsonObject();
                String name = string(o, "name");
                if (name.isEmpty()) continue;
                fertige.add(new Ready(
                        string(o, "id"), name,
                        string(o, "sparte").toUpperCase(Locale.ROOT),
                        string(o, "seltenheit").toUpperCase(Locale.ROOT),
                        number(o, "stufe").intValue(),
                        number(o, "preis").longValue(),
                        string(o, "auktion")));
            }
        }
        fertige.sort((a, b) -> Long.compare(a.price(), b.price()));
        ready = List.copyOf(fertige);
        updated = string(root, "aktualisiert");
        auctions = number(root, "angebote").longValue();
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
            ShokiMod.LOGGER.warn("[ShokiMod] Could not read cached pet profit list: {}", e.toString());
        }
    }

    private static void writeCache(String body) {
        try {
            Path path = cachePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not cache pet profit list: {}", e.toString());
        }
    }
}
