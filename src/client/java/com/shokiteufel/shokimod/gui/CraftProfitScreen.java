package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.CraftProfitData;
import com.shokiteufel.shokimod.util.CraftProfitData.Row;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Was sich zu bauen lohnt - Rezepte, bewertet mit den Preisen von jetzt.
 *
 * Gesucht wird in beiden Richtungen: im Ergebnis und in den Zutaten. Wer "Enchanted
 * String" eintippt, will die dreiundzwanzig Rezepte sehen, die es verbrauchen -
 * nicht nur das eine, das es herstellt. Der Schalter daneben schraenkt auf die
 * Zutaten ein, wenn nur diese Richtung gemeint ist.
 *
 * Die Preise kommen aus {@link ItemValue} und {@link com.shokiteufel.shokimod.util.BazaarLive}:
 * Bazaar, wo es einen gibt, sonst der Tiefstpreis im Auktionshaus. Was sich gar
 * nicht bewerten laesst, steht unten und sagt das auch.
 */
public class CraftProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 520;
    private static final int LIMIT = 500;

    /** Ueberdauert das Schliessen - wer weitersucht, faengt nicht von vorn an */
    private static String search = "";
    private static boolean inputsOnly = false;
    private static boolean onlyProfitable = true;

    private final Screen parent;
    private EditBox searchBox;
    private List<Row> cached;
    private int listTop = 74;
    private int page;

    public CraftProfitScreen(Screen parent) {
        super(Component.literal("Craft profit"));
        this.parent = parent;
    }

    private static ModConfig.FusionCategory cfg() {
        return ModConfig.INSTANCE.hunting.fusion;
    }

    /** Dieselbe Wahl wie bei den Fusionen - zwei Schalter fuer dasselbe waeren einer zu viel */
    private static boolean instantBuy() {
        return cfg().ingredientMode == ModConfig.FusionCategory.BuyMode.INSTANT_BUY;
    }

    private static boolean instantSell() {
        return cfg().resultMode == ItemValue.PriceMode.INSTANT_SELL;
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 60 - listTop) / ROW_HEIGHT);
    }

    private List<Row> visible() {
        if (cached == null) {
            cached = CraftProfitData.select(search, inputsOnly, instantBuy(), instantSell(),
                    onlyProfitable, LIMIT);
        }
        return cached;
    }

    private void invalidate() {
        cached = null;
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

        addRenderableWidget(Button.builder(
                Component.literal(inputsOnly ? "as material" : "anywhere")
                        .withStyle(ChatFormatting.AQUA),
                button -> {
                    inputsOnly = !inputsOnly;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 226, sucheY, 100, 18).build());

        addRenderableWidget(Button.builder(
                Component.literal((onlyProfitable ? "☑" : "☐") + " Profit only"),
                button -> {
                    onlyProfitable = !onlyProfitable;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 330, sucheY, 96, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Fusions")
                        .withStyle(ChatFormatting.GRAY),
                button -> minecraft.setScreen(new ShardProfitScreen(parent)))
                .bounds(left + 430, sucheY, 90, 18).build());

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

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + LIST_WIDTH - 80, unten, 80, 20).build());
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
        com.shokiteufel.shokimod.util.BazaarLive.wanted();
        int left = left();
        int centerX = width / 2;

        graphics.centeredText(font, Component.literal("Craft profit")
                .withStyle(ChatFormatting.GOLD), centerX, 14, 0xFFFFAA00);

        if (!CraftProfitData.ready()) {
            graphics.centeredText(font, Component.literal("Loading the recipes ...")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        List<Row> zeilen = visible();
        graphics.centeredText(font, Component.literal(
                        zeilen.size() + " of " + CraftProfitData.recipeCount()
                        + " recipes shown"
                        + (search.isBlank() ? " - type an item to narrow it down" : ""))
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF888888);

        graphics.text(font, "Craft", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Materials", left + 170, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Cost", left + 356, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Sells for", left + 412, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 472, listTop - 12, 0xFF888888, false);

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, cut(row.yield(), 27), left + 4, y, 0xFFFFCC66, false);
            graphics.text(font, cut(row.partsText(), 34), left + 170, y, 0xFFBBBBBB, false);

            if (!row.complete()) {
                // Ohne Preis ist jede Zahl hier eine Behauptung. Lieber ein Strich
                graphics.text(font, "no price", left + 356, y, 0xFF888888, false);
            } else {
                long gewinn = row.profit();
                graphics.text(font, ItemValue.format(row.cost()), left + 356, y, 0xFFFF7777, false);
                graphics.text(font, ItemValue.format(row.revenue()), left + 412, y, 0xFFAAAAFF, false);
                graphics.text(font, ItemValue.format(gewinn), left + 472, y,
                        gewinn > 0 ? 0xFF55FF55 : 0xFFFF5555, false);
            }
        }

        if (zeilen.isEmpty()) {
            graphics.centeredText(font, Component.literal(search.isBlank()
                            ? "Nothing pays off right now - try the other trade route"
                            : "Nothing found for \"" + search + "\"")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 40, 0xFFAAAAAA);
        }
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
