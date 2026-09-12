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
 * nicht nur das eine, das es herstellt.
 *
 * Ein Klick auf eine Zeile oeffnet das Rezept im Spiel, mit dem sich das Ding gleich
 * bauen laesst.
 *
 * Die Preise kommen aus {@link ItemValue} und {@link com.shokiteufel.shokimod.util.BazaarLive}:
 * Bazaar, wo es einen gibt, sonst der Tiefstpreis im Auktionshaus. Was sich gar
 * nicht bewerten laesst, steht unten und sagt das auch.
 */
public class CraftProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    // Breiter geworden mit der Forge: Zeit und Stundenlohn sind zwei Spalten, die
    // es vorher nicht gab, und beide zu quetschen haette die Zahlen unleserlich
    // gemacht, auf die es gerade ankommt
    private static final int LIST_WIDTH = 600;
    private static final int LIMIT = 500;

    /** Ueberdauert das Schliessen - wer weitersucht, faengt nicht von vorn an */
    private static String search = "";
    private static boolean onlyProfitable = true;
    /**
     * Wo das Ergebnis verkauft wird.
     *
     * Nicht dasselbe wie sofort oder per Auftrag - das ist die Frage nach dem Haus.
     * Manches laesst sich an beiden Orten losschlagen, und die Preise gehen weit
     * auseinander: Was im Bazaar zu Tausenden gehandelt wird, bringt im Auktionshaus
     * mitunter das Doppelte, dafuer einzeln und mit Wartezeit.
     */
    private static boolean sellToBazaar = true;
    /**
     * Welche Rezeptart die Liste zeigt.
     *
     * Zwei verschiedene Fragen: Am Handwerkstisch steht man einmal und hat es
     * gleich; in der Forge legt man etwas hin und kommt Stunden spaeter wieder.
     */
    private static CraftProfitData.Kind kind = CraftProfitData.Kind.ALL;
    /** Nach Gewinn je Stunde reihen statt nach Gewinn */
    private static boolean perHour = false;

    private final Screen parent;
    private EditBox searchBox;
    private List<Row> cached;
    /** Wie alt der Live-Stand beim letzten Bild war - fuer das Neuberechnen */
    private int lastLiveAge = -1;
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
            cached = CraftProfitData.select(search, instantBuy(), instantSell(),
                    sellToBazaar, onlyProfitable, kind, perHour,
                    cfg().quickForgePercent, LIMIT);
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
                Component.literal(sellToBazaar ? "Sell to BZ" : "Sell to AH")
                        .withStyle(ChatFormatting.AQUA),
                button -> {
                    sellToBazaar = !sellToBazaar;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 226, sucheY, 100, 18).build());

        addRenderableWidget(Button.builder(
                Component.literal((onlyProfitable ? "☑" : "☐") + " Profit"),
                button -> {
                    onlyProfitable = !onlyProfitable;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 330, sucheY, 80, 18).build());

        // Handwerkstisch oder Forge. Wer wissen will, was sich ueber Nacht lohnt,
        // will die zweitausendfuenfhundert Handwerksrezepte nicht dazwischen haben
        addRenderableWidget(Button.builder(
                Component.literal("Type: " + kind.label).withStyle(ChatFormatting.GOLD),
                button -> {
                    CraftProfitData.Kind[] alle = CraftProfitData.Kind.values();
                    kind = alle[(kind.ordinal() + 1) % alle.length];
                    // In der Forge ist der Stundenlohn die Zahl, auf die es ankommt -
                    // also stellt sich die Reihung mit um, statt darauf zu warten,
                    // dass jemand den zweiten Knopf findet
                    perHour = kind == CraftProfitData.Kind.FORGE_ONLY;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 414, sucheY, 86, 18).build());

        Button reihung = Button.builder(
                Component.literal(perHour ? "Sort: Coins/h" : "Sort: Profit")
                        .withStyle(ChatFormatting.AQUA),
                button -> {
                    perHour = !perHour;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 504, sucheY, 96, 18).build();
        // Der Hinweis gehoert dazu, sonst ist die Liste irrefuehrend: Ganz oben
        // stehen die Rezepte von dreissig Sekunden, und ihr Stundenlohn geht in die
        // Milliarden - aber ihre Teile entstehen anderswo und brauchen selbst Tage.
        // Die Zahl stimmt, sie ist nur keine Verdienstmoeglichkeit
        reihung.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Coins per hour of forge time. The thirty-second "
                        + "recipes lead this list because their parts are forged "
                        + "elsewhere - the number is right, the loop is not.")));
        addRenderableWidget(reihung);

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

        // Die eigene Forge laeuft schneller, wenn der Perk sitzt. Der Schritt von
        // fuenf reicht: Es geht darum, dass die angezeigte Zeit ungefaehr die eigene
        // ist, nicht um das letzte Prozent
        addRenderableWidget(Button.builder(
                Component.literal("Quick Forge: "
                                + cfg().quickForgePercent + "%")
                        .withStyle(ChatFormatting.GOLD),
                button -> {
                    int jetzt = cfg().quickForgePercent;
                    cfg().quickForgePercent = jetzt >= 30 ? 0 : jetzt + 5;
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 320, unten, 130, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + LIST_WIDTH - 80, unten, 80, 20).build());
    }

    /**
     * Ein Klick auf eine Zeile zeigt das Rezept im Spiel.
     *
     * Hypixel kennt dafuer /recipe mit dem Namen des Gegenstands und macht dabei
     * gleich das Handwerksfenster auf, aus dem sich das Ding bauen laesst. Das
     * Fenster schliesst vorher, sonst laege es darueber - dieselbe Regel wie beim
     * Sprung in eine Auktion.
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // Erst die Knoepfe fragen: Sonst faengt die Liste einen Klick ab, der einem
        // Schalter galt - und statt der Einstellung ginge ein Rezept auf
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!CraftProfitData.ready()) return false;

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
        minecraft.player.connection.sendCommand("recipe " + row.name());
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
        com.shokiteufel.shokimod.util.BazaarLive.wanted();
        // Frische Preise heissen andere Zahlen - eine festgehaltene Auswahl zeigte
        // sonst den Stand vom Oeffnen, waehrend daneben "3s old" steht
        int alter = com.shokiteufel.shokimod.util.BazaarLive.ageSeconds();
        if (alter >= 0 && alter != lastLiveAge) {
            lastLiveAge = alter;
            if (alter <= 1) invalidate();
        }
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
        // Wie frisch die Zahlen sind, gehoert daneben. Beim Bazaar sind es Sekunden,
        // beim Auktionshaus Minuten - das ist ein Unterschied, den man kennen sollte
        boolean live = com.shokiteufel.shokimod.util.BazaarLive.fresh();
        String stand = sellToBazaar
                ? (live ? ", live prices " + com.shokiteufel.shokimod.util.BazaarLive.ageSeconds() + "s old"
                        : ", bazaar prices up to 10 min old")
                : ", auction prices up to 10 min old";
        graphics.centeredText(font, Component.literal(
                        zeilen.size() + " of "
                        + (kind == CraftProfitData.Kind.FORGE_ONLY
                                ? CraftProfitData.forgeCount() + " forge recipes"
                                : String.valueOf(CraftProfitData.recipeCount()))
                        + (sellToBazaar ? " sellable on the bazaar"
                                        : " sellable on the auction house")
                        + stand
                        + (search.isBlank() ? " - type an item to narrow it down"
                                            : " - click a row for /recipe"))
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28,
                sellToBazaar && live ? 0xFF77DD77 : 0xFF888888);

        graphics.text(font, "Craft", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Materials", left + 140, listTop - 12, 0xFF888888, false);
        // Die Forge-Zeit. Ohne sie waere ein Gewinn nichtssagend: Zehn Millionen in
        // dreissig Sekunden und zehn Millionen in fuenfzig Stunden sind nicht
        // dasselbe Geschaeft
        graphics.text(font, "Time", left + 300, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Cost", left + 340, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Sells for", left + 396, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 452, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Coins/h", left + 508, listTop - 12, 0xFF888888, false);
        // Wie viele Stueck taeglich weggehen. Der Gewinn allein sagt nicht, ob sich
        // die Ware ueberhaupt bewegt - wer hundert baut und taeglich fuenf verkauft,
        // sitzt lange darauf
        graphics.text(font, "Sold/day", left + 560, listTop - 12, 0xFF888888, false);

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, cut(row.yield(), 22), left + 4, y, 0xFFFFCC66, false);
            graphics.text(font, cut(row.partsText(), 26), left + 140, y, 0xFFBBBBBB, false);

            // Die Wartezeit. Ein Strich heisst: sofort fertig, es gibt keine
            graphics.text(font, row.seconds() > 0 ? row.durationText() : "-",
                    left + 300, y, row.seconds() > 0 ? 0xFFFFAA00 : 0xFF666666, false);

            if (!row.complete()) {
                // Ohne Preis ist jede Zahl hier eine Behauptung. Lieber ein Strich
                graphics.text(font, "no price", left + 340, y, 0xFF888888, false);
            } else {
                long gewinn = row.profit();
                graphics.text(font, ItemValue.format(row.cost()), left + 340, y, 0xFFFF7777, false);
                graphics.text(font, ItemValue.format(row.revenue()), left + 396, y, 0xFFAAAAFF, false);
                graphics.text(font, ItemValue.format(gewinn), left + 452, y,
                        gewinn > 0 ? 0xFF55FF55 : 0xFFFF5555, false);
                // Der Stundenlohn steht nur, wo gewartet wird. Was sofort fertig ist,
                // hat keinen - und eine Zahl dahin zu schreiben hiesse, zwei Dinge
                // ohne gemeinsame Einheit nebeneinanderzustellen
                if (row.seconds() > 0) {
                    long proStunde = row.profitPerHour();
                    graphics.text(font, ItemValue.format(proStunde), left + 508, y,
                            proStunde > 0 ? 0xFF55FF55 : 0xFFFF5555, false);
                } else {
                    graphics.text(font, "-", left + 508, y, 0xFF666666, false);
                }
            }

            // Der Tagesumsatz. Liegt kein frischer Bazaar-Stand vor, steht hier ein
            // Strich statt einer erfundenen Zahl - der Wochenwert kommt nur mit den
            // Live-Daten, die gespeicherte Datei fuehrt ihn nicht
            long proTag = com.shokiteufel.shokimod.util.BazaarLive.soldPerDay(row.id());
            graphics.text(font, proTag < 0 ? "-" : ItemValue.format(proTag),
                    left + 560, y, mengeFarbe(proTag), false);
        }

        if (zeilen.isEmpty()) {
            graphics.centeredText(font, Component.literal(search.isBlank()
                            ? "Nothing pays off right now - try the other trade route"
                            : "Nothing found for \"" + search + "\"")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            // Mittig zwischen den beiden Pfeilen, auf ihrer Hoehe
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    /**
     * Die Farbe des Tagesumsatzes.
     *
     * Rot heisst: Hier bewegt sich fast nichts, und ein Gewinn auf dem Papier nuetzt
     * wenig, wenn die Ware tagelang liegt. Die Schwellen sind grob und sollen nur
     * den Blick lenken.
     */
    private static int mengeFarbe(long proTag) {
        if (proTag < 0) return 0xFF666666;
        if (proTag < 100) return 0xFFFF5555;
        if (proTag < 1000) return 0xFFFFAA00;
        return 0xFF55FF55;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
