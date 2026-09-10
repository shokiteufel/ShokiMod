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
    private static final int LIST_WIDTH = 420;
    /** Die Sparte, die zuletzt gewaehlt war - ueberdauert das Schliessen des Fensters */
    private static String category = "";
    /** Falsch = was sich zu leveln lohnt, wahr = was ein fertiges Exemplar kostet */
    private static boolean showReady = false;

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

    /** Die Zeilen der gewaehlten Sparte, beste zuerst */
    private List<Row> visible() {
        List<Row> out = new ArrayList<>();
        for (Row row : PetProfitData.rows()) {
            if (category.isEmpty() || category.equals(row.category())) out.add(row);
        }
        return out;
    }

    /** Dasselbe fuer die Preisliste der fertigen Exemplare */
    private List<PetProfitData.Ready> visibleReady() {
        List<PetProfitData.Ready> out = new ArrayList<>();
        for (PetProfitData.Ready r : PetProfitData.readyPets()) {
            if (category.isEmpty() || category.equals(r.category())) out.add(r);
        }
        return out;
    }

    private int rowCount() {
        return showReady ? visibleReady().size() : visible().size();
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

        // Unter dem letzten Reiter faengt die Liste an
        listTop = y + 28;

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
                Component.literal(showReady ? "View: level 100 prices" : "View: worth levelling"),
                button -> {
                    showReady = !showReady;
                    page = 0;
                    rebuild();
                }).bounds(left + 52, unten, 170, 20).build());

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
            List<?> zeilen = showReady ? visibleReady() : visible();
            int left = left();
            double x = event.x();
            double y = event.y();
            if (x >= left && x <= left + LIST_WIDTH) {
                int index = (int) ((y - listTop + 3) / ROW_HEIGHT);
                if (index >= 0 && index < perPage() && start + index < zeilen.size()) {
                    String auktion = showReady
                            ? visibleReady().get(start + index).auction()
                            : visible().get(start + index).auction();
                    if (!auktion.isEmpty() && minecraft != null && minecraft.player != null) {
                        minecraft.setScreen(null);
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

        graphics.centeredText(font, Component.literal("Best pets to level")
                .withStyle(ChatFormatting.GOLD), centerX, 14, 0xFFFFAA00);

        if (!PetProfitData.ready()) {
            graphics.centeredText(font, Component.literal("Loading the list ...")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
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
        graphics.text(font, "Pet", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Lvl", left + 196, listTop - 12, 0xFF888888, false);
        if (showReady) {
            graphics.text(font, "Rarity", left + 226, listTop - 12, 0xFF888888, false);
            graphics.text(font, "Cheapest", left + 330, listTop - 12, 0xFF888888, false);
            drawReady(graphics, left);
            drawFooter(graphics, centerX);
            return;
        }
        graphics.text(font, "Buy", left + 226, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 296, listTop - 12, 0xFF888888, false);
        graphics.text(font, "per XP", left + 372, listTop - 12, 0xFF888888, false);

        List<Row> zeilen = visible();
        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, cut(row.name(), 30), left + 4, y, colour(row.rarity()), false);
            graphics.text(font, String.valueOf(row.level()), left + 196, y, 0xFFCCCCCC, false);
            graphics.text(font, ItemValue.format(row.price()), left + 226, y, 0xFFCCCCCC, false);
            graphics.text(font, ItemValue.format(row.profit()), left + 296, y, 0xFF55FF55, false);
            graphics.text(font, String.format(Locale.US, "%.2f", row.perXp()), left + 372, y, 0xFFFFAA00, false);
        }

        drawFooter(graphics, centerX);
    }

    /** Die Preisliste der fertigen Exemplare */
    private void drawReady(GuiGraphicsExtractor graphics, int left) {
        List<PetProfitData.Ready> zeilen = visibleReady();
        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            PetProfitData.Ready row = zeilen.get(start + i);
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, cut(row.name(), 30), left + 4, y, colour(row.rarity()), false);
            graphics.text(font, String.valueOf(row.level()), left + 196, y, 0xFFCCCCCC, false);
            graphics.text(font, pretty(row.rarity()), left + 226, y, colour(row.rarity()), false);
            graphics.text(font, ItemValue.format(row.price()), left + 330, y, 0xFF55FF55, false);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int centerX) {
        if (rowCount() == 0) {
            graphics.centeredText(font, Component.literal(showReady
                            ? "No finished pets on the auction house right now"
                            : "Nothing worth levelling here right now")
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
}
