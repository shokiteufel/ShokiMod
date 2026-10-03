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
        if (minecraft != null) minecraft.setScreenAndShow(parent);
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
        minecraft.setScreenAndShow(null);
        minecraft.player.connection.sendCommand("bz " + row.name());
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

        graphics.centeredText(font, Component.literal("Bazaar flips")
                .withStyle(ChatFormatting.GOLD), centerX, 14, 0xFFFFAA00);

        if (!BazaarLive.fresh()) {
            graphics.centeredText(font, Component.literal("Fetching the bazaar ...")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        List<Row> zeilen = visible();
        graphics.centeredText(font, Component.literal(
                        zeilen.size() + " goods worth the spread, live prices "
                        + BazaarLive.ageSeconds() + "s old"
                        + " - buy order and sell offer each outbid by " + BazaarFlips.STEP
                        + ", tax taken off - click a row for /bz")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF77DD77);

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
            graphics.centeredText(font, Component.literal(
                            search.isBlank() ? "Nothing clears the tax at this daily amount"
                                             : "Nothing found for \"" + search + "\"")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, cut(row.name(), 30), left + 4, y, 0xFFFFCC66, false);
            graphics.text(font, BazaarFlips.coins(row.buyOrder()), left + 190, y, 0xFFFF7777, false);
            graphics.text(font, BazaarFlips.coins(row.sellOffer()), left + 254, y, 0xFFAAAAFF, false);
            graphics.text(font, BazaarFlips.coins(row.profit()), left + 318, y, 0xFF55FF55, false);
            graphics.text(font, String.format(Locale.ROOT, "%.1f%%", row.margin()),
                    left + 374, y, 0xFF55FFFF, false);
            graphics.text(font, ItemValue.format(row.bought()), left + 428, y, 0xFFCCCCCC, false);
            graphics.text(font, ItemValue.format(row.sold()), left + 500, y, 0xFFCCCCCC, false);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
