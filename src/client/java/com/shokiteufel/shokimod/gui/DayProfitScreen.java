package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DayRecord;
import com.shokiteufel.shokimod.handler.ProfitLog;
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
 * Gerechnet wird mit den Preisen von damals: Das Log (siehe ProfitLog) haelt zu jeder Ware fest, was sie am
 * Tag der Funde wert war. Nur wo kein Preis festgehalten ist - Tage von vor dem Log, oder kein Preis bekannt -,
 * gilt der von heute. Bis 2.0.5 galt fuer alle Tage der von heute.
 */
public class DayProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_WIDTH = 420;
    private static final int LIST_TOP = 74;
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy");
    /** Fuer Zeitraeume: ohne Wochentag, der sagt dort nichts */
    private static final DateTimeFormatter KURZ = DateTimeFormatter.ofPattern("dd.MM.");
    /** Nur die Uhrzeit - fuer Saetze, die nicht zur Reset-Stunde angefangen haben */
    private static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("HH:mm");

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
        return allDays();
    }

    /** Dasselbe ohne Fenster - fuer den Sprung aus dem Chat in ein bestimmtes Jahr */
    static List<DayRecord> allDays() {
        List<DayRecord> out = new ArrayList<>();
        ModConfig.ProfitCategory cfg = ProfitTracker.cfg();
        if (!cfg.dayCounts.isEmpty()) {
            // Mit den Preisen, die der Tag beim Abschluss festhaelt - damit die Zahl um sechs Uhr nicht springt
            out.add(new DayRecord(cfg.dayStartedAt, cfg.dayUptimeMillis, cfg.dayCounts,
                    com.shokiteufel.shokimod.util.SkyBlockYear.mayor(),
                    ProfitTracker.frozenCoins(cfg.dayCounts, cfg.dayCoins)));
        }
        out.addAll(ProfitTracker.history());
        return out;
    }

    /**
     * Alles, was in einem SkyBlock-Jahr zusammenkam - oder null, wenn davon nichts steht.
     *
     * Gebraucht fuer den Sprung aus der Zeile, die beim Jahreswechsel im Chat steht:
     * Sie fuehrt nicht in die Liste der Jahre, sondern gleich in die Waren des eben
     * abgelaufenen.
     */
    public static DayRecord yearRecord(int jahr) {
        for (DayRecord zeile : group(allDays(), Tab.YEARS)) {
            if (com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(zeile.start) == jahr) return zeile;
        }
        return null;
    }

    /**
     * Die Zeilen des gewaehlten Reiters.
     *
     * Jahre sind gerechnet: 124 Stunden, feste Grenzen. Amtszeiten sind es nicht - ein
     * Buergermeister regiert zwar genau ein Jahr, faengt damit aber irgendwo mitten im
     * Kalender an und hoert dort wieder auf. Nach dem Jahr zu gruppieren zerschnitte
     * deshalb jede Amtszeit in zwei Haelften.
     *
     * Also wird nicht gerechnet, sondern gelesen: Die Tage tragen den Namen, der an ihnen
     * galt, und ein Wechsel des Namens ist das Ende einer Amtszeit. Kommt derselbe
     * Buergermeister Jahre spaeter wieder, sind das zwei Amtszeiten und zwei Zeilen - so
     * wie es war.
     */
    private List<DayRecord> rows() {
        long now = System.currentTimeMillis();
        if (rowsMemo != null && rowsMemoTab == tab && rowsMemoVersion == ProfitLog.version()
                && now - rowsMemoAt < ROWS_MEMO_MILLIS) {
            return rowsMemo;
        }
        List<DayRecord> fresh = group(days(), tab);
        java.util.IdentityHashMap<DayRecord, Double> werte = new java.util.IdentityHashMap<>();
        for (DayRecord tag : fresh) werte.put(tag, ProfitTracker.worthOf(tag));
        rowsMemo = fresh;
        rowsWorth = werte;
        rowsMemoTab = tab;
        rowsMemoVersion = ProfitLog.version();
        rowsMemoAt = now;
        return fresh;
    }

    /**
     * Die Zeilen, kurz gemerkt.
     *
     * Das Bild fragt mehrmals je Bild danach, und jede Frage legte alle Tage mit allen Waren neu zusammen - bei dreissig
     * Tagen mit hunderten Waren mehrere Tausend Rechnungen je Bild. Eine Sekunde ist genug: Der laufende Tag aendert sich
     * langsam, und ein Reiterwechsel leert den Merker.
     */
    private List<DayRecord> rowsMemo = null;
    private java.util.IdentityHashMap<DayRecord, Double> rowsWorth = new java.util.IdentityHashMap<>();
    private Tab rowsMemoTab = null;
    private int rowsMemoVersion = -1;
    private long rowsMemoAt = 0L;
    private static final long ROWS_MEMO_MILLIS = 1_000L;

    /** Dieselbe Rechnung ohne Fenster - so laesst sie sich ohne laufendes Spiel pruefen */
    static List<DayRecord> group(List<DayRecord> tage, Tab welcher) {
        if (welcher == Tab.DAYS) return tage;
        if (welcher == Tab.MAYORS) return terms(tage);

        Map<Integer, DayRecord> jahre = new LinkedHashMap<>();
        for (DayRecord tag : tage) {
            int jahr = com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(tag.start);
            DayRecord ziel = jahre.computeIfAbsent(jahr, j -> new DayRecord(
                    com.shokiteufel.shokimod.util.SkyBlockYear.startOf(j), 0L, Map.of(), tag.mayor));
            dazu(ziel, tag);
        }
        return new ArrayList<>(jahre.values());
    }

    /**
     * Die Amtszeiten, neueste zuerst.
     *
     * Gegangen wird die Tagesliste von neu nach alt; solange der Name derselbe bleibt,
     * gehoeren die Tage zusammen. Der Anfang der Zeile ist der aelteste Tag darin, damit
     * die Beschriftung sagt, ab wann gezaehlt wurde.
     */
    static List<DayRecord> terms(List<DayRecord> tage) {
        List<DayRecord> out = new ArrayList<>();
        String laufend = null;
        DayRecord ziel = null;
        for (DayRecord tag : tage) {
            String name = tag.mayor == null || tag.mayor.isBlank() ? "?" : tag.mayor;
            if (ziel == null || !name.equals(laufend)) {
                ziel = new DayRecord(tag.start, 0L, Map.of(), tag.mayor);
                out.add(ziel);
                laufend = name;
            }
            dazu(ziel, tag);
            // Der aelteste Tag der Gruppe bestimmt, ab wann sie laeuft
            if (tag.start > 0 && tag.start < ziel.start) ziel.start = tag.start;
            ziel.ende = Math.max(ziel.ende, tag.start);
        }
        return out;
    }

    /** Einen Tag auf eine Zeile draufrechnen */
    private static void dazu(DayRecord ziel, DayRecord tag) {
        ziel.add(tag);
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
                rowsMemo = null;
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
                        "Day's own prices - each find counts 30 s of Active - click a row for its items")
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
            graphics.text(font, ItemValue.format(rowsWorth.getOrDefault(tag, 0.0)), left + 320, y, 0xFF55FF55, false);

            // Im Jahres-Reiter darunter die beguenstigten Farben. Sie stehen in keiner
            // Schnittstelle, nur in der Kiste von /dyes - wer sie einmal im Jahr
            // oeffnet, hat die Angabe fuer immer. Steht sie noch nicht da, sagt die
            // Zeile genau das, statt einfach leer zu bleiben
            if (tab == Tab.YEARS) {
                String farben = com.shokiteufel.shokimod.scanner.DyeRotation.of(
                        com.shokiteufel.shokimod.util.SkyBlockYear.yearOf(tag.start));
                graphics.text(font,
                        farben.isEmpty() ? "boosted dyes unknown - open /dyes during the year" : "boosted: " + farben,
                        left + 4, y + 10, farben.isEmpty() ? 0xFF666666 : 0xFF55FFFF, false);
            }
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
        if (tab == Tab.MAYORS) return tag.ende >= cfg.dayStartedAt;
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
            case DAYS -> {
                java.time.ZonedDateTime beginn =
                        Instant.ofEpochMilli(tag.start).atZone(ZoneId.systemDefault());
                String datum = TAG.format(beginn);
                // Faengt ein Satz nicht zur Reset-Stunde an, steht die Uhrzeit dabei.
                // Das passiert beim Jahreswechsel: Der bricht den Tag mitten durch, und
                // dann stuenden sonst zwei Zeilen mit demselben Datum untereinander
                yield beginn.getHour() == ProfitTracker.cfg().dayResetHour
                        ? datum
                        : datum + " " + UHR.format(beginn);
            }
            case YEARS -> "Year " + jahr;
            case MAYORS -> {
                String name = tag.mayor == null || tag.mayor.isBlank() ? "?" : tag.mayor;
                // Die Amtszeit, wie sie wirklich lag - Anfang und letzter gezaehlter Tag
                String von = KURZ.format(Instant.ofEpochMilli(tag.start).atZone(ZoneId.systemDefault()));
                String bis = tag.ende > tag.start
                        ? KURZ.format(Instant.ofEpochMilli(tag.ende).atZone(ZoneId.systemDefault()))
                        : von;
                yield name + "  (" + von + (von.equals(bis) ? "" : " - " + bis) + ")";
            }
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
