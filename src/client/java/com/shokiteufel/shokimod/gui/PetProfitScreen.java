package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.PetProfitData;
import com.shokiteufel.shokimod.util.PetProfitData.Row;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Welche Pets sich zu leveln lohnen, nach Sparte auswaehlbar.
 *
 * Die Zahlen kommen fertig gerechnet aus dem Netz (siehe {@link PetProfitData}); hier
 * wird nur ausgewaehlt und angezeigt. Gereiht wird nach Gewinn je Erfahrungspunkt -
 * das ist die Zahl, die sagt, wo sich die Arbeit lohnt, nicht der Gewinn allein: ein
 * Drache mit dreihundert Millionen Gewinn braucht auch dreissig Millionen Erfahrung.
 */
public class PetProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    /** Wo die Liste beginnt - wird bei zwei Reihen Reiter nach unten geschoben */
    private int listTop = 74;
    private static final int LIST_WIDTH = 470;
    /** Die Sparte, die zuletzt gewaehlt war - ueberdauert das Schliessen des Fensters */
    private static String category = "";
    /**
     * Nur frisch geschluepfte Pets - die ohne Vorgeschichte.
     *
     * Dass dabei Pets fehlten, lag nicht an diesem Haken: Die Liste selbst fuehrte je
     * Pet nur das beste Angebot, und war das ein Vierziger mit besserem Verhaeltnis,
     * kam die Stufe 1 gar nicht erst mit. Seit dem 30.09. liefert der Ablaufplan beides.
     */
    private static boolean onlyLevelOne = false;
    /** Pets ohne Bonbons: die Stufe soll erarbeitet sein, nicht gekauft */
    private static boolean noCandy = false;

    private final Screen parent;
    private int page;

    public PetProfitScreen(Screen parent) {
        super(Component.literal("Pet profit"));
        this.parent = parent;
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 40 - listTop) / ROW_HEIGHT);
    }

    // Die gefilterte Liste, einmal gebaut und gehalten (vorher baute jede Frage eine neue, und ein Bild fragt mehrmals). Eingaben: die Liste aus
    // dem Netz (PetProfitData.rows() ist eine unveraenderliche Kopie, die bei jedem neuen Stand durch eine neue ersetzt wird - ihre Identitaet
    // ist der Versionszaehler) und die drei Schalter dieses Fensters (Sparte, "Level 1 only", "No pet candy"). Beides wird in jedem Bild
    // verglichen, gebaut wird nur bei einer Aenderung.
    private List<Row> sichtbar;
    private List<Row> sichtbarQuelle;
    private String sichtbarSparte;
    private boolean sichtbarNurEins;
    private boolean sichtbarOhneBonbon;

    /** Die Zeilen der gewaehlten Sparte, beste zuerst */
    private List<Row> visible() {
        // rows() holt auch nach, solange das Fenster offen ist (prefetch) - deshalb in jedem Bild gerufen, nicht nur beim Bauen
        List<Row> quelle = PetProfitData.rows();
        if (sichtbar == null || quelle != sichtbarQuelle || !category.equals(sichtbarSparte)
                || onlyLevelOne != sichtbarNurEins || noCandy != sichtbarOhneBonbon) {
            List<Row> out = new ArrayList<>();
            for (Row row : quelle) {
                if (!category.isEmpty() && !category.equals(row.category())) continue;
                if (onlyLevelOne && row.level() != 1) continue;
                if (noCandy && row.candy() > 0) continue;
                out.add(row);
            }
            sichtbar = out;
            sichtbarQuelle = quelle;
            sichtbarSparte = category;
            sichtbarNurEins = onlyLevelOne;
            sichtbarOhneBonbon = noCandy;
        }
        return sichtbar;
    }

    // Gemerkte Texte: gebaut nur, wenn sich ihre Quellen aendern, nicht in jedem Bild
    /** Die graue Statuszeile oben ("123 auctions checked - updated 3 minutes ago ...") */
    private final FensterText.Gemerkt info = new FensterText.Gemerkt();
    /**
     * "updated 3 minutes ago": age() parst bei jedem Aufruf den Zeitstempel, also hoechstens einmal je Sekunde gefragt (das Alter zaehlt in
     * Minuten) und nur bei geaendertem Text ein neues Objekt
     */
    private String standText = "";
    private String standQuelle;
    private long standAt;

    private void texteAktualisieren() {
        long jetzt = System.currentTimeMillis();
        String quelle = PetProfitData.updatedAt();
        if (quelle != standQuelle || jetzt - standAt >= 1_000L) {
            String neu = PetProfitData.age();
            if (!neu.equals(standText)) standText = neu;
            standQuelle = quelle;
            standAt = jetzt;
        }
        long angebote = PetProfitData.auctionsSeen();
        if (!info.gilt(angebote, standText)) {
            info.setze(angebote, standText, Component.literal(
                            angebote + " auctions checked"
                            + (standText.isEmpty() ? "" : " - updated " + standText)
                            + " - click a row to open the auction")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Die fertig geformten Texte einer Zeile: gekuerzter Name, Zahlen mit "k/m/b", Farbe der Seltenheit.
     *
     * Eine {@link Row} ist unveraenderlich (ein Record), und ein neuer Stand bringt neue Zeilen - gerechnet wird also einmal, wenn die Zeile
     * auf die Seite kommt, nicht in jedem Bild.
     */
    private static final class ZeilenText {
        final String name;
        final int nameFarbe;
        final String stufe;
        final String preis;
        final String gewinn;
        final String ziel;
        final String proXp;

        ZeilenText(Row row) {
            name = cut(row.name(), 30);
            nameFarbe = colour(row.rarity());
            stufe = String.valueOf(row.level());
            preis = ItemValue.format(row.price());
            gewinn = ItemValue.format(row.profit());
            // Was dasselbe Pet fertig kostet. Die Drachen gehen bis 200, dort steht
            // zusaetzlich der Preis eines Hundertsten - da steigen die meisten ein
            ziel = row.twoTargets()
                    ? ItemValue.format(row.price100()) + "/" + ItemValue.format(row.targetPrice())
                    : ItemValue.format(row.targetPrice());
            proXp = String.format(Locale.US, "%.2f", row.perXp());
        }
    }

    // Die Texte der Zeilen auf der gezeigten Seite. Neu gebaut, wenn sich die Liste (neue Zeilen-Objekte), der Seitenanfang oder die Zeilenzahl
    // je Seite aendert
    private ZeilenText[] zeilenTexte = new ZeilenText[0];
    private List<Row> zeilenTexteListe;
    private int zeilenTexteStart = -1;
    private int zeilenTexteProSeite = -1;

    private void zeilenTexteAktualisieren(List<Row> zeilen, int start, int proSeite) {
        if (zeilen == zeilenTexteListe && start == zeilenTexteStart && proSeite == zeilenTexteProSeite) return;
        int n = Math.max(0, Math.min(proSeite, zeilen.size() - start));
        ZeilenText[] neu = new ZeilenText[n];
        for (int i = 0; i < n; i++) neu[i] = new ZeilenText(zeilen.get(start + i));
        zeilenTexte = neu;
        zeilenTexteListe = zeilen;
        zeilenTexteStart = start;
        zeilenTexteProSeite = proSeite;
    }

    private int rowCount() {
        return visible().size();
    }

    private int pageCount() {
        return Math.max(1, (rowCount() + perPage() - 1) / perPage());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        int left = left();
        int y = 46;

        // "Alle" und je ein Knopf fuer die Sparten, die wirklich vorkommen
        List<String> sparten = new ArrayList<>();
        sparten.add("");
        sparten.addAll(PetProfitData.categories());

        int x = left;
        for (String sparte : sparten) {
            String beschriftung = sparte.isEmpty() ? "All" : pretty(sparte);
            int breite = Math.max(38, font.width(beschriftung) + 12);
            // Was nicht mehr in die Zeile passt, kommt in die naechste. Vorher fiel es
            // weg - und damit fehlte ausgerechnet Mining, der letzte in der Reihe
            if (x + breite > left + LIST_WIDTH) {
                x = left;
                y += 20;
            }
            boolean gewaehlt = sparte.equals(category);
            Button knopf = Button.builder(
                    Component.literal(beschriftung).withStyle(
                            gewaehlt ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                    button -> {
                        category = sparte;
                        page = 0;
                        rebuild();
                    }).bounds(x, y, breite, 18).build();
            knopf.active = !gewaehlt;
            addRenderableWidget(knopf);
            x += breite + 2;
        }

        // Unter dem letzten Reiter faengt die Liste an. Die Reiter sind achtzehn hoch,
        // darunter brauchen die Spaltenkoepfe noch eine Zeile - sonst haengen sie in
        // den Knoepfen
        listTop = y + 40;

        int unten = height - 28;
        // Die Pfeile links und rechts der Seitenzahl, nicht in der Ecke: Wo man ist
        // und wie man weiterkommt, gehoert zusammen
        if (pageCount() > 1) {
            int mitte = width / 2;
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(mitte - 60, height - 50, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(mitte + 40, height - 50, 20, 20).build());
        }
        addRenderableWidget(Button.builder(
                Component.literal((onlyLevelOne ? "☑" : "☐") + " Level 1 only"), button -> {
                    onlyLevelOne = !onlyLevelOne;
                    page = 0;
                    rebuild();
                }).bounds(left, unten, 110, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal((noCandy ? "☑" : "☐") + " No pet candy"), button -> {
                    noCandy = !noCandy;
                    page = 0;
                    rebuild();
                }).bounds(left + 114, unten, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + LIST_WIDTH - 90, unten, 90, 20).build());
    }

    /**
     * Ein Klick auf eine Zeile oeffnet die Auktion im Spiel.
     *
     * Hypixel kennt dafuer /viewauction mit der Kennung der Auktion - die steht in den
     * Daten. Das Fenster schliesst sich davor, sonst laege es ueber dem, was der Server
     * gleich aufmacht. Ist die Auktion inzwischen verkauft, sagt das der Server selbst;
     * die Liste ist bis zu eine halbe Stunde alt.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Erst die Knoepfe fragen. Sonst faengt die Liste einen Klick ab, der einem
        // Reiter galt - und statt der Sparte oeffnete sich die oberste Auktion
        if (super.mouseClicked(event, doubleClick)) return true;
        if (PetProfitData.ready()) {
            int start = page * perPage();
            List<Row> zeilen = visible();
            int left = left();
            double x = event.x();
            double y = event.y();
            // Nur die Liste selbst zaehlt. Der gerade gewaehlte Reiter ist ein
            // abgeschalteter Knopf und gibt den Klick weiter - ohne diese Grenze
            // oeffnete ein zweiter Klick auf denselben Reiter die oberste Auktion
            if (y >= listTop - 3 && x >= left && x <= left + LIST_WIDTH) {
                int index = (int) ((y - listTop + 3) / ROW_HEIGHT);
                if (index >= 0 && index < perPage() && start + index < zeilen.size()) {
                    String auktion = zeilen.get(start + index).auction();
                    if (!auktion.isEmpty() && minecraft != null && minecraft.player != null) {
                        minecraft.setScreenAndShow(null);
                        minecraft.player.connection.sendCommand("viewauction " + auktion);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int centerX = width / 2;

        // Feste Texte: je Zeichenkette einmal gebaut und geteilt, nicht in jedem Bild
        graphics.centeredText(font, FensterText.gold("Best pets to level"), centerX, 14, 0xFFFFAA00);

        if (!PetProfitData.ready()) {
            graphics.centeredText(font, FensterText.grau("Loading the list ..."), centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        // Die Statuszeile ist fertig gebaut und wird nur neu gebaut, wenn sich ihre Zahlen oder der Stand aendern
        texteAktualisieren();
        graphics.centeredText(font, info.component(), centerX, 28, 0xFF888888);

        // Spaltenkoepfe: dieselben x-Werte wie die Zeilen darunter, damit beides nie
        // auseinanderlaeuft
        graphics.text(font, "Pet", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Lvl", left + 186, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Buy", left + 216, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 280, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Lv100/200 Price", left + 344, listTop - 12, 0xFF888888, false);
        graphics.text(font, "per XP", left + 424, listTop - 12, 0xFF888888, false);

        List<Row> zeilen = visible();
        // Die Texte der gezeigten Zeilen liegen fertig vor (gekuerzt, formatiert, mit Farbe) und werden nur neu gebaut, wenn Zeilen oder Seite wechseln
        int proSeite = perPage();
        int start = page * proSeite;
        zeilenTexteAktualisieren(zeilen, start, proSeite);
        ZeilenText[] texte = zeilenTexte;
        for (int i = 0; i < texte.length; i++) {
            ZeilenText t = texte[i];
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, t.name, left + 4, y, t.nameFarbe, false);
            graphics.text(font, t.stufe, left + 186, y, 0xFFCCCCCC, false);
            graphics.text(font, t.preis, left + 216, y, 0xFFCCCCCC, false);
            graphics.text(font, t.gewinn, left + 280, y, 0xFF55FF55, false);
            graphics.text(font, t.ziel, left + 344, y, 0xFF55FFFF, false);
            graphics.text(font, t.proXp, left + 424, y, 0xFFFFAA00, false);
        }

        drawFooter(graphics, centerX, zeilen.size());
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int centerX, int anzahl) {
        if (anzahl == 0) {
            graphics.centeredText(font, FensterText.grau("Nothing worth levelling here right now"), centerX, listTop + 20, 0xFFAAAAAA);
        }
        int seiten = Math.max(1, (anzahl + perPage() - 1) / perPage());
        if (seiten > 1) {
            // Mittig zwischen den beiden Pfeilen und ueber der Knopfreihe. Vorher lag
            // sie bei height-22 und damit mitten im Knopf "No pet candy"
            graphics.centeredText(font, FensterText.seite(page, seiten), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    /** Die Farbe der Seltenheit, wie im Spiel */
    private static int colour(String rarity) {
        return switch (rarity) {
            case "COMMON" -> 0xFFFFFFFF;
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            case "DIVINE" -> 0xFF55FFFF;
            default -> 0xFFCCCCCC;
        };
    }

    /** "FISHING" liest sich als "Fishing" */
    private static String pretty(String sparte) {
        if (sparte.length() < 2) return sparte;
        return sparte.charAt(0) + sparte.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
