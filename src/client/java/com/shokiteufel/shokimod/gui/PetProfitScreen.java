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
    /** Nur frisch geschluepfte Pets - die ohne Vorgeschichte */
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

    /** Die Zeilen der gewaehlten Sparte, beste zuerst */
    private List<Row> visible() {
        List<Row> out = new ArrayList<>();
        for (Row row : PetProfitData.rows()) {
            if (!category.isEmpty() && !category.equals(row.category())) continue;
            if (onlyLevelOne && row.level() != 1) continue;
            if (noCandy && row.candy() > 0) continue;
            out.add(row);
        }
        return out;
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

        String stand = PetProfitData.age();
        graphics.centeredText(font, Component.literal(
                        PetProfitData.auctionsSeen() + " auctions checked"
                        + (stand.isEmpty() ? "" : " - updated " + stand)
                        + " - click a row to open the auction")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF888888);

        // Spaltenkoepfe: dieselben x-Werte wie die Zeilen darunter, damit beides nie
        // auseinanderlaeuft
        graphics.text(font, "Pet", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Lvl", left + 186, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Buy", left + 216, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 280, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Lv100/200 Price", left + 344, listTop - 12, 0xFF888888, false);
        graphics.text(font, "per XP", left + 424, listTop - 12, 0xFF888888, false);

        List<Row> zeilen = visible();
        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, cut(row.name(), 30), left + 4, y, colour(row.rarity()), false);
            graphics.text(font, String.valueOf(row.level()), left + 186, y, 0xFFCCCCCC, false);
            graphics.text(font, ItemValue.format(row.price()), left + 216, y, 0xFFCCCCCC, false);
            graphics.text(font, ItemValue.format(row.profit()), left + 280, y, 0xFF55FF55, false);
            // Was dasselbe Pet fertig kostet. Die Drachen gehen bis 200, dort steht
            // zusaetzlich der Preis eines Hundertsten - da steigen die meisten ein
            String ziel = row.twoTargets()
                    ? ItemValue.format(row.price100()) + "/" + ItemValue.format(row.targetPrice())
                    : ItemValue.format(row.targetPrice());
            graphics.text(font, ziel, left + 344, y, 0xFF55FFFF, false);
            graphics.text(font, String.format(Locale.US, "%.2f", row.perXp()), left + 424, y, 0xFFFFAA00, false);
        }

        drawFooter(graphics, centerX);
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int centerX) {
        if (rowCount() == 0) {
            graphics.centeredText(font, Component.literal("Nothing worth levelling here right now")
                    .withStyle(ChatFormatting.GRAY), centerX, listTop + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            // Mittig zwischen den beiden Pfeilen und ueber der Knopfreihe. Vorher lag
            // sie bei height-22 und damit mitten im Knopf "No pet candy"
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
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
