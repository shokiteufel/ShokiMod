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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final int LIST_TOP = 74;
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy");

    /** Welche Zusammenfassung gerade dasteht */
    private enum Tab {
        DAYS("Days"),
        YEARS("SkyBlock year"),
        MAYORS("Mayor");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** Ueberdauert das Schliessen - wer beim Jahr war, will dort weitermachen */
    private static Tab tab = Tab.DAYS;

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
            out.add(new DayRecord(cfg.dayStartedAt, cfg.dayUptimeMillis, cfg.dayCounts,
                    com.shokiteufel.shokimod.util.SkyBlockYear.mayor()));
        }
        out.addAll(ProfitTracker.history());
        return out;
    }

    /**
     * Die Zeilen des gewaehlten Reiters.
     *
     * Jahre und Buergermeister sind zusammengefasste Tage: Ein Buergermeister regiert
     * genau ein SkyBlock-Jahr, beide Listen gehen also ueber dieselben Zeitraeume - die
     * eine nennt die Zahl, die andere den Namen. Der Zeitpunkt einer zusammengefassten
     * Zeile ist der Anfang ihres Jahres, damit die Reihenfolge stimmt.
     */
    private List<DayRecord> rows() {
        if (tab == Tab.DAYS) return days();

        Map<String, DayRecord> gruppen = new LinkedHashMap<>();
        for (DayRecord tag : days()) {
            int jahr = com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(tag.start);
            String key = tab == Tab.YEARS ? String.valueOf(jahr)
                    : (tag.mayor == null || tag.mayor.isBlank() ? "?" : tag.mayor) + "|" + jahr;

            DayRecord ziel = gruppen.get(key);
            if (ziel == null) {
                ziel = new DayRecord(com.shokiteufel.shokimod.util.SkyBlockYear.startOf(jahr),
                        0L, Map.of(), tag.mayor);
                gruppen.put(key, ziel);
            }
            ziel.uptimeMillis += tag.uptimeMillis;
            for (Map.Entry<String, Integer> entry : tag.counts.entrySet()) {
                ziel.counts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            if ((ziel.mayor == null || ziel.mayor.isBlank()) && tag.mayor != null) ziel.mayor = tag.mayor;
        }
        return new ArrayList<>(gruppen.values());
    }

    private int pageCount() {
        return Math.max(1, (rows().size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);
        int unten = height - 30;
        int left = left();

        // Die Reiter: derselbe Knopf, der gerade gilt, ist abgeschaltet
        int x = left;
        for (Tab eintrag : Tab.values()) {
            Button knopf = Button.builder(Component.literal(eintrag.label), button -> {
                tab = eintrag;
                page = 0;
                rebuild();
            }).bounds(x, 40, 130, 20).build();
            knopf.active = tab != eintrag;
            addRenderableWidget(knopf);
            x += 134;
        }

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

        List<DayRecord> tage = rows();
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
                        "Priced as of now - each find counts 30 s of Active - click a row for its items")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF888888);

        graphics.text(font, switch (tab) {
            case DAYS -> "Day";
            case YEARS -> "SkyBlock year";
            case MAYORS -> "Mayor";
        }, left + 4, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Items", left + 170, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Active", left + 240, LIST_TOP - 12, 0xFF888888, false);
        graphics.text(font, "Worth", left + 320, LIST_TOP - 12, 0xFF888888, false);

        List<DayRecord> tage = rows();
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

            boolean laeuft = start + i == 0 && laufend(tag);
            graphics.text(font, label(tag) + (laeuft ? (tab == Tab.DAYS ? " (today)" : " (now)") : ""),
                    left + 4, y, laeuft ? 0xFFFFAA00 : 0xFFFFFFFF, false);
            graphics.text(font, String.valueOf(tag.counts.size()), left + 170, y, 0xFFCCCCCC, false);
            graphics.text(font, clock(tag.uptimeMillis), left + 240, y, 0xFF55FFFF, false);
            graphics.text(font, ItemValue.format(ProfitTracker.worthOf(tag)), left + 320, y, 0xFF55FF55, false);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    /** Laeuft dieser Zeitraum noch? */
    private static boolean laufend(DayRecord tag) {
        ModConfig.ProfitCategory cfg = ProfitTracker.cfg();
        if (cfg.dayCounts.isEmpty()) return false;
        if (tab == Tab.DAYS) return tag.start == cfg.dayStartedAt;
        return com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(tag.start)
                == com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(System.currentTimeMillis());
    }

    /**
     * Die Beschriftung einer Zeile - je Reiter eine andere Frage.
     *
     * Beim Tag das Datum, beim Jahr seine Zahl, beim Buergermeister sein Name und das
     * Jahr dazu. Wer vor dieser Neuerung gespielt hat, hat keinen Namen in den Daten;
     * dort steht ein Fragezeichen statt eines geratenen.
     */
    static String label(DayRecord tag) {
        if (tag.start <= 0) return "unknown day";
        int jahr = com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(tag.start);
        return switch (tab) {
            case DAYS -> TAG.format(Instant.ofEpochMilli(tag.start).atZone(ZoneId.systemDefault()));
            case YEARS -> "Year " + jahr;
            case MAYORS -> (tag.mayor == null || tag.mayor.isBlank() ? "?" : tag.mayor)
                    + "  (Year " + jahr + ")";
        };
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
