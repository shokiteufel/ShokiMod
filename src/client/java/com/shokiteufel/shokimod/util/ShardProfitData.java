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

    /**
     * So oft wird nach neuen Preisen gesehen.
     *
     * Fuenfzehn Sekunden klingen nach viel fuer eine Datei, die alle paar Minuten neu
     * entsteht - kosten aber fast nichts: Beim Nachfragen geht die Kennung der zuletzt
     * geholten Fassung mit, und solange sich nichts geaendert hat, antwortet GitHub mit
     * "unveraendert" und schickt keine Daten. Gezahlt wird also nur, wenn es wirklich
     * etwas Neues gibt.
     */
    private static final long REFRESH_MILLIS = 15 * 1000L;
    private static final long RETRY_MILLIS = 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    /** Was ein Shard kostet, bringt und wie er heisst */
    private record Shard(String name, String bazaarId, String category, String rarity,
                         int fuseAmount, long instantBuy, long instantSell,
                         int volume, String area) {

        /**
         * Der Preis, der gerade gilt - lieber der frische als der gespeicherte.
         *
         * Liegt ein direkt bei Hypixel geholter Stand vor, zaehlt der: Er ist Sekunden
         * alt statt Minuten. Fehlt er - weil niemand hinsieht oder die Verbindung
         * klemmt -, gilt weiter, was von GitHub kam. Eine Zahl von vor fuenf Minuten
         * ist immer noch besser als keine.
         */
        long buyNow() {
            long[] live = BazaarLive.priceOf(bazaarId);
            return live != null && live[0] > 0 ? live[0] : instantBuy;
        }

        long sellNow() {
            long[] live = BazaarLive.priceOf(bazaarId);
            return live != null && live[1] > 0 ? live[1] : instantSell;
        }
    }

    /**
     * Eine Fusion, fertig zum Anzeigen.
     *
     * Entsteht erst beim Filtern und nur fuer die Treffer - alle 128.000 auf einmal
     * als Objekte waeren Verschwendung.
     */
    public record Row(String result, String resultBazaarId,
                      String first, String second, int amount,
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
    /** Die Kennung der zuletzt geholten Preisdatei - damit GitHub schweigen darf */
    private static volatile String priceTag = "";
    private static volatile long unchangedCount = 0L;

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

    /**
     * Kennt die Fusionsliste diesen Namen als Shard?
     *
     * In der Hunting Box liegen nicht nur Shards, sondern auch Ausruestung. Wer alles
     * zaehlt, was dort steht, bekommt "Fabled Flaming Flay" als Sorte gemeldet und
     * eine Statistik, die niemandem hilft.
     */
    public static boolean isShard(String name) {
        if (name == null || name.isBlank() || !ready()) return false;
        String gesucht = normalize(name);
        for (Shard s : shards) {
            if (s != null && normalize(s.name()).equals(gesucht)) return true;
        }
        return false;
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
    public static List<Row> select(String category, String search, boolean searchInputsOnly,
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
            if (!suche.isEmpty()) {
                boolean inZutat = a.name().toLowerCase(Locale.ROOT).contains(suche)
                        || b.name().toLowerCase(Locale.ROOT).contains(suche);
                // Wer nur die Zutat sucht, fragt "was kann ich daraus machen" und will
                // die Fusionen nicht sehen, die den Shard erst herstellen
                boolean treffer = searchInputsOnly
                        ? inZutat
                        : inZutat || ziel.name().toLowerCase(Locale.ROOT).contains(suche);
                if (!treffer) continue;
            }
            if (owned != null && !hasEnough(owned, a, b)) continue;

            // Ein Preis von null heisst nicht "kostenlos", sondern "dafuer gibt es
            // gerade kein Angebot". Wer das als Zahl nimmt, bekommt eine Fusion ohne
            // Kosten an die Spitze gereiht - das schoenste Geschaeft des Tages, und
            // keines, das sich machen laesst. Solche Zeilen fallen weg
            long preisA = instantBuy ? a.buyNow() : a.sellNow();
            long preisB = instantBuy ? b.buyNow() : b.sellNow();
            long preisZiel = instantSell ? ziel.sellNow() : ziel.buyNow();
            if (preisA <= 0 || preisB <= 0 || preisZiel <= 0) continue;

            int menge = outAmount[i];
            long kosten = (long) a.fuseAmount() * preisA + (long) b.fuseAmount() * preisB;
            long erloes = (long) menge * preisZiel;
            if (onlyGain && erloes - kosten <= 0) continue;

            // Dieselben Zahlen in die Zeile, mit denen eben gerechnet wurde. Nimmt
            // man hier die gespeicherten, zeigt die Liste andere Kosten an, als sie
            // zum Reihen benutzt hat - und niemand kaeme darauf, warum
            out.add(new Row(ziel.name(), ziel.bazaarId(), a.name(), b.name(), menge,
                    a.fuseAmount(), b.fuseAmount(),
                    ziel.rarity(), ziel.category(), ziel.area(),
                    a.buyNow(), a.sellNow(),
                    b.buyNow(), b.sellNow(),
                    ziel.sellNow(), ziel.buyNow(), ziel.volume()));
        }

        out.sort((x, y) -> Long.compare(y.profit(instantBuy, instantSell),
                                        x.profit(instantBuy, instantSell)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /**
     * Warum kommt mit dem Bestand nichts durch?
     *
     * Eine leere Liste hat drei moegliche Gruende, und sie verlangen verschiedene
     * Antworten: Der Vorrat kennt die Shards nicht (Namen passen nicht), er kennt sie
     * und hat zu wenige (eine Fusion nimmt meist fuenf je Zutat), oder es fehlt
     * schlicht der Partner. Von aussen sehen alle drei gleich aus - deshalb wird hier
     * gezaehlt, statt den Betrachter raten zu lassen.
     *
     * @return ein Satz fuer das Fenster, oder leer wenn es nichts zu erklaeren gibt
     */
    public static String explainEmpty(Map<String, Integer> owned) {
        if (!ready() || owned == null || owned.isEmpty()) return "";

        Shard[] tabelle = shards;
        int bekannt = 0;
        int reichtEinzeln = 0;
        long beideDa = 0;
        long knapp = 0;

        // Erst: Wie viele der gelagerten Sorten kennt die Fusionsliste ueberhaupt,
        // und von wie vielen liegen genug fuer eine Fusion
        for (Shard s : tabelle) {
            if (s == null) continue;
            int habe = have(owned, s.name());
            if (habe <= 0) continue;
            bekannt++;
            if (habe >= s.fuseAmount()) reichtEinzeln++;
        }

        // Dann: Wie viele Kombinationen scheitern woran
        for (int i = 0; i < count; i++) {
            Shard a = tabelle[firstIndex[i]];
            Shard b = tabelle[secondIndex[i]];
            if (a == null || b == null) continue;
            int ha = have(owned, a.name());
            int hb = have(owned, b.name());
            if (ha <= 0 || hb <= 0) continue;
            beideDa++;
            if (!hasEnough(owned, a, b)) knapp++;
        }

        if (bekannt == 0) {
            return "None of your shards appear in the fusion list - the names do not match";
        }
        if (beideDa == 0) {
            return bekannt + " of your shards are known, but no fusion uses two of them together";
        }
        if (knapp == beideDa) {
            return beideDa + " fusions use shards you own, but you need more of them - "
                    + "most take 5 of each (" + reichtEinzeln + " of " + bekannt
                    + " kinds reach that)";
        }
        return "";
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
        Integer n = owned.get(normalize(name));
        return n == null ? 0 : n;
    }

    /**
     * Ein Shard-Name in der Form, in der beide Seiten ihn wiedererkennen.
     *
     * Im Spiel heisst das Feld "Abyssal Miner Shard", die Fusionsdaten fuehren
     * "Abyssal Miner". Wer die beiden roh vergleicht, findet nie etwas - und genau
     * das war zu sehen: sechsundvierzig Sorten in der Box, null Treffer in der Liste.
     * Abgeschnitten wird deshalb ein angehaengtes "Shard", und alles, was nicht
     * Buchstabe oder Ziffer ist, faellt weg: Hypixel setzt vor manche Namen ein
     * Symbol, und ein Bindestrich hier gegen ein Leerzeichen dort hat schon genug
     * Abgleiche zerlegt.
     */
    public static String normalize(String name) {
        if (name == null) return "";
        String text = name.toLowerCase(Locale.ROOT).trim();
        if (text.endsWith(" shard")) text = text.substring(0, text.length() - 6);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.toString();
    }

    /**
     * Wie viele der gezaehlten Sorten sich einem Shard zuordnen lassen.
     *
     * Die Zahl trennt zwei Faelle, die von aussen gleich aussehen: "die Box ist leer"
     * und "die Namen passen nicht zusammen". Ohne sie bliebe nur Raten.
     */
    public static int matched(Map<String, Integer> owned) {
        if (owned == null || owned.isEmpty() || !ready()) return 0;
        Set<String> bekannt = new java.util.HashSet<>();
        for (Shard s : shards) {
            if (s != null) bekannt.add(normalize(s.name()));
        }
        int n = 0;
        for (String name : owned.keySet()) {
            if (bekannt.contains(name)) n++;
        }
        return n;
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
               .append(", prices ").append(updated)
               .append(", ").append(unchangedCount).append(" unchanged replies")
               .append(BazaarLive.fresh()
                       ? ", live prices " + BazaarLive.ageSeconds() + "s old"
                       : ", no live prices");
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
            String preise = get(client, PRICES_URL, priceTag);
            if (preise == null) {
                // Unveraendert oder ein Fehler - beides ist kein Grund, den alten
                // Stand wegzuwerfen
                if (lastError == null) succeededAt = System.currentTimeMillis();
                return;
            }

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
        return get(client, url, null);
    }

    /**
     * Eine Datei holen - und nur dann, wenn sie sich geaendert hat.
     *
     * Geht eine Kennung mit, antwortet GitHub bei unveraendertem Inhalt mit 304 und
     * schickt keine Daten. Das macht haeufiges Nachsehen praktisch kostenlos.
     */
    private static String get(HttpClient client, String url, String tag)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "ShokiMod")
                .timeout(TIMEOUT)
                .GET();
        if (tag != null && !tag.isEmpty()) builder.header("If-None-Match", tag);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 304) {
            unchangedCount++;
            return null;
        }
        if (response.statusCode() != 200) {
            fail("HTTP " + response.statusCode() + " for " + url);
            return null;
        }
        if (url.equals(PRICES_URL)) {
            priceTag = response.headers().firstValue("ETag").orElse("");
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
                    string(o, "i"),
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
