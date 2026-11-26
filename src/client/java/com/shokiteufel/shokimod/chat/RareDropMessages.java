package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import com.shokiteufel.shokimod.data.ModConfig.RareDropMessagesCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Rare Drop Messages: die Meldungen ueber seltene Pets und Buecher werden genauer.
 *
 * <ul>
 * <li><b>Pet Drop Rarity</b>: "PET DROP! Baby Yeti" wird zu "PET DROP! EPIC Baby Yeti" (die Seltenheit steht in der Farbe des Namens, jetzt auch als Wort); dasselbe
 * bei "You caught a [Lvl 1] Squid", "You claimed a Tarantula Pet!", "... has obtained [Lvl 1] Bal" und bei Oringos Liste. Ein "a" davor wird zu "an", wenn das
 * Wort mit einem Vokal beginnt.</li>
 * <li><b>Enchanted Book Name</b>: In "RARE DROP! Enchanted Book" steht der Name der Verzauberung ("Angler VI") statt "Enchanted Book".</li>
 * <li><b>Missing Enchanted Book Message</b>: Kommt ein Slayer-Buch ins Inventar, ohne dass Hypixel eine Zeile schreibt, schreibt die Mod "RARE DROP! Smite VII".</li>
 * </ul>
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code RareDropMessages}). Von dort stammen die Regeln: die fuenf Meldungen mit dem Pet, die Farbe des Namens als
 * Seltenheit (Weiss, Gruen, Blau, Lila, Gold, Rosa ...), die Liste der Slayer-Buecher (Smite VI/VII, Ender Slayer VI/VII, Mana Steal I, Smarty Pants I, Bane of
 * Arthropods VI, Critical VI, Fire Aspect III, Ultimate Reiterate I), das Fenster von einer Sekunde, in dem eine Zeile als "kuerzlich" gilt, und keine Meldung in der
 * Dark Auction, dem Dungeon-Hub, den Catacombs und Kuudra. Die Umsetzung ist eigene Arbeit: Java, die Muster laufen gegen den Klartext, die Seltenheit kommt aus
 * dem Stil des Pet-Namens ({@link StyledText}).
 *
 * <p>Abweichungen: (1) SkyHanni aendert die schon gezeigte Zeile nachtraeglich; das gibt es hier nicht. Deshalb wird die Zeile "RARE DROP! Enchanted Book" zurueckgehalten:
 * Kommt in anderthalb Sekunden ein Buch ins Inventar dazu, steht die Zeile mit dem Namen dieses Buchs da, sonst nach 1,5 Sekunden unveraendert.
 * (2) SkyHanni verlangt fuer den Namen und fuer die fehlende Meldung beide Schalter zugleich (die Pruefung am Anfang von {@code onItemAdd}); hier wirkt jeder Schalter allein.
 * (3) SkyHanni haengt an die fehlende Meldung die "User Luck" an; die gibt es hier nicht.
 */
final class RareDropMessages {

    // ---- Pet Drop Rarity ----

    private static final Pattern[] PET = {
            Pattern.compile("(?<start>PET DROP! )(?<petName>[^(.]+)(?<end>(?: .*)?)"),
            Pattern.compile("(?<start>.? ?(?:GOOD|GREAT|OUTSTANDING) CATCH! You caught a \\[Lvl 1] )(?<petName>[^(.]+)(?<end>.*)"),
            Pattern.compile("(?<start>You claimed a )(?<petName>[^(.]+)(?<end>.*You can manage your Pets.*)"),
            Pattern.compile("(?<start>.*has obtained \\[Lvl 1] )(?<petName>[^(.]+)(?<end>.*)"),
            Pattern.compile("(?<start>\\[NPC] Oringo: ✆ • )(?<petName>[^(.]+)(?<end> Pet)"),
    };

    // ---- Enchanted Book Name ----

    /** "RARE DROP! Enchanted Book (+208% ✯ Magic Find)" - die Klammer ist das Zeichen fuer Magic Find, das Muster nimmt jedes */
    private static final Pattern BOOK = Pattern.compile("RARE DROP! Enchanted Book(?: \\(\\+\\d+%? \\S+ Magic Find\\))?.*");

    private static final Set<String> SLAYER_BOOKS = Set.of("Smite VI", "Smite VII", "Ender Slayer VI", "Ender Slayer VII", "Mana Steal I",
            "Smarty Pants I", "Bane of Arthropods VI", "Critical VI", "Fire Aspect III", "Reiterate I");

    /** In diesen Gebieten gibt es keine Meldung fuer ein Buch, das ins Inventar kommt (es wird dort gekauft oder gehoert zum Lauf) */
    private static final Set<String> IGNORED_AREAS = Set.of("dark auction", "dungeon hub", "catacombs", "kuudra's hollow", "kuudra");

    /** So lange wartet die zurueckgehaltene Zeile auf ihr Buch */
    static final long BOOK_WAIT_MS = 1500L;
    /** So lange gilt eine Zeile "RARE DROP!" als kuerzlich (SkyHanni: eine Sekunde) */
    static final long RECENT_MS = 1000L;
    /** Wie oft das Inventar gelesen wird, solange eine Zeile wartet / nur zum Beobachten */
    static final long SCAN_WAITING_MS = 100L;
    static final long SCAN_WATCHING_MS = 500L;

    private StyledText bookLine = null;
    private long bookAt = 0L;
    private Map<String, Integer> bookBefore = Map.of();

    private Map<String, Integer> lastBooks = null;
    private long lastScan = Long.MIN_VALUE / 2;
    private final List<Long> recentAt = new ArrayList<>();
    private final List<String> recentText = new ArrayList<>();

    void reset() {
        bookLine = null;
        bookBefore = Map.of();
        lastBooks = null;
        recentAt.clear();
        recentText.clear();
    }

    /** Wartet eine Zeile oder ist etwas zu beobachten? Dann muss der Tick des Chats {@link #poll} rufen */
    boolean pending() {
        return bookLine != null;
    }

    // ---- Pet Drop Rarity ----

    /** Die Zeile mit der Seltenheit vor dem Namen, oder null */
    static StyledText petRarity(StyledText cur) {
        String plain = cur.plain();
        if (!mayBePet(plain)) return null;
        for (Pattern p : PET) {
            Matcher m = p.matcher(plain);
            if (!m.matches()) continue;
            int nameAt = m.end("start");
            char code = StyledText.colourCharOf(cur.styleAt(nameAt));
            String rarity = rarityOf(code);
            if (rarity == null) continue;
            StyledText out = cur;
            int at = nameAt;
            // "a EPIC" waere falsch: aus "a " vor dem Namen wird "an ", wenn das Wort mit einem Vokal beginnt
            if (m.group("start").endsWith("a ") && "AEIOU".indexOf(rarity.charAt(0)) >= 0) {
                out = out.insert(nameAt - 1, "n", cur.styleAt(nameAt - 1));
                at++;
            }
            return out.replace(at, at, StyledText.ofLegacy("§" + code + "§l" + rarity + " "));
        }
        return null;
    }

    /** Der billige Blick vor den Mustern: Eines der fuenf Stuecke muss in der Zeile stehen */
    static boolean mayBePet(String plain) {
        return plain.contains("PET DROP!") || plain.contains("You caught a [Lvl 1] ") || plain.startsWith("You claimed a ")
                || plain.contains("has obtained [Lvl 1] ") || plain.startsWith("[NPC] Oringo:");
    }

    /** Das Wort zur Farbe des Namens, oder null, wenn es keine Seltenheit ist */
    static String rarityOf(char colour) {
        switch (colour) {
            case 'f':
                return "COMMON";
            case 'a':
                return "UNCOMMON";
            case '9':
                return "RARE";
            case '5':
                return "EPIC";
            case '6':
                return "LEGENDARY";
            case 'd':
                return "MYTHIC";
            case 'b':
                return "DIVINE";
            case 'c':
                return "SPECIAL";
            case '4':
                return "ULTIMATE";
            default:
                return null;
        }
    }

    // ---- Enchanted Book Name ----

    /** Merkt jede Zeile "RARE DROP!": ein Buch, zu dem so eine Zeile kam, braucht keine eigene */
    void noteRareDrop(String plain, long now) {
        if (!plain.startsWith("RARE DROP!")) return;
        prune(now);
        recentAt.add(now);
        recentText.add(plain);
    }

    private void prune(long now) {
        while (!recentAt.isEmpty() && now - recentAt.get(0) > 3 * RECENT_MS) {
            recentAt.remove(0);
            recentText.remove(0);
        }
    }

    /** Hat Hypixel in der letzten Sekunde eine Zeile geschrieben, die zu diesem Buch gehoert (mit seinem Namen, oder "Enchanted Book")? */
    private boolean recentMessageFor(String bookName, long now) {
        for (int i = recentAt.size() - 1; i >= 0; i--) {
            if (now - recentAt.get(i) > RECENT_MS) break;
            String text = recentText.get(i);
            if (text.contains(bookName) || BOOK.matcher(text).matches()) return true;
        }
        return false;
    }

    /**
     * Die Zeile "RARE DROP! Enchanted Book": ausgeblendet und gemerkt, bis das Buch da ist. Gibt die Zeile zurueck, die dabei frei wird (ein zweites Buch, ehe das
     * erste einen Namen hat: es steht unveraendert da), oder null.
     */
    ChatVerdict book(Incoming in, ChatPlace p, long now) {
        if (!in.plain.startsWith("RARE DROP! Enchanted Book") || !BOOK.matcher(in.plain).matches()) return null;
        List<ChatVerdict.Line> freed = bookLine == null ? List.of() : List.of(ChatVerdict.Line.ofComponent(bookLine.toComponent()));
        bookLine = in.styled();
        bookAt = now;
        bookBefore = new HashMap<>(p.enchantedBooks());
        return freed.isEmpty() ? ChatVerdict.HIDE : new ChatVerdict(ChatVerdict.Kind.REPLACE, freed);
    }

    /**
     * Vom Tick gerufen: liest das Inventar, wenn eine Zeile wartet (oft) oder die fehlende Meldung gefragt ist (selten), und schreibt, was faellig ist: die
     * Zeile mit dem Namen des Buchs, die Zeile unveraendert nach der Wartezeit, die Meldung fuer ein Slayer-Buch ohne Zeile.
     */
    ChatVerdict poll(ChatTabCategory t, ChatPlace p, long now) {
        RareDropMessagesCategory c = t.rareDrop;
        boolean watch = c != null && c.enchantedBookMissingMessage;
        if (bookLine == null && !watch) return ChatVerdict.SHOW;

        List<ChatVerdict.Line> out = new ArrayList<>();
        Map<String, Integer> books = null;
        long gap = bookLine != null ? SCAN_WAITING_MS : SCAN_WATCHING_MS;
        if (now - lastScan >= gap) {
            lastScan = now;
            books = p.enchantedBooks();
        }

        if (bookLine != null) {
            String named = books == null ? null : increased(bookBefore, books);
            if (named != null) {
                out.add(ChatVerdict.Line.ofComponent(withName(bookLine, named).toComponent()));
                bookLine = null;
            } else if (now - bookAt >= BOOK_WAIT_MS) {
                out.add(ChatVerdict.Line.ofComponent(bookLine.toComponent()));
                bookLine = null;
            }
        }

        if (watch && books != null) {
            if (p.menuOpen() || !p.skyblock() || ignoredArea(p.area())) {
                // Was in einem Fenster oder an einem solchen Ort ins Inventar kommt, ist kein Drop: nur den Stand nachfuehren
                lastBooks = books;
            } else {
                if (lastBooks != null) {
                    for (Map.Entry<String, Integer> e : books.entrySet()) {
                        int before = lastBooks.getOrDefault(e.getKey(), 0);
                        if (e.getValue() <= before) continue;
                        String plainName = plainName(e.getKey());
                        if (!isSlayerBook(plainName)) continue;
                        if (recentMessageFor(plainName, now)) continue;
                        out.add(ChatVerdict.Line.of("§r§6§lRARE DROP! " + e.getKey()));
                    }
                }
                lastBooks = books;
            }
        }
        return out.isEmpty() ? ChatVerdict.SHOW : new ChatVerdict(ChatVerdict.Kind.REPLACE, out);
    }

    /** Das Buch, von dem jetzt mehr im Inventar liegen als zu Beginn, oder null */
    static String increased(Map<String, Integer> before, Map<String, Integer> now) {
        for (Map.Entry<String, Integer> e : now.entrySet()) {
            if (e.getValue() > before.getOrDefault(e.getKey(), 0)) return e.getKey();
        }
        return null;
    }

    /** Die Zeile mit "Enchanted Book" durch den Namen (mit Farbcodes) ersetzt; steht das Wort nicht darin, bleibt sie, wie sie ist */
    static StyledText withName(StyledText line, String legacyName) {
        int at = line.plain().indexOf("Enchanted Book");
        if (at < 0) return line;
        return line.replace(at, at + "Enchanted Book".length(), StyledText.ofLegacy(legacyName));
    }

    private static boolean ignoredArea(String area) {
        return area != null && IGNORED_AREAS.contains(area.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static String plainName(String legacyName) {
        return legacyName.replaceAll("§.", "").trim();
    }

    /** Ist es eines der Slayer-Buecher? Das Wort "Ultimate" davor zaehlt nicht */
    static boolean isSlayerBook(String plainName) {
        String n = plainName.startsWith("Ultimate ") ? plainName.substring("Ultimate ".length()) : plainName;
        return SLAYER_BOOKS.contains(n);
    }
}
