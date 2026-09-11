package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.ShardStock;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ShardProfitData;
import com.shokiteufel.shokimod.util.ShardProfitData.Row;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Welche Shard-Fusionen etwas abwerfen, nach Sparte auswaehlbar.
 *
 * Die Preise kommen fertig aus dem Netz (siehe {@link ShardProfitData}), gerechnet wird
 * hier: Fuer Zutaten und Ergebnis gibt es je zwei Handelswege, und sie lassen sich frei
 * mischen. Deshalb steht der Gewinn nicht in den Daten - er haengt an einer Wahl, die
 * nur der Spieler treffen kann, und aendert sich mit ihr um Millionen.
 *
 * Gereiht wird nach dem Gewinn auf dem gewaehlten Weg, nicht nach dem, der in den Daten
 * am besten aussah: Sonst stuende oben eine Fusion, die sich fuer den Betrachter gar
 * nicht lohnt.
 */
public class ShardProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 500;
    /** Wo die Liste beginnt - wird bei zwei Reihen Reiter nach unten geschoben */
    private int listTop = 74;
    /** Die Sparte, die zuletzt gewaehlt war - ueberdauert das Schliessen des Fensters */
    private static String category = "";
    /** Nur Fusionen, die auf dem gewaehlten Weg wirklich etwas abwerfen */
    private static boolean onlyProfitable = true;
    /**
     * Nur Fusionen, deren Zutaten im Lager liegen.
     *
     * Beantwortet die andere Frage: nicht "was ist am besten", sondern "was kann ich
     * jetzt machen". Verlangt einen Blick in die Hunting-Box - ohne ihn bleibt der
     * Schalter wirkungslos, und das Fenster sagt das auch.
     */
    private static boolean onlyOwned = false;
    /** Wonach gesucht wird - leer heisst: alles */
    private static String search = "";
    /**
     * Sucht nur in den Zutaten, nicht im Ergebnis.
     *
     * Zwei verschiedene Fragen: "was kann ich aus diesem Shard machen" und "wie komme
     * ich an diesen Shard". Wer das erste meint, will die Fusionen nicht sehen, die
     * ihn erst herstellen.
     */
    private static boolean searchInputsOnly = false;

    /**
     * Mehr Zeilen liest niemand durch.
     *
     * Gereiht wird nach Gewinn; was danach kommt, ist entweder schlechter oder
     * gleich gut. Die Schranke haelt auch das Sortieren klein, wenn jemand ohne
     * Suchbegriff und ohne Sparte durch alles blaettert
     */
    private static final int LIMIT = 2000;

    private final Screen parent;
    private EditBox searchBox;
    private List<Row> cached;
    private int page;

    public ShardProfitScreen(Screen parent) {
        super(Component.literal("Shard fusions"));
        this.parent = parent;
    }

    private static ModConfig.FusionCategory cfg() {
        return ModConfig.INSTANCE.hunting.fusion;
    }

    /** Werden die Zutaten sofort gekauft? */
    private static boolean instantBuy() {
        return cfg().ingredientMode == ModConfig.FusionCategory.BuyMode.INSTANT_BUY;
    }

    /** Wird das Ergebnis sofort verkauft? */
    private static boolean instantSell() {
        return cfg().resultMode == ItemValue.PriceMode.INSTANT_SELL;
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 40 - listTop) / ROW_HEIGHT);
    }

    /**
     * Die Auswahl, festgehalten.
     *
     * Dahinter laufen alle 128.000 Kombinationen durch. Das dauert Millisekunden -
     * aber das Fenster fragt beim Zeichnen mehrfach nach Zeilenzahl, Seitenzahl und
     * Inhalt, und dreimal je Bild waere aus Millisekunden ein Ruckeln geworden.
     * Neu gerechnet wird nur, wenn sich an den Vorgaben etwas geaendert hat.
     */
    private List<Row> visible() {
        if (cached == null) {
            cached = ShardProfitData.select(
                    category, search, searchInputsOnly,
                    onlyOwned ? ShardStock.counts() : null,
                    onlyProfitable, instantBuy(), instantSell(), LIMIT);
        }
        return cached;
    }

    /** Nach jeder Aenderung an den Vorgaben gilt die alte Auswahl nicht mehr */
    private void invalidate() {
        cached = null;
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
        sparten.addAll(ShardProfitData.categories());

        int x = left;
        for (String sparte : sparten) {
            String beschriftung = sparte.isEmpty() ? "All" : pretty(sparte);
            int breite = Math.max(38, font.width(beschriftung) + 12);
            // Was nicht mehr in die Zeile passt, kommt in die naechste
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
                        invalidate();
                        rebuild();
                    }).bounds(x, y, breite, 18).build();
            knopf.active = !gewaehlt;
            addRenderableWidget(knopf);
            x += breite + 2;
        }

        // Unter dem letzten Reiter faengt die Liste an, mit Platz fuer das Suchfeld
        // und die Spaltenkoepfe darunter
        int sucheY = y + 24;
        listTop = y + 62;

        searchBox = new EditBox(font, left, sucheY, 200, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search a shard ..."));
        searchBox.setMaxLength(40);
        searchBox.setValue(search);
        searchBox.setResponder(text -> {
            search = text == null ? "" : text.trim();
            page = 0;
            invalidate();
        });
        addRenderableWidget(searchBox);

        // Wo gesucht wird: ueberall, oder nur in den Zutaten
        addRenderableWidget(Button.builder(
                Component.literal(searchInputsOnly ? "as ingredient" : "anywhere")
                        .withStyle(ChatFormatting.AQUA),
                button -> {
                    searchInputsOnly = !searchInputsOnly;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 206, sucheY, 100, 18).build());

        // Nur was im Lager liegt. Der Schalter bleibt sichtbar, auch wenn noch nie
        // in die Box gesehen wurde - sonst sucht man ihn und findet ihn nicht
        addRenderableWidget(Button.builder(
                Component.literal((onlyOwned ? "☑" : "☐") + " Only what I have")
                        .withStyle(ShardStock.known() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY),
                button -> {
                    onlyOwned = !onlyOwned;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 310, sucheY, 140, 18).build());

        int unten = height - 28;
        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left, unten, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 24, unten, 20, 20).build());
        }

        // Die beiden Handelswege, jeder fuer sich umschaltbar. Sie stehen hier und
        // nicht nur in den Einstellungen, weil man sie beim Lesen der Liste vergleicht
        addRenderableWidget(Button.builder(
                Component.literal("Buy: " + cfg().ingredientMode).withStyle(ChatFormatting.AQUA),
                button -> {
                    cfg().ingredientMode =
                            instantBuy() ? ModConfig.FusionCategory.BuyMode.BUY_ORDER
                                         : ModConfig.FusionCategory.BuyMode.INSTANT_BUY;
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 52, unten, 130, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Sell: " + cfg().resultMode).withStyle(ChatFormatting.GREEN),
                button -> {
                    cfg().resultMode = instantSell() ? ItemValue.PriceMode.SELL_ORDER
                                                     : ItemValue.PriceMode.INSTANT_SELL;
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 186, unten, 130, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal((onlyProfitable ? "☑" : "☐") + " Profit only"), button -> {
                    onlyProfitable = !onlyProfitable;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 320, unten, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + LIST_WIDTH - 80, unten, 80, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int centerX = width / 2;

        graphics.centeredText(font, Component.literal("Shard fusions")
                .withStyle(ChatFormatting.GOLD), centerX, 14, 0xFFFFAA00);

        if (!ShardProfitData.ready()) {
            graphics.centeredText(font, Component.literal("Loading the list ...")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        // Was der Bestandsschalter gerade bedeutet - sonst wundert man sich ueber
        // eine leere Liste
        if (onlyOwned) {
            String hinweis;
            int farbe;
            if (!ShardStock.known()) {
                hinweis = "Open your shard box once - nothing counted yet";
                farbe = 0xFFFFAA00;
            } else {
                // Nicht nur, wie viele gezaehlt wurden, sondern wie viele davon einem
                // Shard zugeordnet werden konnten. Die beiden Zahlen trennen "die Box
                // ist leer" von "die Namen passen nicht zusammen" - ohne sie sieht
                // beides gleich aus: eine leere Liste
                int erkannt = ShardProfitData.matched(ShardStock.counts());
                hinweis = erkannt + " of " + ShardStock.kinds() + " matched, seen "
                        + ShardStock.age();
                farbe = erkannt == 0 ? 0xFFFF5555 : 0xFF55FF55;
            }
            graphics.text(font, hinweis, left + 456, listTop - 34, farbe, false);
        }

        String stand = ShardProfitData.age();
        graphics.centeredText(font, Component.literal(
                        rowCount() + " of " + ShardProfitData.combinations()
                        + " combinations shown"
                        + (stand.isEmpty() ? "" : " - prices " + stand))
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF888888);

        graphics.text(font, "Fusion", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Result", left + 210, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Cost", left + 320, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Sells for", left + 380, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 444, listTop - 12, 0xFF888888, false);

        boolean kaufen = instantBuy();
        boolean verkaufen = instantSell();
        List<Row> zeilen = visible();
        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            long gewinn = row.profit(kaufen, verkaufen);
            graphics.text(font, cut(row.recipe(), 40), left + 4, y, 0xFFCCCCCC, false);
            graphics.text(font, cut(row.yield(), 17), left + 210, y, colour(row.rarity()), false);
            graphics.text(font, ItemValue.format(row.cost(kaufen)), left + 320, y, 0xFFFF7777, false);
            graphics.text(font, ItemValue.format(row.revenue(verkaufen)), left + 380, y, 0xFFAAAAFF, false);
            graphics.text(font, ItemValue.format(gewinn), left + 444, y,
                    gewinn > 0 ? 0xFF55FF55 : 0xFFFF5555, false);
        }

        drawFooter(graphics, centerX);
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int centerX) {
        if (rowCount() == 0) {
            graphics.centeredText(font, Component.literal(
                            "Nothing pays off on this route right now - try the other one")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 22, 0xFFAAAAAA);
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
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /**
     * Die Liste soll sich mit dem Suchtext sofort mitbewegen.
     *
     * Der Responder setzt nur den Text; die Seitenzahl und die Knoepfe darunter haengen
     * aber an der Trefferzahl. Neu gebaut wird erst hier, nach dem Tastendruck - waehrend
     * des Responders waere das Feld selbst noch in Arbeit.
     */
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
}
