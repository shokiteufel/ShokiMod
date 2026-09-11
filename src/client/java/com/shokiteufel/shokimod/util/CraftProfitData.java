package com.shokiteufel.shokimod.util;

import com.google.gson.Gson;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Was sich zu bauen lohnt - Handwerksrezepte, bewertet mit den Preisen von jetzt.
 *
 * <p>Geholt werden nur die Rezepte; die Preise fuehrt die Mod laengst selbst. Der
 * Bazaar und die Auktions-Tiefstpreise liegen in {@link ItemValue}, und beide sind
 * dort frischer als alles, was eine vorgerechnete Datei liefern koennte. Rezepte
 * dagegen aendern sich nur, wenn Hypixel etwas aendert - dreihundert Kilobyte, einmal
 * geholt und behalten.
 *
 * <p><b>Die zwei Fragen, die das Fenster beantwortet:</b> "Was kostet es, das hier zu
 * bauen, und was bringt es?" - und die andere Richtung: "Ich habe dieses Material,
 * was kann ich daraus bauen?" Deshalb sucht die Liste in beiden Richtungen: im
 * Ergebnis und in den Zutaten. Enchanted String wird in dreiundzwanzig Rezepten
 * gebraucht, und keines davon faende, wer nur nach Ergebnissen sucht.
 */
public final class CraftProfitData {

    private static final String URL =
            "https://raw.githubusercontent.com/shokiteufel/ShokiMod/data/craftrecipes.json";
    private static final String CACHE_FILE = "craftrecipes.json";
    /** Rezepte aendern sich selten - einmal am Tag nachsehen genuegt */
    private static final long REFRESH_MILLIS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_MILLIS = 5 * 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    /** Eine Zutat: was und wie viel davon */
    public record Part(String id, String name, int amount) {
    }

    /**
     * Ein Rezept mit dem, was es kostet und einbringt.
     *
     * Gerechnet wird beim Bauen der Zeile, nicht beim Anzeigen: Die Preise koennen
     * sich zwischen zwei Bildern aendern, und dann stuende in der Liste eine andere
     * Zahl, als die Reihung benutzt hat.
     */
    public record Row(String id, String name, int amount, List<Part> parts,
                      long cost, long revenue, boolean complete) {

        public long profit() {
            return revenue - cost;
        }

        /** "3x Enchanted String, 1x Stick" */
        public String partsText() {
            StringBuilder out = new StringBuilder();
            for (Part p : parts) {
                if (out.length() > 0) out.append(", ");
                out.append(p.amount()).append("x ").append(p.name());
            }
            return out.toString();
        }

        /** "1x Grappling Hook" */
        public String yield() {
            return amount + "x " + name;
        }
    }

    /** Ein Rezept, wie es in der Datei steht */
    private record Recipe(String result, int amount, String[] ids, int[] counts) {
    }

    private static volatile List<Recipe> recipes = List.of();
    private static volatile Map<String, String> names = Map.of();
    private static volatile String updated = "";
    private static volatile String key = "";
    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static volatile long attemptedAt = 0L;
    private static volatile long succeededAt = 0L;
    private static volatile String lastError = null;
    private static volatile String origin = "nothing loaded";
    private static volatile boolean cacheRead = false;

    private CraftProfitData() {
    }

    public static boolean ready() {
        return !recipes.isEmpty();
    }

    public static int recipeCount() {
        return recipes.size();
    }

    /** Der lesbare Name zu einer Kennung, oder die Kennung selbst */
    public static String nameOf(String id) {
        String n = names.get(id);
        return n == null || n.isBlank() ? pretty(id) : n;
    }

    /** "ENCHANTED_STRING" liest sich zur Not als "Enchanted String" */
    private static String pretty(String id) {
        String[] worte = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String w : worte) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    /**
     * Die Rezepte, die zur Suche passen - fertig gerechnet und gereiht.
     *
     * @param search       muss im Ergebnis oder in einer Zutat vorkommen; leer heisst alle
     * @param inputsOnly   nur in den Zutaten suchen, nicht im Ergebnis
     * @param instantBuy   Material sofort kaufen statt per Buy Order
     * @param instantSell  Ergebnis sofort verkaufen statt per Sell Order
     * @param onlyGain     nur was etwas abwirft
     */
    public static List<Row> select(String search, boolean instantBuy,
                                   boolean instantSell, boolean sellToBazaar,
                                   boolean onlyGain, int limit) {
        prefetch();
        if (!ready()) return List.of();

        String suche = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>(Math.min(limit, 256));

        for (Recipe r : recipes) {
            if (!suche.isEmpty() && !matches(r, suche)) continue;

            long kosten = 0;
            boolean vollstaendig = true;
            List<Part> teile = new ArrayList<>(r.ids().length);
            for (int i = 0; i < r.ids().length; i++) {
                String id = r.ids()[i];
                int menge = r.counts()[i];
                teile.add(new Part(id, nameOf(id), menge));
                long stueck = buyPrice(id, instantBuy);
                // Ohne Preis laesst sich die Fusion nicht bewerten. Sie faellt nicht
                // weg - man will sie ja trotzdem sehen -, aber ihre Zahl ist dann
                // keine, und das muss dranstehen
                if (stueck <= 0) {
                    vollstaendig = false;
                } else {
                    kosten += stueck * (long) menge;
                }
            }
            long erloes = sellPrice(r.result(), instantSell, sellToBazaar) * (long) r.amount();
            if (erloes <= 0) vollstaendig = false;
            if (onlyGain && (!vollstaendig || erloes - kosten <= 0)) continue;

            out.add(new Row(r.result(), nameOf(r.result()), r.amount(),
                    List.copyOf(teile), kosten, erloes, vollstaendig));
        }

        // Was sich nicht bewerten laesst, nach hinten - sonst stuende eine Zeile ohne
        // Preise oben, nur weil ihre Kosten mangels Zahlen bei null liegen
        out.sort((a, b) -> {
            if (a.complete() != b.complete()) return a.complete() ? -1 : 1;
            return Long.compare(b.profit(), a.profit());
        });
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static boolean matches(Recipe r, String suche) {
        for (String id : r.ids()) {
            if (nameOf(id).toLowerCase(Locale.ROOT).contains(suche)) return true;
        }
        return nameOf(r.result()).toLowerCase(Locale.ROOT).contains(suche);
    }

    /**
     * Was ein Stueck kostet.
     *
     * Die Bazaar-Zahlen heissen nach der Verkaufsrichtung, meinen aber beide
     * Richtungen: Was eine Sell Order einbringt, ist genau das, was ein Sofortkauf
     * kostet - es ist dasselbe offene Angebot, von der anderen Seite gesehen. Fuer
     * alles ohne Bazaar bleibt der Tiefstpreis im Auktionshaus.
     */
    private static long buyPrice(String id, boolean instant) {
        long[] live = BazaarLive.priceOf(id);
        if (live != null) {
            long wert = instant ? live[0] : live[1];
            if (wert > 0) return wert;
        }
        ItemValue.BazaarPrice bazaar = ItemValue.BAZAAR.get(id);
        if (bazaar != null) {
            double wert = instant ? bazaar.sellOrder() : bazaar.instantSell();
            if (wert > 0) return Math.round(wert);
        }
        Double bin = ItemValue.LOWEST_BIN.get(id);
        return bin != null && bin > 0 ? Math.round(bin) : 0;
    }

    /**
     * Was ein Stueck einbringt - an dem Ort, den der Betrachter gewaehlt hat.
     *
     * Bazaar und Auktionshaus sind zwei verschiedene Maerkte, und die Preise gehen
     * weit auseinander. Was im Bazaar zu Tausenden gehandelt wird, bringt im
     * Auktionshaus mitunter das Doppelte - dafuer einzeln und mit Wartezeit. Welcher
     * Weg gemeint ist, kann nur der Spieler wissen.
     *
     * Was es am gewaehlten Ort nicht gibt, wird am anderen nachgeschlagen: Eine Zeile
     * ohne Preis nuetzt niemandem, und die Herkunft steht ohnehin in der Spalte.
     */
    private static long sellPrice(String id, boolean instant, boolean bazaarFirst) {
        if (bazaarFirst) {
            long[] live = BazaarLive.priceOf(id);
            if (live != null) {
                long wert = instant ? live[1] : live[0];
                if (wert > 0) return wert;
            }
            ItemValue.BazaarPrice bazaar = ItemValue.BAZAAR.get(id);
            if (bazaar != null) {
                double wert = instant ? bazaar.instantSell() : bazaar.sellOrder();
                if (wert > 0) return Math.round(wert);
            }
            Double bin = ItemValue.LOWEST_BIN.get(id);
            return bin != null && bin > 0 ? Math.round(bin) : 0;
        }
        Double bin = ItemValue.LOWEST_BIN.get(id);
        if (bin != null && bin > 0) return Math.round(bin);
        long[] live = BazaarLive.priceOf(id);
        if (live != null) {
            long wert = instant ? live[1] : live[0];
            if (wert > 0) return wert;
        }
        ItemValue.BazaarPrice bazaar = ItemValue.BAZAAR.get(id);
        if (bazaar != null) {
            double wert = instant ? bazaar.instantSell() : bazaar.sellOrder();
            if (wert > 0) return Math.round(wert);
        }
        return 0;
    }

    /** Holt nach, wenn der Stand alt genug ist */
    public static void prefetch() {
        if (!cacheRead) {
            cacheRead = true;
            readCache();
        }
        // Die Preise kommen von woanders - die hier anzustossen gehoert trotzdem
        // hierher, sonst steht das Fenster beim ersten Oeffnen ohne Zahlen da
        ItemValue.prefetch();
        long now = System.currentTimeMillis();
        if (now - attemptedAt < (ready() ? REFRESH_MILLIS : RETRY_MILLIS)) return;
        if (!fetching.compareAndSet(false, true)) return;

        attemptedAt = now;
        Thread worker = new Thread(CraftProfitData::fetch, "ShokiMod craft recipes");
        worker.setDaemon(true);
        worker.start();
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        StringBuilder out = new StringBuilder("craft recipes: ");
        if (!ready()) {
            out.append("EMPTY");
        } else {
            out.append(recipes.size()).append(" recipes, ").append(names.size())
               .append(" names from ").append(origin)
               .append(", built ").append(updated).append(" (").append(key).append(")");
        }
        if (succeededAt > 0) out.append(", last success ").append(ago(succeededAt));
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
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
                fail("no usable recipes in the answer");
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
        ShokiMod.LOGGER.warn("[ShokiMod] Could not load craft recipes: {}", reason);
    }

    private static boolean parse(String body, String from) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return false;
        JsonObject liste = root.getAsJsonObject("rezepte");
        if (liste == null || liste.isEmpty()) return false;

        Map<String, String> gefundeneNamen = new HashMap<>();
        JsonObject namenBlock = root.getAsJsonObject("namen");
        if (namenBlock != null) {
            for (String id : namenBlock.keySet()) {
                JsonElement e = namenBlock.get(id);
                if (e != null && !e.isJsonNull()) gefundeneNamen.put(id, e.getAsString());
            }
        }

        List<Recipe> gelesen = new ArrayList<>(3000);
        for (String ergebnis : liste.keySet()) {
            for (JsonElement element : liste.getAsJsonArray(ergebnis)) {
                if (!element.isJsonObject()) continue;
                JsonObject r = element.getAsJsonObject();
                JsonObject zutaten = r.getAsJsonObject("z");
                if (zutaten == null || zutaten.isEmpty()) continue;
                int menge = r.get("c") == null ? 1 : r.get("c").getAsInt();
                if (menge <= 0) continue;

                String[] ids = new String[zutaten.size()];
                int[] counts = new int[zutaten.size()];
                int i = 0;
                for (String zutat : zutaten.keySet()) {
                    ids[i] = zutat;
                    counts[i] = zutaten.get(zutat).getAsInt();
                    i++;
                }
                gelesen.add(new Recipe(ergebnis, menge, ids, counts));
            }
        }
        if (gelesen.isEmpty()) return false;

        recipes = List.copyOf(gelesen);
        names = Map.copyOf(gefundeneNamen);
        updated = root.get("aktualisiert") == null ? "" : root.get("aktualisiert").getAsString();
        key = root.get("kennung") == null ? "" : root.get("kennung").getAsString();
        origin = from;
        return true;
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
            ShokiMod.LOGGER.warn("[ShokiMod] Could not read cached recipes: {}", e.toString());
        }
    }

    private static void writeCache(String body) {
        try {
            Path path = cachePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not cache recipes: {}", e.toString());
        }
    }
}
