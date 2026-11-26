package com.shokiteufel.shokimod.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Compact Jacob Claim (Garden): Wer im Menue von Jacob alle Preise des Farming Contests auf einmal holt, bekommt von Hypixel einen Block mit einer Zeile
 * je Wettbewerb und je Preis. Hier bleibt eine Zeile: "Contest Rewards | Tickets: 271 Ja | Books: 1 Sf | Medals: 8 | Bits: 2293", der ganze Block steht beim Zeigen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CompactJacobClaim}). Von dort stammt die Regel: Die Zeile "FARMING CONTEST REWARDS CLAIMED" beginnt, was danach
 * kommt (die Zeilen der Wettbewerbe, "REWARDS" und jede Preiszeile, die zaehlt) wird ausgeblendet und gezaehlt; die Zeile mit den Bits ist immer die letzte, sonst
 * gilt der Block 300 Millisekunden nach der letzten Zeile als zu Ende. Die Zeile nennt Tickets (Jacob's und Carnival), Buecher (Turbo-<Pflanze> I), Medaillen, Bits
 * und Overclocker, in dieser Reihenfolge, mit "|" dazwischen. Die Umsetzung ist eigene Arbeit: Java, die Uhr ist der Tick des Chats ({@code poll}) statt einer
 * verzoegerten Aufgabe, die Tabelle der Pflanzen (Kurzname, Farbe) ist eigen, und wie beim Rest von Chat (Teil 2) werden alle Muster gegen den Klartext der Zeile
 * geprueft.
 */
final class CompactJacobClaim {

    /** Der Block ist so lange nach der letzten Zeile zu Ende (die Bits sind es sofort) */
    static final long QUIET_MS = 300L;

    private static final String OPENING = "  FARMING CONTEST REWARDS CLAIMED";
    private static final String REWARDS_HEADER = "  REWARDS";
    private static final Pattern CONTEST = Pattern.compile(" {3}» (?<crop>[\\w ]+) Contest on (?<season>[\\w ]+) (?<day>\\d+[stndh]{2,3}), Year (?<year>\\d+)");
    private static final Pattern TICKET = Pattern.compile(" {4}(?<type>Jacob's|Carnival) Ticket(?: x(?<amount>[\\d,]+))?");
    private static final Pattern BOOK = Pattern.compile(" {4}(?<amount>[\\d,]+)x Turbo-(?<crop>[^ ]+) I Book");
    private static final Pattern MEDALS = Pattern.compile(" {4}\\+(?<amount>[\\d,]+) (?<type>[^ ]+) medals?");
    private static final Pattern BITS = Pattern.compile(" {4}\\+(?<amount>[\\d,]+) Bits");
    private static final Pattern OVERCLOCKER = Pattern.compile(" {4}Overclocker 3000(?: x(?<count>\\d+))?");

    /** Eine Pflanze: wie sie in der Zeile heisst (klein geschrieben), ihr Kurzname und der Farbcode */
    enum Crop {
        WHEAT("Wh", '6', "wheat", "seeds"),
        CARROT("Ca", '6', "carrot"),
        POTATO("Po", '6', "potato"),
        NETHER_WART("Ne", 'c', "nether wart", "wart", "warts"),
        PUMPKIN("Pu", '6', "pumpkin"),
        MELON("Me", 'a', "melon slice", "melon"),
        COCOA_BEANS("Co", '6', "cocoa beans", "cocoa", "coco"),
        SUGAR_CANE("Su", 'a', "sugar cane", "cane"),
        CACTUS("Ca", 'a', "cactus", "cacti"),
        MUSHROOM("Mu", 'c', "mushroom", "mushrooms", "red mushroom", "brown mushroom"),
        SUNFLOWER("Sf", '6', "sunflower"),
        MOONFLOWER("Mf", 'b', "moonflower"),
        WILD_ROSE("Wr", 'c', "wild rose", "rose");

        final String shortName;
        final char colour;
        private final String[] names;

        Crop(String shortName, char colour, String... names) {
            this.shortName = shortName;
            this.colour = colour;
            this.names = names;
        }

        static Crop byName(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            for (Crop c : values()) {
                for (String candidate : c.names) {
                    if (candidate.equals(n)) return c;
                }
            }
            return null;
        }
    }

    /** Eine Medaille: wie sie in der Zeile heisst und ihr Farbcode (die Reihenfolge ist die der Zeile: Gold, Silber, Bronze) */
    enum Medal {
        GOLD("gold", '6'),
        SILVER("silver", 'f'),
        BRONZE("bronze", 'c');

        final String word;
        final char colour;

        Medal(String word, char colour) {
            this.word = word;
            this.colour = colour;
        }

        static Medal byName(String name) {
            for (Medal m : values()) {
                if (m.word.equals(name)) return m;
            }
            return null;
        }
    }

    private boolean inLoop = false;
    private long lastLineAt = 0L;
    private int jacobTickets = 0;
    private int carnivalTickets = 0;
    private int overclockers = 0;
    private int bits = 0;
    private final Map<Crop, Integer> books = new EnumMap<>(Crop.class);
    private final Map<Medal, Integer> medals = new EnumMap<>(Medal.class);
    private final List<String> messages = new ArrayList<>();

    void reset() {
        inLoop = false;
        jacobTickets = 0;
        carnivalTickets = 0;
        overclockers = 0;
        bits = 0;
        books.clear();
        medals.clear();
        messages.clear();
    }

    boolean pending() {
        return inLoop;
    }

    /** Eine Zeile; null heisst, der Block hat dazu nichts zu sagen (die Zeile bleibt stehen) */
    ChatVerdict line(Incoming in, long now) {
        String plain = in.plain;
        if (!inLoop && !plain.equals(OPENING)) return null;

        if (plain.equals(OPENING)) {
            // Ein neuer Block, ehe der alte zu Ende ist: den alten zuerst schreiben
            List<ChatVerdict.Line> before = inLoop ? publish() : List.of();
            reset();
            inLoop = true;
            lastLineAt = now;
            messages.add(in.styled().legacy());
            return before.isEmpty() ? ChatVerdict.HIDE : new ChatVerdict(ChatVerdict.Kind.REPLACE, before);
        }
        lastLineAt = now;

        if (plain.equals(REWARDS_HEADER) || CONTEST.matcher(plain).matches()) {
            messages.add(in.styled().legacy());
            return ChatVerdict.HIDE;
        }
        boolean changed = false;
        boolean last = false;
        Matcher m = TICKET.matcher(plain);
        if (m.matches()) {
            int amount = m.group("amount") == null ? 1 : number(m.group("amount"));
            if (m.group("type").equals("Jacob's")) jacobTickets += amount;
            else carnivalTickets += amount;
            changed = true;
        }
        if (!changed && (m = OVERCLOCKER.matcher(plain)).matches()) {
            overclockers += m.group("count") == null ? 1 : number(m.group("count"));
            changed = true;
        }
        if (!changed && (m = BOOK.matcher(plain)).matches()) {
            Crop crop = Crop.byName(m.group("crop"));
            if (crop != null) {
                books.merge(crop, number(m.group("amount")), Integer::sum);
                changed = true;
            }
        }
        if (!changed && (m = MEDALS.matcher(plain)).matches()) {
            Medal medal = Medal.byName(m.group("type"));
            if (medal != null) {
                medals.merge(medal, number(m.group("amount")), Integer::sum);
                changed = true;
            }
        }
        if (!changed && (m = BITS.matcher(plain)).matches()) {
            bits += number(m.group("amount"));
            changed = true;
            // Die Bits sind immer der letzte Preis: Mehr kommt nicht
            last = true;
        }
        if (!changed) return null;
        messages.add(in.styled().legacy());
        if (last) return new ChatVerdict(ChatVerdict.Kind.REPLACE, publish());
        return ChatVerdict.HIDE;
    }

    /** Ist der Block zu Ende, weil lange nichts mehr kam? Vom Tick gerufen */
    ChatVerdict poll(long now) {
        if (!inLoop || now - lastLineAt < QUIET_MS) return ChatVerdict.SHOW;
        return new ChatVerdict(ChatVerdict.Kind.REPLACE, publish());
    }

    /** Die Zeile mit allen Preisen; der Zustand ist danach leer */
    private List<ChatVerdict.Line> publish() {
        String text = compactText();
        StyledText line = StyledText.ofLegacy(text).withEvents(null, StyledText.hoverOf(String.join("\n", messages)));
        reset();
        return List.of(ChatVerdict.Line.ofComponent(line.toComponent()));
    }

    /** Der Text der Zeile mit Farbcodes (ohne den Text beim Zeigen) */
    String compactText() {
        List<String> parts = new ArrayList<>();
        parts.add("§6§lContest Rewards");
        if (jacobTickets > 0 || carnivalTickets > 0) {
            List<String> tickets = new ArrayList<>();
            if (jacobTickets > 0) tickets.add("§a" + jacobTickets + " Ja");
            if (carnivalTickets > 0) tickets.add("§a" + carnivalTickets + " Ca");
            parts.add("Tickets: " + String.join("§7, ", tickets));
        }
        List<String> bookList = new ArrayList<>();
        for (Map.Entry<Crop, Integer> e : books.entrySet()) {
            if (e.getValue() > 0) bookList.add("§" + e.getKey().colour + e.getValue() + " " + e.getKey().shortName);
        }
        if (!bookList.isEmpty()) {
            bookList.sort(Comparator.comparing(CompactJacobClaim::withoutColours));
            parts.add("Books: " + String.join("§7, ", bookList));
        }
        List<String> medalList = new ArrayList<>();
        for (Map.Entry<Medal, Integer> e : medals.entrySet()) {
            if (e.getValue() > 0) medalList.add("§" + e.getKey().colour + e.getValue());
        }
        if (!medalList.isEmpty()) parts.add("Medals: " + String.join("§7, ", medalList));
        if (bits > 0) parts.add("Bits: §b" + bits);
        if (overclockers > 0) parts.add((overclockers == 1 ? "Overclocker" : "Overclockers") + ": §6" + overclockers);
        return String.join(" §8§l| §r", parts);
    }

    private static String withoutColours(String s) {
        return s.replaceAll("§.", "");
    }

    private static int number(String digits) {
        try {
            return Integer.parseInt(digits.replace(",", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
