package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig.DayRecord;
import com.shokiteufel.shokimod.handler.ProfitTracker;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Woraus ein einzelner Tag bestand - eine Zeile je Ware, die wertvollste zuerst.
 *
 * Dasselbe Bild wie im Kasten, nur fuer einen Tag, der schon vorbei ist. Gerechnet wird
 * mit den Preisen von heute: Gespeichert sind Stueckzahlen, und was sie einbringen,
 * entscheidet der Markt, auf dem man sie verkauft.
 */
public class DayItemScreen extends Screen {

    private static final int ROW_HEIGHT = 18;
    private static final int LIST_WIDTH = 420;
    private static final int LIST_TOP = 60;

    /** Eine Zeile: Ware, Stueckzahl, Wert */
    private record Row(String itemId, String name, int count, double worth) {
    }

    private final Screen parent;
    private final DayRecord tag;
    private final String titel;
    private int page;

    public DayItemScreen(Screen parent, DayRecord tag, String titel) {
        super(Component.literal("Day profit"));
        this.parent = parent;
        this.tag = tag;
        this.titel = titel;
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 60 - LIST_TOP) / ROW_HEIGHT);
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tag.counts.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            // Ein verbrauchter Koeder steht im Minus - das gehoert in die Liste
            if (count == 0 || (count < 0 && !ProfitTracker.isBait(entry.getKey()))) continue;

            double unit = ProfitTracker.unitPrice(entry.getKey());
            out.add(new Row(entry.getKey(), ProfitTracker.nameOf(entry.getKey()), count,
                    unit > 0 ? unit * count : 0));
        }
        out.sort(Comparator.comparingDouble(Row::worth).reversed().thenComparing(Row::name));
        return out;
    }

    private int pageCount() {
        return Math.max(1, (rows().size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);
        int unten = height - 30;
        int left = left();

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

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(left + LIST_WIDTH - 90, unten, 90, 20).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int centerX = width / 2;

        List<Row> zeilen = rows();
        double gesamt = 0;
        for (Row row : zeilen) gesamt += row.worth();

        graphics.centeredText(font, Component.literal(titel).withStyle(ChatFormatting.GOLD),
                centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, Component.literal(
                        ItemValue.format(gesamt) + " in " + DayProfitScreen.clock(tag.uptimeMillis)
                        + " - priced as of now")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF888888);

        graphics.text(font, "Item", left + 4, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Count", left + 240, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Worth", left + 330, LIST_TOP - 12, 0xFF888888, false);

        if (zeilen.isEmpty()) {
            graphics.centeredText(font, Component.literal("Nothing was counted on this day")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, row.name(), left + 4, y, ProfitTracker.colourOf(row.itemId()), false);
            graphics.text(font, "x" + row.count(), left + 240, y, 0xFFCCCCCC, false);
            graphics.text(font, row.worth() != 0 ? ItemValue.format(row.worth()) : "?",
                    left + 330, y, row.worth() > 0 ? 0xFF55FF55 : row.worth() < 0 ? 0xFFFF5555 : 0xFF888888, false);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
