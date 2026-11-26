package com.shokiteufel.shokimod.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Die Regeln, nach denen die vier Equipment-Plaetze aus den Fenstern von SkyBlock gelesen werden - ohne Spieltypen, damit sie sich ohne Spiel pruefen lassen.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyBlockEquipmentUpdater}). Von dort stammt die Regel: Gelesen wird in drei Fenstern - "Stats & Equipment",
 * "Loadouts" (auch mit Seitenzahl davor, "(1/2) Loadouts") und "Equipment Sets" (auch mit Seitenzahl). In den ersten beiden stehen die vier Gegenstaende in der
 * zweiten Spalte (Platz 1 + 9 * Reihe); schwarze Glasscheiben sind Fuellung und zaehlen nicht, die ersten vier Plaetze danach sind Halskette, Umhang, Guertel und
 * Handschuhe. Ein Platz, dessen Name mit "Empty" oder "Slot " beginnt, ist leer. Im Fenster "Equipment Sets" steht ein Satz je Spalte; der gewaehlte hat in der
 * untersten Reihe (Plaetze 36 bis 44) einen gruenen Farbstoff, und aus seiner Spalte wird gelesen. Die Umsetzung ist eigene Arbeit: Java, die Zellen des Fensters
 * kommen als kleine Werte herein ({@link Cell}), und was gelesen wird, kommt als Liste von Plaetzen heraus ({@link Pick}); ein Fenster, in dem noch nichts steht
 * (der Server hat die Plaetze noch nicht geschickt), wird nie gelesen.
 */
public final class EquipmentRules {

    /** So viele Plaetze hat das Equipment: Halskette, Umhang, Guertel, Handschuhe oder Armband */
    public static final int SLOTS = 4;

    /** Wie die Plaetze heissen (fuer den Hinweis auf einem leeren Platz) */
    public static final String[] SLOT_NAMES = {"Necklace", "Cloak", "Belt", "Gloves / Bracelet"};

    /** In welcher Spalte der beiden Fenster mit festem Platz die Gegenstaende stehen (die zweite, von null an gezaehlt) */
    static final int FIXED_COLUMN = 1;

    private static final Pattern STATS = Pattern.compile("^Stats & Equipment$");
    private static final Pattern SETS = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Equipment Sets$");
    private static final Pattern LOADOUTS = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Loadouts$");
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");

    private EquipmentRules() {
    }

    /** Welches Fenster ist es - eines, aus dem die Mod liest, oder irgendeines */
    public enum Window {
        NONE,
        /** "Stats & Equipment": das Menue von /equipment und /stats */
        STATS,
        /** "Equipment Sets": der Kleiderschrank fuer Equipment */
        SETS,
        /** "Loadouts" */
        LOADOUTS
    }

    /** Was auf einem Platz liegt, soweit es fuer die Regeln zaehlt */
    public enum Kind {
        /** nichts */
        AIR,
        /** eine schwarze Glasscheibe: Fuellung */
        BLACK_PANE,
        /** ein gruener Farbstoff: im Kleiderschrank das Zeichen fuer den gewaehlten Satz */
        LIME_DYE,
        /** alles andere: ein Gegenstand oder ein Platzhalter */
        OTHER
    }

    /** Ein Platz des Fensters (ohne den eigenen Rucksack): seine Nummer, was darauf liegt und sein Name in Kleinbuchstaben ohne Farbcodes */
    public record Cell(int index, Kind kind, String name) {
    }

    /** Ein gelesener Equipment-Platz: wo er im Fenster liegt und ob dort nur der Platzhalter ("Empty Necklace Slot") steht */
    public record Pick(int index, boolean empty) {
    }

    /** Eine Zelle aus dem, was das Fenster hergibt: der Name wird klein geschrieben, von Farbcodes und Leerraum befreit */
    public static Cell cell(int index, Kind kind, String hoverName) {
        String name = hoverName == null ? "" : COLOUR_CODE.matcher(hoverName).replaceAll("").trim().toLowerCase(Locale.ENGLISH);
        return new Cell(index, kind, name);
    }

    /** Aus welchem Fenster liest die Mod? Der Titel darf Farbcodes tragen */
    public static Window windowOf(String title) {
        if (title == null) return Window.NONE;
        String clean = COLOUR_CODE.matcher(title).replaceAll("").trim();
        if (STATS.matcher(clean).matches()) return Window.STATS;
        if (SETS.matcher(clean).matches()) return Window.SETS;
        if (LOADOUTS.matcher(clean).matches()) return Window.LOADOUTS;
        return Window.NONE;
    }

    /** Steht auf diesem Platz nur ein Platzhalter statt eines Gegenstandes? ("Empty Cloak Slot", "Slot 3") - der Name ist klein geschrieben (siehe {@link #cell}) */
    public static boolean isPlaceholderName(String lowerCaseName) {
        if (lowerCaseName == null) return false;
        String name = lowerCaseName.trim();
        return name.startsWith("empty") || name.startsWith("slot ");
    }

    /** Hat der Server das Fenster schon gefuellt? Solange alle Plaetze leer sind, gibt es nichts zu lesen - sonst wuerde das Gemerkte mit vier leeren Plaetzen ueberschrieben */
    public static boolean isFilled(List<Cell> cells) {
        if (cells == null) return false;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).kind() != Kind.AIR) return true;
        }
        return false;
    }

    /**
     * Die Spalte, in der die Gegenstaende stehen: in "Stats & Equipment" und "Loadouts" die zweite, im Kleiderschrank die des gewaehlten Satzes.
     *
     * @return die Spalte (0 bis 8), oder -1, wenn es keine gibt (kein Fenster, in dem gelesen wird, oder im Kleiderschrank noch kein Satz gewaehlt)
     */
    public static int columnOf(Window window, List<Cell> cells) {
        if (window == null) return -1;
        return switch (window) {
            case STATS, LOADOUTS -> FIXED_COLUMN;
            case SETS -> selectedSetColumn(cells);
            case NONE -> -1;
        };
    }

    /** Im Kleiderschrank: Der gewaehlte Satz hat einen gruenen Farbstoff in der untersten Reihe (Plaetze 36 bis 44) - seine Spalte, sonst -1 */
    static int selectedSetColumn(List<Cell> cells) {
        if (cells == null) return -1;
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            if (cell.kind() == Kind.LIME_DYE && isSetRow(cell.index())) return cell.index() % 9;
        }
        return -1;
    }

    /** Liegt dieser Platz in der untersten Reihe des Kleiderschranks, wo die Zeichen der Saetze stehen (36 bis 44)? */
    public static boolean isSetRow(int index) {
        return index > 35 && index < 45;
    }

    /**
     * Die vier Plaetze der Spalte: die ersten vier ohne schwarze Glasscheibe, von oben nach unten.
     *
     * @return die vier Plaetze, oder null, wenn nichts zu lesen ist: das Fenster ist noch leer, die Spalte unbekannt oder es gibt weniger als vier Plaetze darin
     */
    public static List<Pick> equipmentSlots(List<Cell> cells, int column) {
        if (cells == null || column < 0 || column > 8 || !isFilled(cells)) return null;
        List<Pick> picks = new ArrayList<>(SLOTS);
        for (int i = 0; i < cells.size() && picks.size() < SLOTS; i++) {
            Cell cell = cells.get(i);
            if (cell.index() % 9 != column || cell.kind() == Kind.BLACK_PANE) continue;
            picks.add(new Pick(cell.index(), cell.kind() == Kind.AIR || isPlaceholderName(cell.name())));
        }
        return picks.size() == SLOTS ? picks : null;
    }

    /**
     * Ein Klick im Kleiderschrank auf das Zeichen des gewaehlten Satzes (gruener Farbstoff, unterste Reihe) nimmt den Satz ab: Dann ist nichts mehr angelegt.
     * Hier nur die Frage, ob es dieser Klick ist.
     */
    public static boolean takesSetOff(Window window, int index, Kind kind) {
        return window == Window.SETS && kind == Kind.LIME_DYE && isSetRow(index);
    }

    /**
     * Welcher Befehl kommt bei einem Klick auf einen Platz: {@code /equipment}, das Menue mit dem Kleiderschrank - in der Rift und in der Safari gibt es den
     * Kleiderschrank nicht, dort {@code /stats}. Ohne den Schraegstrich, wie ihn {@code sendCommand} will.
     */
    public static String clickCommand(EquipmentSet set) {
        return set == EquipmentSet.NORMAL ? "equipment" : "stats";
    }
}
