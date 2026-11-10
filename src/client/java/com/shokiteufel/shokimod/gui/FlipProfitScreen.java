package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.BazaarFlips;
import com.shokiteufel.shokimod.util.BazaarFlips.Row;
import com.shokiteufel.shokimod.util.BazaarLive;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Was der Bazaar zwischen Kaufauftrag und Verkaufsangebot uebrig laesst.
 *
 * Gerechnet wird in {@link BazaarFlips}; hier wird ausgewaehlt, gereiht und gezeigt.
 * Die Preise kommen live von Hypixel, solange das Fenster offen ist - bei einem
 * Geschaeft, das von der Spanne zwischen zwei Auftraegen lebt, waere ein zehn Minuten
 * alter Stand wertlos.
 */
public class FlipProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 560;
    private int listTop = 82;

    /** Wonach gereiht wird - ueberdauert das Schliessen des Fensters */
    private static Sort sort = Sort.PROFIT;
    private static String search = "";
    private int page = 0;
    private EditBox searchBox;

    private List<Row> cached = null;
    private int lastLiveAge = -1;

    // Gemerkte Texte: gebaut nur, wenn sich ihre Quellen aendern, nicht in jedem Bild
    /** Die graue Statuszeile oben ("12 goods worth the spread, live prices 3s old ...") - neu gebaut, wenn sich die Zahl der Waren oder das Alter aendert */
    private final FensterText.Gemerkt info = new FensterText.Gemerkt();
    /** "Nothing found for ..." - gebaut nur, wenn sich der Suchtext aendert */
    private final FensterText.Gemerkt nichtsGefunden = new FensterText.Gemerkt();

    /**
     * Die fertig geformten Texte einer Zeile: gekuerzter Name, Preise, Marge, Mengen.
     *
     * Eine {@link Row} ist unveraenderlich (ein Record), und eine neue Liste bekommt neue Zeilen - gerechnet wird also einmal, wenn die
     * Zeile auf die Seite kommt, nicht in jedem Bild.
     */
    private static final class ZeilenText {
        final String name;
        final String kauf;
        final String verkauf;
        final String gewinn;
        final String marge;
        final String gekauft;
        final String verkauft;

        ZeilenText(Row row) {
            name = cut(row.name(), 30);
            kauf = BazaarFlips.coins(row.buyOrder());
            verkauf = BazaarFlips.coins(row.sellOffer());
            gewinn = BazaarFlips.coins(row.profit());
            marge = String.format(Locale.ROOT, "%.1f%%", row.margin());
            gekauft = ItemValue.format(row.bought());
            verkauft = ItemValue.format(row.sold());
        }
    }

    // Die Texte der Zeilen auf der gezeigten Seite. Neu gebaut, wenn sich die Liste (neue Zeilen-Objekte), der Seitenanfang oder die Zeilenzahl
    // je Seite aendert
    private ZeilenText[] zeilenTexte = new ZeilenText[0];
    private List<Row> zeilenTexteListe;
    private int zeilenTexteStart = -1;
    private int zeilenTexteProSeite = -1;

    /**
     * Die drei Fragen, die eine Spanne beantworten kann.
     *
     * Der Gewinn je Stueck sagt, wie viel ein einzelner Durchgang bringt; die Marge
     * sagt, wie viel Geld dafuer gebunden ist; der Tageswert sagt, was die Ware
     * ueberhaupt hergibt. Oben steht je nach Frage etwas anderes, und keine der drei
     * Reihungen ist die richtige fuer alle.
     */
    private enum Sort {
        PROFIT("Profit"),
        MARGIN("Margin"),
        PER_DAY("Per day");

        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    private final Screen parent;

    public FlipProfitScreen(Screen parent) {
        super(Component.literal("Bazaar flips"));
        this.parent = parent;
    }

    private static ModConfig.BazaarCategory cfg() {
        return ModConfig.INSTANCE.bazaar;
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 70 - listTop) / ROW_HEIGHT);
    }

    private void invalidate() {
        cached = null;
    }

    private List<Row> visible() {
        if (cached != null) return cached;

        List<Row> alle = BazaarFlips.rows(cfg().tax.percent, (long) cfg().minPerDay);
        List<Row> out = new ArrayList<>(alle.size());
        String suche = search.toLowerCase(Locale.ROOT);
        for (Row row : alle) {
            if (!suche.isEmpty() && !row.name().toLowerCase(Locale.ROOT).contains(suche)) continue;
            out.add(row);
        }
        switch (sort) {
            case MARGIN -> out.sort((a, b) -> Double.compare(b.margin(), a.margin()));
            case PER_DAY -> out.sort((a, b) -> Double.compare(b.perDay(), a.perDay()));
            default -> {
                // schon nach Gewinn gereiht
            }
        }
        cached = out;
        return cached;
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + perPage() - 1) / perPage());
    }

    /**
     * Die zusammengesetzten Texte des Bildes (Statuszeile, "Nothing found"): gebaut nur, wenn sich ihre Quellen aendern (Zahl der Waren,
     * Alter des Live-Stands, Suchtext).
     */
    private void texteAktualisieren(int anzahl) {
        int alter = Math.clamp(BazaarLive.ageSeconds(), 0, 999_999);
        long schluessel = anzahl * 1_000_000L + alter;
        if (!info.gilt(schluessel)) {
            info.setze(schluessel, Component.literal(
                            anzahl + " goods worth the spread, live prices "
                            + alter + "s old"
                            + " - buy order and sell offer each outbid by " + BazaarFlips.STEP
                            + ", tax taken off - click a row for /bz")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (anzahl == 0 && !search.isBlank() && !nichtsGefunden.gilt(0L, search)) {
            nichtsGefunden.setze(0L, search, Component.literal("Nothing found for \"" + search + "\"")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Die Texte der Zeilen auf der gezeigten Seite: gerechnet, wenn eine andere Seite oder Liste gezeigt wird, sonst dasselbe Feld wie im Bild davor */
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

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        int left = left();
        int sucheY = 44;
        listTop = 82;

        searchBox = new EditBox(font, left, sucheY, 220, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search an item ..."));
        searchBox.setMaxLength(48);
        searchBox.setValue(search);
        searchBox.setResponder(text -> {
            search = text == null ? "" : text.trim();
            page = 0;
            invalidate();
        });
        addRenderableWidget(searchBox);

        Button reihung = Button.builder(
                Component.literal("Sort: " + sort.label).withStyle(ChatFormatting.AQUA),
                button -> {
                    Sort[] alle = Sort.values();
                    sort = alle[(sort.ordinal() + 1) % alle.length];
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 226, sucheY, 100, 18).build();
        reihung.setTooltip(Tooltip.create(Component.literal(
                "Profit: coins per item. Margin: profit against the coins tied up - "
                        + "the cheap goods lead here. Per day: profit times the smaller "
                        + "of the two daily amounts, an upper bound you share with "
                        + "everyone running the same orders.")));
        addRenderableWidget(reihung);

        // Die Steuer sitzt in den Einstellungen, weil sie fuer jeden Verkauf gilt -
        // aber sie gehoert auch hierher: Wer sie falsch stehen hat, liest eine Liste,
        // die bei den billigen Waren in der Reihenfolge nicht stimmt
        addRenderableWidget(Button.builder(
                Component.literal("Tax: " + cfg().tax.percent + "%").withStyle(ChatFormatting.GOLD),
                button -> {
                    ModConfig.SellTax[] alle = ModConfig.SellTax.values();
                    cfg().tax = alle[(cfg().tax.ordinal() + 1) % alle.length];
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 330, sucheY, 100, 18).build());

        addRenderableWidget(Button.builder(
                Component.literal("Min/day: " + ItemValue.format((long) cfg().minPerDay))
                        .withStyle(ChatFormatting.GRAY),
                button -> {
                    // Die Stufen, nach denen man wirklich entscheidet: gar kein Filter,
                    // "bewegt sich", "laeuft gut", "laeuft staendig"
                    float[] stufen = {0f, 100f, 1000f, 10000f, 50000f};
                    float jetzt = cfg().minPerDay;
                    float naechste = stufen[0];
                    for (float stufe : stufen) {
                        if (stufe > jetzt) {
                            naechste = stufe;
                            break;
                        }
                    }
                    cfg().minPerDay = naechste;
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 434, sucheY, 126, 18).build());

        int unten = height - 28;
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

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + LIST_WIDTH - 80, unten, 80, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /** Ein Klick auf eine Zeile oeffnet die Ware im Bazaar */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;

        double x = event.x();
        double y = event.y();
        int left = left();
        if (y < listTop - 3 || x < left || x > left + LIST_WIDTH) return false;

        int index = (int) ((y - listTop + 3) / ROW_HEIGHT);
        List<Row> zeilen = visible();
        int start = page * perPage();
        if (index < 0 || index >= perPage() || start + index >= zeilen.size()) return false;

        Row row = zeilen.get(start + index);
        if (minecraft == null || minecraft.player == null) return false;
        minecraft.setScreen(null);
        minecraft.player.connection.sendCommand("bz " + BazaarFlips.searchName(row.id()));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        boolean behandelt = super.keyPressed(event);
        if (searchBox != null && searchBox.isFocused() && !search.equals(searchBox.getValue().trim())) {
            search = searchBox.getValue().trim();
            invalidate();
            rebuild();
        }
        return behandelt;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // Solange jemand hinsieht, werden die Preise direkt bei Hypixel geholt
        BazaarLive.wanted();
        int alter = BazaarLive.ageSeconds();
        if (alter >= 0 && alter != lastLiveAge) {
            lastLiveAge = alter;
            if (alter <= 1) invalidate();
        }

        int left = left();
        int centerX = width / 2;

        graphics.centeredText(font, FensterText.gold("Bazaar flips"), centerX, 14, 0xFFFFAA00);

        if (!BazaarLive.fresh()) {
            graphics.centeredText(font, FensterText.grau("Fetching the bazaar ..."), centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        List<Row> zeilen = visible();
        // Die Statuszeile ist fertig gebaut und wird nur neu gebaut, wenn sich ihre Zahlen aendern
        texteAktualisieren(zeilen.size());
        graphics.centeredText(font, info.component(), centerX, 28, 0xFF77DD77);

        graphics.text(font, "Item", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Buy order", left + 190, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Sell offer", left + 254, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 318, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Margin", left + 374, listTop - 12, 0xFF888888, false);
        // Beide Seiten, nicht nur eine: Gekauft werden muss ebenso wie verkauft, und
        // eine Ware, die taeglich tausendmal weggeht, aber nur zehnmal hereinkommt,
        // laesst den eigenen Kaufauftrag stehen
        graphics.text(font, "Bought/day", left + 428, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Sold/day", left + 500, listTop - 12, 0xFF888888, false);

        if (zeilen.isEmpty()) {
            graphics.centeredText(font, search.isBlank()
                            ? FensterText.grau("Nothing clears the tax at this daily amount")
                            : nichtsGefunden.component(),
                    centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        // Die Texte der gezeigten Zeilen liegen fertig vor (gekuerzt, formatiert) und werden nur neu gebaut, wenn Zeilen oder Seite wechseln
        int proSeite = perPage();
        int start = page * proSeite;
        zeilenTexteAktualisieren(zeilen, start, proSeite);
        ZeilenText[] texte = zeilenTexte;
        for (int i = 0; i < texte.length; i++) {
            ZeilenText t = texte[i];
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, t.name, left + 4, y, 0xFFFFCC66, false);
            graphics.text(font, t.kauf, left + 190, y, 0xFFFF7777, false);
            graphics.text(font, t.verkauf, left + 254, y, 0xFFAAAAFF, false);
            graphics.text(font, t.gewinn, left + 318, y, 0xFF55FF55, false);
            graphics.text(font, t.marge, left + 374, y, 0xFF55FFFF, false);
            graphics.text(font, t.gekauft, left + 428, y, 0xFFCCCCCC, false);
            graphics.text(font, t.verkauft, left + 500, y, 0xFFCCCCCC, false);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    /**
     * Kuerzt, und vertraegt auch gar keinen Text.
     *
     * Ein fehlender Name hat das ganze Spiel abgeschossen, als eine Bazaar-Ware keinen
     * in der Item-Liste hatte. Namen kommen jetzt aus BazaarFlips.nameOf und sind nie
     * leer - aber ein Fenster darf an einer von zweitausend Waren nicht sterben.
     */
    private static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
