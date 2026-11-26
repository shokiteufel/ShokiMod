package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.TextCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Ein Kasten mit Zeilen, wie ihn crittermod fuer seine beiden Anzeigen benutzt.
 *
 * Vier Zeilenarten reichen aus: Ueberschrift, freier Text, Beschriftung mit Wert
 * rechtsbuendig, und ein Fortschrittsbalken. Der Kasten misst seine Breite selbst
 * am laengsten Inhalt, damit nichts abgeschnitten wird und nichts flattert.
 *
 * <p>Fettdruck (HUD > Bold numbers): Ein Aufrufer markiert eine Zeile mit {@link #boldValue()} (oder ein Stueck einer Segment-Zeile mit
 * {@link Segment#withNumber}), dann zeichnet und MISST der Kasten den Wert fett. Der gespeicherte Text der Zeile bleibt dabei unveraendert;
 * der fette Text entsteht beim Ausmessen einmal je Kasten ({@link #measure}) und wird danach nur noch gezeichnet. Weil Messen und Zeichnen
 * denselben fetten Text benutzen (ein fettes Zeichen ist einen Punkt breiter), stimmen Kastenbreite und die rechtsbuendigen Spalten.
 */
public class HudPanel {

    private static final int PADDING = 4;

    /** Der Abstand vom Rand des Kastens zur Schrift - wer Klicks zuordnet, braucht ihn */
    public static int padding() {
        return PADDING;
    }
    /** Abstand zwischen Beschriftung und Wert in einer Paar-Zeile */
    private static final int GUTTER = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_WIDTH = 60;
    /**
     * Kraeftig deckend, nicht bloss angedeutet.
     *
     * Bei halbdurchsichtigem Grund scheint die Welt durch und frisst den Kontrast -
     * ueber hellem Gelaende wirkt derselbe Kasten dann matt.
     *
     * Voll deckend, damit die Schrift ueberall gleich aussieht: bleibt ein Rest
     * Durchsicht, faerbt heller Untergrund die Schrift matt, und derselbe Kasten
     * wirkt ueber Laub anders als in einer Hoehle.
     */
    private static final int BACKGROUND = 0xFF101010;
    /** Feine helle Kante oben, damit der Kasten eine Form hat statt zu verlaufen */
    private static final int TOP_EDGE = 0x50FFFFFF;

    private enum Kind { TITLE, TEXT, PAIR, BAR, BLANK, ICON, TABLE, SEGMENTS, ALIGNED, SUBICON }

    /**
     * Ein Stueck einer Segment-Zeile: ein Text in einer eigenen Farbe.
     *
     * @param boldFrom Anfang der Zahl im Text, die bei "Bold numbers" fett wird (einschliesslich)
     * @param boldTo   Ende der Zahl (ausschliesslich); gleich oder kleiner als boldFrom: nichts wird fett
     */
    public record Segment(String text, int colour, int boldFrom, int boldTo) {

        /** Ohne fette Zahl */
        public Segment(String text, int colour) {
            this(text, colour, 0, 0);
        }

        /** "Total: " + "123" + " 4%": der Text ist alles zusammen, fett wird nur die Zahl in der Mitte */
        public static Segment withNumber(String before, String number, String after, int colour) {
            return new Segment(before + number + after, colour, before.length(), before.length() + number.length());
        }

        /**
         * Der Text, wie er gezeichnet und gemessen wird. Ohne Fettdruck genau {@link #text}; mit Fettdruck steht "§l" vor der Zahl und "§r"
         * dahinter (das Zurueck setzt die Farbe des Stuecks wieder ein, denn die kommt vom Zeichnen, nicht aus dem Text).
         */
        String shown(boolean bold) {
            if (!bold || boldFrom < 0 || boldTo <= boldFrom || boldTo > text.length()) return text;
            String head = text.substring(0, boldFrom) + "§l" + text.substring(boldFrom, boldTo);
            return boldTo == text.length() ? head : head + "§r" + text.substring(boldTo);
        }
    }

    /** Zwischen zwei Segmenten einer Zeile */
    private static final String SEGMENT_SEPARATOR = " | ";
    private static final int SEPARATOR_COLOUR = 0xFFAAAAAA;

    private record Row(Kind kind, String label, String value,
                       int labelColour, int valueColour, int current, int max,
                       net.minecraft.world.item.ItemStack icon, String extra,
                       List<Segment> parts, float scale, boolean bold) {

        /** Ohne Fettdruck */
        Row(Kind kind, String label, String value, int labelColour, int valueColour, int current, int max,
            net.minecraft.world.item.ItemStack icon, String extra, List<Segment> parts, float scale) {
            this(kind, label, value, labelColour, valueColour, current, max, icon, extra, parts, scale, false);
        }

        /** Dieselbe Zeile mit fettem Wert */
        Row withBold() {
            return new Row(kind, label, value, labelColour, valueColour, current, max, icon, extra, parts, scale, true);
        }

        /** Ohne Segmente */
        Row(Kind kind, String label, String value, int labelColour, int valueColour, int current, int max,
            net.minecraft.world.item.ItemStack icon, String extra) {
            this(kind, label, value, labelColour, valueColour, current, max, icon, extra, List.of(), 1.0f);
        }

        /** Ohne zweite Spalte */
        Row(Kind kind, String label, String value, int labelColour, int valueColour, int current, int max,
            net.minecraft.world.item.ItemStack icon) {
            this(kind, label, value, labelColour, valueColour, current, max, icon, "");
        }

        /** Alle Zeilen ausser der Bild-Zeile kommen ohne Gegenstand aus */
        Row(Kind kind, String label, String value, int labelColour, int valueColour, int current, int max) {
            this(kind, label, value, labelColour, valueColour, current, max,
                 net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** Kantenlaenge des Gegenstandsbildes; es ist hoeher als eine gewoehnliche Zeile */
    private static final int ICON_SIZE = 16;

    /**
     * Wie gross das Bild gezeichnet wird, im Verhaeltnis zu den sechzehn Pixeln.
     *
     * Steht hier, weil Hoehe und Breite des Kastens davon abhaengen - wer nur das Bild
     * groesser stellt, soll keinen abgeschnittenen Kasten bekommen.
     */
    private float iconScale = 1.0f;

    public HudPanel iconScale(float scale) {
        this.iconScale = Math.max(0.25f, Math.min(4.0f, scale));
        measuredWidth = -1;   // die Breite muss neu ausgemessen werden
        return this;
    }

    private int iconSize() {
        return Math.max(4, Math.round(ICON_SIZE * iconScale));
    }

    private final List<Row> rows = new ArrayList<>();

    /**
     * Ob die markierten Werte fett sind ("Bold numbers"): einmal beim Bau gelesen, nicht in jedem Bild. Ein Kasten wird oft neu gebaut (hoechstens
     * eine Viertelsekunde alt, siehe SafariHud), also gilt eine Aenderung des Schalters sofort danach - und ein einzelner Kasten ist in sich
     * stimmig: Er misst und zeichnet mit demselben Stand.
     */
    private final boolean boldNumbers;

    public HudPanel() {
        this(HudNumbers.bold());
    }

    /** Mit festem Stand des Schalters - fuer die Pruefung, die keine Konfiguration anfassen soll */
    HudPanel(boolean boldNumbers) {
        this.boldNumbers = boldNumbers;
    }

    /**
     * Die Texte der Werte, wie sie gemessen und gezeichnet werden: die gespeicherten Werte, bei markierten Zeilen und eingeschaltetem Fettdruck mit
     * "§l" (siehe {@link #measure}). Gefuellt beim Ausmessen, danach nur gelesen - im Zeichnen entsteht kein neuer Text.
     */
    private String[] shownValue = null;
    /** Die zweite Zelle der Tabellenzeilen, wie sie gezeichnet wird */
    private String[] shownExtra = null;
    /** Die Stuecke der Segment-Zeilen, wie sie gezeichnet werden (je Zeile ein Feld, bei anderen Zeilen null) */
    private String[][] shownParts = null;
    /** Dieselben Stuecke ohne Farbcodes fuer den Umriss ("Glowing text") */
    private String[][] shownPartsPlain = null;

    /**
     * Gibt es eine Zeile, die buendig unter dem Text einer Bild-Zeile beginnt? Dann steht auch der Wert der Bild-Zeile
     * links an dieser Stelle statt am rechten Rand - sonst wanderte er bei einer langen Zeile darunter nach rechts,
     * und beide waeren nicht mehr buendig.
     */
    private boolean hasAligned = false;
    /** Gibt es eine Bild-Zeile mit Text, an der sich buendige Zeilen ausrichten koennen? */
    private boolean anchored = false;
    /** Wo der Text der Bild-Zeile beginnt (die Beschriftung), vom linken Rand des Inhalts gerechnet */
    private int labelX = 0;
    /**
     * Wo die Werte beginnen, wenn die Bild-Zeile Beschriftung UND Wert hat ("Pet:" und der Name): hinter der breitesten Beschriftung. So
     * stehen "Pet:" und "Held Item:" untereinander, und der Name des Pets und der des Items beginnen in einer Spalte.
     */
    private int valueX = 0;
    /** Zwei Spalten (Beschriftung, Wert) oder eine (nur Text, wie bei "Bild rechts")? */
    private boolean twoColumns = false;

    /**
     * Einmal ausgemessen, dann behalten.
     *
     * Schriftbreiten sind teuer - Minecraft zerlegt dafuer die Zeichenkette und schlaegt
     * jede Glyphe nach. Gezeichnet wird in jedem Bild, die Zeilen aendern sich dabei
     * aber nicht: ein Kasten wird gebaut und danach nur noch angezeigt.
     */
    private int measuredWidth = -1;
    private int[] valueWidths = null;
    /** Breite der beiden Spalten der Tabellenzeilen */
    private int firstColumn = 0;
    private int secondColumn = 0;
    /**
     * Eine Zelle kann aus Zahl und Anteil bestehen, getrennt durch dieses Zeichen: Dann stehen
     * die Zahlen in allen Zeilen rechtsbuendig untereinander und die Anteile ebenso, statt dass
     * jede Zelle als Ganzes am rechten Rand klebt.
     */
    public static final char CELL_SPLIT = '\u0001';
    private static final int CELL_GAP = 4;
    private int firstNumber = 0;
    private int firstShare = 0;
    private int secondNumber = 0;
    private int secondShare = 0;

    private static String numberOf(String cell) {
        int i = cell.indexOf(CELL_SPLIT);
        return i < 0 ? cell : cell.substring(0, i);
    }

    private static String shareOf(String cell) {
        int i = cell.indexOf(CELL_SPLIT);
        return i < 0 ? "" : cell.substring(i + 1);
    }

    private static String joined(String cell) {
        return numberOf(cell) + (shareOf(cell).isEmpty() ? "" : " " + shareOf(cell));
    }

    /** Wo eine Zelle ihre Zahl und ihren Anteil zeichnet - rechtsbuendig in Zahl- und Anteilsspalte */
    private void drawCell(GuiGraphicsExtractor graphics, Font font, String cell, int right,
                          int numberWidth, int shareWidth, int y, int colour) {
        if (cell.isEmpty()) return;
        // Zerlegen, Breite und Farbcodes je Zelle nur einmal (TextCache): das Zeichnen laeuft in jedem Bild
        String[] teile = TextCache.zelle(cell, CELL_SPLIT);
        if (teile == null) {
            text(graphics, font, cell, TextCache.ohneFarben(cell), right - TextCache.breite(font, cell), y, colour);
            return;
        }
        String number = teile[0];
        String share = teile[1];
        text(graphics, font, share, TextCache.ohneFarben(share), right - TextCache.breite(font, share), y, colour);
        int numberRight = right - (shareWidth > 0 ? shareWidth + CELL_GAP : 0);
        text(graphics, font, number, TextCache.ohneFarben(number), numberRight - TextCache.breite(font, number), y, colour);
    }
    /** Dieselben Zeilen ohne Farbcodes - der Umriss soll einfarbig dunkel sein */
    private String[] labelOutline = null;
    private String[] valueOutline = null;

    public HudPanel title(String text, int colour) {
        rows.add(new Row(Kind.TITLE, text, "", colour, colour, 0, 0));
        return this;
    }

    public HudPanel line(String text, int colour) {
        rows.add(new Row(Kind.TEXT, text, "", colour, colour, 0, 0));
        return this;
    }

    public HudPanel pair(String label, String value, int labelColour, int valueColour) {
        rows.add(new Row(Kind.PAIR, label, value, labelColour, valueColour, 0, 0));
        return this;
    }

    /**
     * Eine Zeile mit zwei Spalten rechts: Name, dann zwei Zahlenspalten, die in allen
     * Tabellenzeilen buendig untereinander stehen. Fuer Zaehler wie "6  0,23%   2  50%".
     */
    public HudPanel table(String label, String first, String second, int labelColour, int valueColour) {
        rows.add(new Row(Kind.TABLE, label, first, labelColour, valueColour, 0, 0,
                net.minecraft.world.item.ItemStack.EMPTY, second));
        return this;
    }

    /**
     * Eine Zeile aus mehreren Stuecken nebeneinander, jedes in seiner Farbe, mit " | " dazwischen -
     * wie die Summenzeile anderer Tracker. `scale` macht sie kleiner (oder groesser) als den Rest;
     * die Zeile behaelt dabei die Hoehe einer gewoehnlichen, damit Klicks auf die Ueberschrift
     * weiter die richtige Zeile treffen.
     */
    public HudPanel segments(float scale, List<Segment> parts) {
        rows.add(new Row(Kind.SEGMENTS, "", "", 0, 0, 0, 0,
                net.minecraft.world.item.ItemStack.EMPTY, "", List.copyOf(parts),
                Math.max(0.4f, Math.min(2.0f, scale))));
        return this;
    }

    public HudPanel bar(String label, int current, int max, int labelColour, int barColour) {
        rows.add(new Row(Kind.BAR, label, current + "/" + max, labelColour, barColour, current, max));
        return this;
    }

    /**
     * Eine Zeile mit dem Bild des Gegenstands links und Text daneben.
     *
     * Das Bild ist sechzehn Pixel hoch und damit hoeher als eine gewoehnliche Zeile -
     * {@link #height()} rechnet das mit, sonst schnitte der Kasten es unten ab.
     */
    public HudPanel icon(net.minecraft.world.item.ItemStack stack, String label, String value,
                         int labelColour, int valueColour) {
        rows.add(new Row(Kind.ICON, label, value, labelColour, valueColour, 0, 0,
                 stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack));
        return this;
    }

    /**
     * Eine Zeile, die buendig mit dem Text der Bild-Zeile beginnt - das Pet-Item steht so genau unter dem Namen des
     * Pets, nicht am rechten Rand (wo eine Paar-Zeile ihren Wert hinsetzt) und nicht unter dem Bild. Beschriftung und
     * Wert stehen hintereinander, linksbuendig. Gibt es keine Bild-Zeile mit Text, ist es eine gewoehnliche Paar-Zeile.
     */
    public HudPanel aligned(String label, String value, int labelColour, int valueColour) {
        rows.add(new Row(Kind.ALIGNED, label, value, labelColour, valueColour, 0, 0));
        hasAligned = true;
        measuredWidth = -1;
        return this;
    }

    /**
     * Eine Zeile mit einem kleineren Bild, wie das Pet-Item unter dem Pet: Das Bild steht mittig unter dem grossen Bild der Bild-Zeile
     * darueber, der Text - falls einer da ist - beginnt buendig mit dem Text der Bild-Zeile (siehe {@link #aligned}).
     *
     * @param scale wie gross, im Verhaeltnis zum Bild der Bild-Zeile: 1.0 gleich gross, 0.75 ein Viertel kleiner
     */
    public HudPanel subIcon(net.minecraft.world.item.ItemStack stack, float scale, String label, String value,
                            int labelColour, int valueColour) {
        rows.add(new Row(Kind.SUBICON, label, value, labelColour, valueColour, 0, 0,
                stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack, "", List.of(),
                Math.max(0.25f, Math.min(1.0f, scale))));
        hasAligned = true;
        measuredWidth = -1;
        return this;
    }

    public HudPanel blank() {
        rows.add(new Row(Kind.BLANK, "", "", 0, 0, 0, 0));
        return this;
    }

    /**
     * Welche Zeilenarten einen fetten Wert kennen: die mit einer Wert-Spalte (Paar, buendige Zeile, Bild-Zeile, Tabelle). Ueberschrift und Text haben
     * keine; der Wert eines Balkens ist "x/y", kein Betrag; eine Segment-Zeile traegt ihre fetten Zahlen in den Stuecken ({@link Segment#withNumber}).
     */
    private static boolean boldable(Kind kind) {
        return switch (kind) {
            case PAIR, ALIGNED, ICON, SUBICON, TABLE -> true;
            case TITLE, TEXT, BAR, BLANK, SEGMENTS -> false;
        };
    }

    /**
     * Macht den Wert der zuletzt hinzugefuegten Zeile fett, sofern "Bold numbers" an ist. Der Aufrufer sagt damit, dass dort ein Betrag oder eine
     * Menge steht (Coins, Stueckzahl) - Zeiten, Namen und Anteile bleiben schlicht. Bei einer Tabellenzeile werden die Zahlen beider Zellen fett,
     * nicht ihr Anteil (hinter {@link #CELL_SPLIT}). Bei Zeilenarten ohne Wert-Spalte geschieht nichts.
     */
    public HudPanel boldValue() {
        if (rows.isEmpty()) return this;
        int last = rows.size() - 1;
        Row row = rows.get(last);
        if (!boldable(row.kind())) return this;
        rows.set(last, row.withBold());
        measuredWidth = -1;   // die Breite muss neu ausgemessen werden
        return this;
    }

    /** Wie viele Zeilen der Kasten hat - fuer Klicks auf eine bestimmte */
    public int rowCount() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int width(Font font) {
        return contentWidth(font) + PADDING * 2;
    }

    public int height() {
        int sum = 0;
        for (Row row : rows) sum += rowHeight(row);
        return sum + PADDING * 2;
    }

    private int rowHeight(Row row) {
        if (row.kind() == Kind.SUBICON) return Math.max(subSize(row), LINE_HEIGHT);
        return row.kind() == Kind.ICON ? Math.max(iconSize(), LINE_HEIGHT) : LINE_HEIGHT;
    }

    /** Kantenlaenge des kleineren Bildes: im Verhaeltnis zum Bild der Bild-Zeile */
    private int subSize(Row row) {
        return Math.max(4, Math.round(iconSize() * row.scale()));
    }

    private int contentWidth(Font font) {
        // Der Methodenverweis entsteht nur, wenn wirklich gemessen wird - nicht in jedem Bild
        if (measuredWidth < 0) measure(font::width);
        return measuredWidth;
    }

    /** Wie {@link #width(Font)} (ohne den Rand), aber mit einer beliebigen Breitenfunktion - fuer die Pruefung, die keine Schrift hat */
    int contentWidth(ToIntFunction<String> width) {
        measure(width);
        return measuredWidth;
    }

    /** Der Text des Werts einer Zeile, wie er gemessen und gezeichnet wird; erst nach dem Ausmessen */
    String shownValue(int row) {
        return shownValue[row];
    }

    /** Die zweite Zelle einer Tabellenzeile, wie sie gezeichnet wird; erst nach dem Ausmessen */
    String shownExtra(int row) {
        return shownExtra[row];
    }

    /** Ein Stueck einer Segment-Zeile, wie es gezeichnet wird; erst nach dem Ausmessen */
    String shownPart(int row, int part) {
        return shownParts[row][part];
    }

    /**
     * Eine Zelle "Zahl|Anteil" mit fetter Zahl: nur der Teil vor {@link #CELL_SPLIT} wird fett, der Anteil bleibt. Ohne Trenner ist die ganze
     * Zelle die Zahl.
     */
    private static String boldCell(String cell) {
        int i = cell.indexOf(CELL_SPLIT);
        if (i < 0) return HudNumbers.bolden(cell);
        return HudNumbers.bolden(cell.substring(0, i)) + cell.substring(i);
    }

    /**
     * Ausmessen: die Texte der Werte festlegen (fett oder nicht) und alle Breiten damit rechnen. Einmal je Kasten; gezeichnet wird danach nur noch.
     *
     * @param w die Breite eines Textes in Punkten - im Spiel die Schrift, in der Pruefung eine eigene Funktion
     */
    private void measure(ToIntFunction<String> w) {
        if (measuredWidth >= 0) return;

        valueWidths = new int[rows.size()];
        // Zuerst die Texte, wie sie gezeichnet werden: Alle Breiten unten rechnen mit ihnen, nicht mit dem gespeicherten Wert
        shownValue = new String[rows.size()];
        shownExtra = new String[rows.size()];
        shownParts = new String[rows.size()][];
        shownPartsPlain = new String[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean bold = boldNumbers && row.bold();
            boolean table = row.kind() == Kind.TABLE;
            shownValue[i] = !bold ? row.value() : table ? boldCell(row.value()) : HudNumbers.bolden(row.value());
            shownExtra[i] = bold && table ? boldCell(row.extra()) : row.extra();
            if (row.kind() == Kind.SEGMENTS) {
                shownParts[i] = new String[row.parts().size()];
                shownPartsPlain[i] = new String[row.parts().size()];
                for (int p = 0; p < shownParts[i].length; p++) {
                    Segment part = row.parts().get(p);
                    String shown = part.shown(boldNumbers);
                    shownParts[i][p] = shown;
                    shownPartsPlain[i][p] = shown.equals(part.text()) ? TextCache.ohneFarben(shown) : outlineOfBold(shown);
                }
            }
        }
        anchored = false;
        twoColumns = false;
        labelX = 0;
        valueX = 0;
        if (hasAligned) {
            for (Row row : rows) {
                if (row.kind() != Kind.ICON || (row.label().isEmpty() && row.value().isEmpty())) continue;
                anchored = true;
                labelX = iconSize() + GUTTER;
                // Steht ein Wert in der Bild-Zeile, ist er der Name - die Beschriftung davor ("Pet:") geht ihm voraus. Die
                // Beschriftungen der buendigen Zeilen stehen darunter in derselben Spalte ("Held Item:" unter "Pet:"), die Werte
                // beginnen hinter der breitesten von ihnen
                twoColumns = !row.value().isEmpty();
                int labelWidth = w.applyAsInt(row.label());
                for (Row other : rows) {
                    if (other.kind() == Kind.ALIGNED || other.kind() == Kind.SUBICON) {
                        labelWidth = Math.max(labelWidth, w.applyAsInt(other.label()));
                    }
                }
                valueX = labelX + (labelWidth > 0 ? labelWidth + GUTTER : GUTTER);
                break;
            }
        }
        labelOutline = new String[rows.size()];
        valueOutline = new String[rows.size()];
        int widest = 0;
        firstColumn = 0;
        secondColumn = 0;
        firstNumber = 0;
        firstShare = 0;
        secondNumber = 0;
        secondShare = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() != Kind.TABLE) continue;
            // Die Zellen, wie sie gezeichnet werden: bei fettem Wert ist die Zahl breiter, und die Spalten richten sich danach
            String first = shownValue[i];
            String second = shownExtra[i];
            firstNumber = Math.max(firstNumber, w.applyAsInt(numberOf(first)));
            firstShare = Math.max(firstShare, w.applyAsInt(shareOf(first)));
            secondNumber = Math.max(secondNumber, w.applyAsInt(numberOf(second)));
            secondShare = Math.max(secondShare, w.applyAsInt(shareOf(second)));
            // Eine Zelle ohne Anteil (eine Kopfzeile) darf auch breiter sein als beide Spalten
            firstColumn = Math.max(firstColumn, w.applyAsInt(joined(first)));
            secondColumn = Math.max(secondColumn, w.applyAsInt(joined(second)));
        }
        firstColumn = Math.max(firstColumn, firstNumber + (firstShare > 0 ? CELL_GAP + firstShare : 0));
        secondColumn = Math.max(secondColumn, secondNumber + (secondShare > 0 ? CELL_GAP + secondShare : 0));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            valueWidths[i] = row.value().isEmpty() ? 0 : w.applyAsInt(shownValue[i]);
            labelOutline[i] = stripColours(row.label());
            valueOutline[i] = stripColours(shownValue[i]);

            int width = switch (row.kind()) {
                case TITLE, TEXT -> w.applyAsInt(row.label());
                case PAIR -> w.applyAsInt(row.label()) + GUTTER + valueWidths[i];
                case BAR -> w.applyAsInt(row.label()) + GUTTER + BAR_WIDTH + GUTTER + valueWidths[i];
                case BLANK -> 0;
                case TABLE -> w.applyAsInt(row.label()) + GUTTER + firstColumn
                              + (secondColumn == 0 ? 0 : GUTTER + secondColumn);
                case ICON -> anchored && twoColumns && valueWidths[i] > 0
                        ? valueX + valueWidths[i]
                        : iconSize() + GUTTER + w.applyAsInt(row.label())
                          + (valueWidths[i] == 0 ? 0 : GUTTER + valueWidths[i]);
                case SUBICON -> {
                    int text = alignedTextWidth(w, row, valueWidths[i]);
                    yield anchored ? text : subSize(row) + (text == 0 ? 0 : GUTTER + text);
                }
                case ALIGNED -> anchored ? alignedTextWidth(w, row, valueWidths[i])
                        : w.applyAsInt(row.label()) + GUTTER + valueWidths[i];
                case SEGMENTS -> (int) Math.ceil(segmentsWidth(w, shownParts[i]) * row.scale());
            };
            widest = Math.max(widest, width);
        }
        measuredWidth = widest;
    }

    /**
     * Wie weit der Text einer buendigen Zeile vom linken Rand des Inhalts reicht. Zwei Spalten: die Beschriftung beginnt in der Spalte der
     * Beschriftung, der Wert in der Spalte der Werte; eine Spalte: beides hintereinander ab dem Text der Bild-Zeile.
     */
    private int alignedTextWidth(ToIntFunction<String> w, Row row, int valueWidth) {
        int labelWidth = w.applyAsInt(row.label());
        if (twoColumns) return valueWidth > 0 ? valueX + valueWidth : labelX + labelWidth;
        return labelX + labelWidth + (row.label().isEmpty() || valueWidth == 0 ? 0 : w.applyAsInt(" ")) + valueWidth;
    }

    /** Die Breite einer Segment-Zeile in ihren eigenen Massen, vor dem Verkleinern: die Stuecke so, wie sie gezeichnet werden */
    private static int segmentsWidth(ToIntFunction<String> w, String[] parts) {
        int width = 0;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) width += w.applyAsInt(SEGMENT_SEPARATOR);
            width += w.applyAsInt(parts[i]);
        }
        return width;
    }

    /**
     * Deckend machen, falls das Alpha-Byte fehlt.
     *
     * 26.1 verwirft Text mit Alpha 0 ersatzlos - aus 0xBBBBBB wird nicht etwa graue
     * Schrift, sondern gar keine. Damit das nicht bei jedem neuen Aufrufer wieder
     * passiert, wird es hier einmal zentral abgefangen.
     */
    private static int opaque(int colour) {
        return (colour >>> 24) == 0 ? 0xFF000000 | colour : colour;
    }

    /**
     * Deckkraft auf eine Farbe rechnen.
     *
     * Nur fuer den Untergrund gedacht. Die Schrift bleibt voll deckend - durchsichtig
     * gemacht waere sie ueber hellem Gelaende nicht mehr zu lesen, und lesen will man
     * sie ja gerade.
     */
    private static int withAlpha(int colour, float alpha) {
        int base = opaque(colour);
        int a = Math.round(((base >>> 24) & 0xFF) * Math.clamp(alpha, 0f, 1f));
        return (a << 24) | (base & 0xFFFFFF);
    }

    /**
     * Farbcodes herausnehmen.
     *
     * Der Umriss wird mit derselben Zeichenkette gezeichnet wie die Schrift. Bliebe ein
     * Farbcode darin, waere der Umriss dort hell statt dunkel - aus dem Leuchten wuerde
     * fette Schrift. Formatierungscodes (fett, kursiv) bleiben stehen, weil sie die
     * Breite aendern und der Umriss sonst verrutschte.
     */
    private static String stripColours(String text) {
        return TextCache.stripColours(text);
    }

    /**
     * Die Farbcodes (0-9, a-f) herausnehmen, "§l" und "§r" bleiben. Fuer das Stueck einer Segment-Zeile mit fetter Zahl: Das "§r" beendet dort den
     * Fettdruck hinter der Zahl; faellt es weg (wie bei {@link #stripColours}), laeuft der Umriss hinter der Zahl fett weiter und stimmt nicht mehr mit
     * der Schrift ueberein.
     */
    static String outlineOfBold(String text) {
        if (text == null || text.indexOf('§') < 0) return text == null ? "" : text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Der dunkle Umriss zu einer Schriftfarbe.
     *
     * Dieselbe Farbe stark abgedunkelt, wie Minecraft es bei leuchtenden Schildern macht:
     * innen der helle Kern, aussen herum sein eigener dunkler Ton. Ein schwarzer Umriss
     * fuer alle waere flacher - so behaelt jede Zeile ihre Farbe.
     */
    private static int outlineOf(int colour) {
        int base = opaque(colour);
        int r = (int) (((base >> 16) & 0xFF) * 0.22f);
        int g = (int) (((base >> 8) & 0xFF) * 0.22f);
        int b = (int) ((base & 0xFF) * 0.22f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Eine Zeile zeichnen - mit Leuchten oder ohne.
     *
     * Mit Leuchten steht die Schrift viermal versetzt (siehe {@link GlowOffsets}) im dunklen Ton
     * darunter und einmal hell darueber. Denselben Kniff benutzt Minecraft fuer Schilder mit
     * Leuchtender Tinte (dort achtmal). Der uebliche Schlagschatten faellt dabei weg, weil der
     * Umriss ihn ersetzt.
     */
    private static void text(GuiGraphicsExtractor graphics, Font font, String text, String plain,
                             int x, int y, int colour) {
        int fill = opaque(colour);
        if (!ModConfig.INSTANCE.hud.glowingHudText) {
            graphics.text(font, text, x, y, fill, true);
            return;
        }

        int outline = outlineOf(colour);
        for (int[] offset : GlowOffsets.OFFSETS) {
            graphics.text(font, plain, x + offset[0], y + offset[1], outline, false);
        }
        graphics.text(font, text, x, y, fill, false);
    }

    /**
     * Welche Zeile liegt unter dem Zeiger? -1, wenn keine.
     *
     * Gerechnet wird in den Massen des Kastens, deshalb wird die Skalierung
     * herausgerechnet - gezeichnet wird er ja vergroessert.
     */
    public int rowAt(Font font, int left, int top, float scale, double mouseX, double mouseY) {
        if (rows.isEmpty() || scale <= 0) return -1;

        double localX = (mouseX - left) / scale;
        double localY = (mouseY - top) / scale;
        if (localX < 0 || localX > width(font)) return -1;

        int index = (int) Math.floor((localY - PADDING) / LINE_HEIGHT);
        return index >= 0 && index < rows.size() ? index : -1;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int left, int top) {
        render(graphics, font, left, top, 1.0f);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int left, int top, float alpha) {
        if (rows.isEmpty()) return;

        int content = contentWidth(font);
        int right = left + content + PADDING * 2;
        graphics.fill(left, top, right, top + height(), withAlpha(BACKGROUND, alpha));
        graphics.fill(left, top, right, top + 1, withAlpha(TOP_EDGE, alpha));

        int x = left + PADDING;
        int y = top + PADDING;
        for (int i = 0; i < rows.size(); i++) {
            draw(graphics, font, rows.get(i), x, y, content, i, alpha);
            y += rowHeight(rows.get(i));
        }
    }

    private void draw(GuiGraphicsExtractor graphics, Font font, Row row, int x, int y,
                      int content, int index, float alpha) {
        int valueWidth = valueWidths[index];
        switch (row.kind()) {
            case BLANK -> {
            }
            case ICON -> {
                int size = iconSize();
                if (!row.icon().isEmpty()) {
                    if (iconScale == 1.0f) {
                        graphics.fakeItem(row.icon(), x, y);
                    } else {
                        // Gegenstandsbilder gibt es nur in einer Groesse - vergroessert
                        // wird ueber die Zeichenflaeche, sonst bliebe es bei sechzehn
                        graphics.pose().pushMatrix();
                        graphics.pose().translate(x, y);
                        graphics.pose().scale(iconScale, iconScale);
                        graphics.fakeItem(row.icon(), 0, 0);
                        graphics.pose().popMatrix();
                    }
                }
                // Der Text sitzt mittig zur Bildhoehe, sonst klebte er am oberen Rand
                int textY = y + (size - LINE_HEIGHT) / 2 + 1;
                int textX = x + size + GUTTER;
                text(graphics, font, row.label(), labelOutline[index], textX, textY, row.labelColour());
                if (!row.value().isEmpty()) {
                    // Mit einer buendigen Zeile darunter beginnt der Wert links, in der Spalte der Werte
                    int wertX = anchored && twoColumns ? x + valueX : x + content - valueWidth;
                    text(graphics, font, shownValue[index], valueOutline[index], wertX, textY, row.valueColour());
                }
            }
            case SUBICON -> {
                int sub = subSize(row);
                int rowH = rowHeight(row);
                // Mittig unter dem grossen Bild; ohne Bild-Zeile darueber links
                int ix = x + (anchored ? Math.max(0, (iconSize() - sub) / 2) : 0);
                if (!row.icon().isEmpty()) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(ix, y + (rowH - sub) / 2f);
                    graphics.pose().scale(sub / (float) ICON_SIZE, sub / (float) ICON_SIZE);
                    graphics.fakeItem(row.icon(), 0, 0);
                    graphics.pose().popMatrix();
                }
                int textY = y + (rowH - LINE_HEIGHT) / 2 + 1;
                int tx = anchored ? x + labelX : x + sub + GUTTER;
                if (!row.label().isEmpty()) {
                    text(graphics, font, row.label(), labelOutline[index], tx, textY, row.labelColour());
                }
                if (!row.value().isEmpty()) {
                    int vx = anchored && twoColumns ? x + valueX
                            : tx + (row.label().isEmpty() ? 0 : TextCache.breite(font, row.label()) + TextCache.breite(font, " "));
                    text(graphics, font, shownValue[index], valueOutline[index], vx, textY, row.valueColour());
                }
            }
            case ALIGNED -> {
                if (anchored) {
                    int ax = x + labelX;
                    text(graphics, font, row.label(), labelOutline[index], ax, y, row.labelColour());
                    if (!row.value().isEmpty()) {
                        int vx = twoColumns ? x + valueX
                                : ax + (row.label().isEmpty() ? 0 : TextCache.breite(font, row.label()) + TextCache.breite(font, " "));
                        text(graphics, font, shownValue[index], valueOutline[index], vx, y, row.valueColour());
                    }
                } else {
                    // Kein Bild, an dem man sich ausrichten koennte: wie eine Paar-Zeile
                    text(graphics, font, row.label(), labelOutline[index], x, y, row.labelColour());
                    text(graphics, font, shownValue[index], valueOutline[index], x + content - valueWidth, y, row.valueColour());
                }
            }
            case TITLE, TEXT -> text(graphics, font, row.label(), labelOutline[index],
                    x, y, row.labelColour());
            case SEGMENTS -> {
                // Verkleinert wird ueber die Zeichenflaeche, und die Schrift sitzt dann mittig in der
                // Zeilenhoehe - sonst klebte eine kleinere Zeile oben an der Kante
                float shift = (LINE_HEIGHT - font.lineHeight * row.scale()) / 2f;
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y + shift);
                graphics.pose().scale(row.scale(), row.scale());
                int cursor = 0;
                for (int i = 0; i < row.parts().size(); i++) {
                    if (i > 0) {
                        text(graphics, font, SEGMENT_SEPARATOR, SEGMENT_SEPARATOR, cursor, 0, SEPARATOR_COLOUR);
                        cursor += TextCache.breite(font, SEGMENT_SEPARATOR);
                    }
                    // Der Text, wie er gemessen wurde (mit "§l" um die Zahl, wenn sie fett ist)
                    String shown = shownParts[index][i];
                    text(graphics, font, shown, shownPartsPlain[index][i], cursor, 0, row.parts().get(i).colour());
                    cursor += TextCache.breite(font, shown);
                }
                graphics.pose().popMatrix();
            }
            case TABLE -> {
                text(graphics, font, row.label(), labelOutline[index], x, y, row.labelColour());
                int secondRight = x + content;
                int firstRight = secondRight - (secondColumn == 0 ? 0 : secondColumn + GUTTER);
                drawCell(graphics, font, shownValue[index], firstRight, firstNumber, firstShare, y, row.valueColour());
                drawCell(graphics, font, shownExtra[index], secondRight, secondNumber, secondShare, y, row.valueColour());
            }
            case PAIR -> {
                text(graphics, font, row.label(), labelOutline[index], x, y, row.labelColour());
                int valueX = x + content - valueWidth;
                text(graphics, font, shownValue[index], valueOutline[index], valueX, y, row.valueColour());
            }
            case BAR -> {
                text(graphics, font, row.label(), labelOutline[index], x, y, row.labelColour());

                // Der Balken sitzt rechts, direkt vor der Zahl - so stehen alle Balken
                // untereinander auf gleicher Hoehe, egal wie lang die Beschriftung ist
                int barLeft = x + content - valueWidth - GUTTER - BAR_WIDTH;
                int barY = y + 2;
                // Die leere Rinne gehoert zum Untergrund und wird mit ihm durchsichtig,
                // der gefuellte Teil ist Inhalt und bleibt stehen
                graphics.fill(barLeft, barY, barLeft + BAR_WIDTH, barY + 5, withAlpha(0xFF303030, alpha));
                if (row.max() > 0 && row.current() > 0) {
                    int filled = Math.max(1, BAR_WIDTH * Math.min(row.current(), row.max()) / row.max());
                    graphics.fill(barLeft, barY, barLeft + filled, barY + 5, opaque(row.valueColour()));
                }

                text(graphics, font, row.value(), valueOutline[index],
                        x + content - valueWidth, y, HudColours.WHITE);
            }
        }
    }
}
