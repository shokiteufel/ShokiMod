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
 * mit den Preisen von damals, wo das Log sie festhaelt (siehe ProfitLog); eine Ware ohne
 * festgehaltenen Preis zaehlt mit dem von heute.
 */
public class DayItemScreen extends Screen {

    private static final int ROW_HEIGHT = 18;
    private static final int LIST_WIDTH = 420;
    private static final int LIST_TOP = 60;

    /**
     * Eine Zeile: Ware, Stueckzahl, Wert - dazu die Texte und Farben, die das Bild zeichnet. Die werden erst gefuellt, wenn die
     * Zeile auf der gezeigten Seite steht, und dann einmal je Neuaufbau der Liste (nicht in jedem Bild): "x5" und der Wert als
     * Zeichenkette (String.format) und die Farbe der Ware (ein regulaerer Ausdruck, dazu mehrere Tabellen).
     */
    private static final class Row {
        private final String itemId;
        private final String name;
        private final int count;
        private final double worth;
        private boolean angezeigt;
        private String countText;
        private String worthText;
        private int nameColour;
        private int worthColour;

        Row(String itemId, String name, int count, double worth) {
            this.itemId = itemId;
            this.name = name;
            this.count = count;
            this.worth = worth;
        }

        String name() {
            return name;
        }

        double worth() {
            return worth;
        }

        void anzeigeFuellen() {
            if (angezeigt) return;
            countText = "x" + count;
            worthText = worth != 0 ? ItemValue.format(worth) : "?";
            worthColour = worth > 0 ? 0xFF55FF55 : worth < 0 ? 0xFFFF5555 : 0xFF888888;
            nameColour = ProfitTracker.colourOf(itemId);
            angezeigt = true;
        }
    }

    private final Screen parent;
    private final DayRecord tag;
    /** Die Ueberschrift (Datum, Jahr, Amtszeit) in Gold - der Titel steht fest, also einmal gebaut */
    private final Component titelText;
    private int page;
    /**
     * "12.3m in 5h 20m - priced at ..." - neu gebaut nur, wenn sich die Summe aendert. Dauer und Vermerk gehoeren zu {@code tag}, einem
     * Satz, der beim Oeffnen des Fensters feststeht (das Log ersetzt Saetze durch Kopien, es aendert keinen an Ort und Stelle).
     */
    private final FensterText.Gemerkt untertitel = new FensterText.Gemerkt();

    public DayItemScreen(Screen parent, DayRecord tag, String titel) {
        super(Component.literal("Day profit"));
        this.parent = parent;
        this.tag = tag;
        this.titelText = Component.literal(titel).withStyle(ChatFormatting.GOLD);
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 60 - LIST_TOP) / ROW_HEIGHT);
    }

    /**
     * Die Zeilen, kurz gemerkt: Name und Preis je Ware und eine Sortierung bei jedem Bild waeren bei hunderten Waren
     * Millisekunden auf dem Zeichenfaden. Der Tag, der schon vorbei ist, aendert sich nicht; nur Preise von heute (fuer
     * Waren ohne festgehaltenen) und Namen, die nachgeladen werden, koennen sich bewegen - eine Sekunde reicht dafuer.
     */
    private List<Row> rowsMemo = null;
    private long rowsMemoAt = 0L;
    /** Die Summe aller Zeilen von {@link #rowsMemo} - gerechnet beim Neuaufbau der Liste, nicht in jedem Bild */
    private double rowsGesamt;

    private List<Row> rows() {
        long now = System.currentTimeMillis();
        if (rowsMemo != null && now - rowsMemoAt < 1_000L) return rowsMemo;

        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tag.counts.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            // Ein verbrauchter Koeder steht im Minus - das gehoert in die Liste
            if (count == 0 || (count < 0 && !ProfitTracker.isBait(entry.getKey()))) continue;

            out.add(new Row(entry.getKey(), ProfitTracker.nameOf(entry.getKey()), count,
                    ProfitTracker.worthOf(tag, entry.getKey(), count)));
        }
        out.sort(Comparator.comparingDouble(Row::worth).reversed().thenComparing(Row::name));
        // Summiert in der Reihenfolge der sortierten Liste, wie das Bild es vorher je Bild tat
        double summe = 0;
        for (Row row : out) summe += row.worth();
        rowsMemo = out;
        rowsGesamt = summe;
        rowsMemoAt = now;
        return out;
    }

    /**
     * Alles, was das Bild an gemerkten Texten braucht, und die Zeilen dazu: den Untertitel (neu gebaut nur, wenn sich die Summe
     * aendert) und die Anzeigetexte der Zeilen auf der gezeigten Seite (gefuellt nur einmal je Neuaufbau der Liste).
     */
    private List<Row> vorbereiten() {
        List<Row> zeilen = rows();
        long schluessel = Double.doubleToLongBits(rowsGesamt);
        if (!untertitel.gilt(schluessel, tag.note)) {
            untertitel.setze(schluessel, tag.note, Component.literal(
                            ItemValue.format(rowsGesamt) + " in " + DayProfitScreen.clock(tag.uptimeMillis)
                            + (tag.note == null || tag.note.isBlank()
                                    ? " - priced at the day's prices"
                                    : " - older days priced at update time"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            zeilen.get(start + i).anzeigeFuellen();
        }
        return zeilen;
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

        List<Row> zeilen = vorbereiten();

        // Titel und Untertitel liegen fertig in Feldern; feste Texte kommen aus dem Zwischenspeicher
        graphics.centeredText(font, titelText, centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, untertitel.component(), centerX, 28, 0xFF888888);

        graphics.text(font, "Item", left + 4, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Count", left + 240, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Worth", left + 330, LIST_TOP - 12, 0xFF888888, false);

        if (zeilen.isEmpty()) {
            graphics.centeredText(font, FensterText.grau("Nothing was counted on this day"),
                    centerX, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < zeilen.size(); i++) {
            Row row = zeilen.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, row.name(), left + 4, y, row.nameColour, false);
            graphics.text(font, row.countText, left + 240, y, 0xFFCCCCCC, false);
            graphics.text(font, row.worthText, left + 330, y, row.worthColour, false);
        }

        if (pageCount() > 1) {
            // "2 / 5" wird nur neu gebaut, wenn sich Seite oder Seitenzahl aendern
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
