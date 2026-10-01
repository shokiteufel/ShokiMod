package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DayRecord;
import com.shokiteufel.shokimod.handler.ProfitTracker;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Was die vergangenen Tage gebracht haben.
 *
 * Der Tageswert im Kasten faengt jeden Morgen wieder bei null an, und bis hierher war das
 * ein Verlust: Wer wissen wollte, ob die gestrige Runde sich gelohnt hat, konnte nur
 * raten. Beim Tageswechsel wandert der Stand jetzt ins Archiv, und hier steht er.
 *
 * Gerechnet wird mit den Preisen von heute, nicht mit denen von damals. Gespeichert sind
 * Stueckzahlen; was sie wert sind, entscheidet der Markt, auf dem man sie verkauft - und
 * das ist der von jetzt.
 */
public class DayProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 420;
    private static final int LIST_TOP = 60;
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy");

    private final Screen parent;
    private int page;

    public DayProfitScreen(Screen parent) {
        super(Component.literal("Day profit"));
        this.parent = parent;
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 60 - LIST_TOP) / ROW_HEIGHT);
    }

    /** Der laufende Tag zuerst, danach die abgeschlossenen */
    private List<DayRecord> days() {
        List<DayRecord> out = new ArrayList<>();
        ModConfig.ProfitCategory cfg = ProfitTracker.cfg();
        if (!cfg.dayCounts.isEmpty()) {
            out.add(new DayRecord(cfg.dayStartedAt, cfg.dayUptimeMillis, cfg.dayCounts));
        }
        out.addAll(ProfitTracker.history());
        return out;
    }

    private int pageCount() {
        return Math.max(1, (days().size() + perPage() - 1) / perPage());
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

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + LIST_WIDTH - 90, unten, 90, 20).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    /** Ein Klick auf eine Zeile zeigt, woraus der Tag bestand */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;

        List<DayRecord> tage = days();
        int start = page * perPage();
        int left = left();
        if (event.y() >= LIST_TOP - 3 && event.x() >= left && event.x() <= left + LIST_WIDTH) {
            int index = (int) ((event.y() - LIST_TOP + 3) / ROW_HEIGHT);
            if (index >= 0 && index < perPage() && start + index < tage.size()) {
                DayRecord tag = tage.get(start + index);
                if (minecraft != null) minecraft.setScreen(new DayItemScreen(this, tag, label(tag)));
                return true;
            }
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int centerX = width / 2;

        graphics.centeredText(font, Component.literal("Day profit").withStyle(ChatFormatting.GOLD),
                centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, Component.literal(
                        "Priced as of now - click a day to see what it was made of")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF888888);

        graphics.text(font, "Day", left + 4, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Items", left + 170, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Active", left + 240, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Worth", left + 320, LIST_TOP - 12, 0xFF888888, false);

        List<DayRecord> tage = days();
        if (tage.isEmpty()) {
            graphics.centeredText(font, Component.literal("No day has finished yet")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < tage.size(); i++) {
            DayRecord tag = tage.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            boolean laeuft = start + i == 0 && tag.start == ProfitTracker.cfg().dayStartedAt
                    && !ProfitTracker.cfg().dayCounts.isEmpty();
            graphics.text(font, label(tag) + (laeuft ? " (today)" : ""), left + 4, y,
                    laeuft ? 0xFFFFAA00 : 0xFFFFFFFF, false);
            graphics.text(font, String.valueOf(tag.counts.size()), left + 170, y, 0xFFCCCCCC, false);
            graphics.text(font, clock(tag.uptimeMillis), left + 240, y, 0xFF55FFFF, false);
            graphics.text(font, ItemValue.format(ProfitTracker.worthOf(tag)), left + 320, y, 0xFF55FF55, false);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    /** "Mon, 29.09.2026" - der Tag, an dem die Zaehlung begann */
    static String label(DayRecord tag) {
        if (tag.start <= 0) return "unknown day";
        return TAG.format(Instant.ofEpochMilli(tag.start).atZone(ZoneId.systemDefault()));
    }

    static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
