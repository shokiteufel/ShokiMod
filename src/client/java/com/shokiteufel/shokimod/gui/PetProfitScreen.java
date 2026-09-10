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
    private static final int LIST_TOP = 74;
    private static final int LIST_WIDTH = 420;
    /** Die Sparte, die zuletzt gewaehlt war - ueberdauert das Schliessen des Fensters */
    private static String category = "";

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
        return Math.max(1, (height - 40 - LIST_TOP) / ROW_HEIGHT);
    }

    /** Die Zeilen der gewaehlten Sparte, beste zuerst */
    private List<Row> visible() {
        List<Row> out = new ArrayList<>();
        for (Row row : PetProfitData.rows()) {
            if (category.isEmpty() || category.equals(row.category())) out.add(row);
        }
        return out;
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
        int y = 46;

        // "Alle" und je ein Knopf fuer die Sparten, die wirklich vorkommen
        List<String> sparten = new ArrayList<>();
        sparten.add("");
        sparten.addAll(PetProfitData.categories());

        int x = left;
        for (String sparte : sparten) {
            String beschriftung = sparte.isEmpty() ? "All" : pretty(sparte);
            int breite = Math.max(38, font.width(beschriftung) + 12);
            if (x + breite > left + LIST_WIDTH) break;  // was nicht mehr passt, faellt weg
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
        if (PetProfitData.ready()) {
            List<Row> zeilen = visible();
            int start = page * perPage();
            int left = left();
            double x = event.x();
            double y = event.y();
            if (x >= left && x <= left + LIST_WIDTH) {
                int index = (int) ((y - LIST_TOP + 3) / ROW_HEIGHT);
                if (index >= 0 && index < perPage() && start + index < zeilen.size()) {
                    Row row = zeilen.get(start + index);
                    if (!row.auction().isEmpty() && minecraft != null && minecraft.player != null) {
                        minecraft.setScreen(null);
                        minecraft.player.connection.sendCommand("viewauction " + row.auction());
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int centerX = width / 2;

        graphics.centeredText(font, Component.literal("Best pets to level")
                .withStyle(ChatFormatting.GOLD), centerX, 14, 0xFFFFAA00);

        if (!PetProfitData.ready()) {
            graphics.centeredText(font, Component.literal("Loading the list ...")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }

        String stand = PetProfitData.updatedAt();
        graphics.centeredText(font, Component.literal(
                        PetProfitData.auctionsSeen() + " auctions checked"
                        + (stand.isEmpty() ? "" : " - " + stand)
                        + " - click a row to open the auction")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF888888);

        // Spaltenkoepfe: dieselben x-Werte wie die Zeilen darunter, damit beides nie
        // auseinanderlaeuft
        graphics.text(font, "Pet", left + 4, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Lvl", left + 196, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Buy", left + 226, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 296, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "per XP", left + 372, LIST_TOP - 12, 0xFF888888, false);

        List<Row> zeilen = visible();
        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, cut(row.name(), 30), left + 4, y, colour(row.rarity()), false);
            graphics.text(font, String.valueOf(row.level()), left + 196, y, 0xFFCCCCCC, false);
            graphics.text(font, ItemValue.format(row.price()), left + 226, y, 0xFFCCCCCC, false);
            graphics.text(font, ItemValue.format(row.profit()), left + 296, y, 0xFF55FF55, false);
            graphics.text(font, String.format(Locale.US, "%.2f", row.perXp()), left + 372, y, 0xFFFFAA00, false);
        }

        if (zeilen.isEmpty()) {
            graphics.centeredText(font, Component.literal("Nothing worth levelling here right now")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 20, 0xFFAAAAAA);
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
}
