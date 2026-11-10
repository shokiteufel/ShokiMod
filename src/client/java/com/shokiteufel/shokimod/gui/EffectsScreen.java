package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.EffectEntry;
import com.shokiteufel.shokimod.handler.EffectsTracker;
import com.shokiteufel.shokimod.render.hud.EffectsHud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Die gemerkten Effekte, einer je Zeile: Name, Restzeit, und je Effekt ob er im Kasten steht
 * und ob er Alarm gibt. Gemerkt wird, sobald man /effects oeffnet.
 *
 * Rechts neben Hide steht "Map": auf welchen Maps der Effekt im Kasten erscheint (derselbe Waehler wie
 * "Areas" im HUD-Editor; ohne Auswahl auf jeder). Das betrifft nur den Kasten, nicht den Alarm.
 *
 * Was nicht gebraucht wird, kann versteckt werden: Ein versteckter Effekt steht nicht mehr in der
 * Liste, im Kasten und gibt keinen Alarm - und er kommt nicht zurueck, wenn /effects ihn das
 * naechste Mal zeigt. Unter "Hidden" lassen sich alle wieder holen oder ganz vergessen.
 */
public class EffectsScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 156;
    private static final int TIME_WIDTH = 74;
    private static final int TOGGLE_WIDTH = 62;
    private static final int HIDE_WIDTH = 36;
    /** Der Map-Knopf rechts neben Hide: die Gebiete, in denen der Effekt im Kasten stehen darf */
    private static final int MAP_WIDTH = 46;
    /**
     * Zeit, Haken und Map-Knopf, wenn der Platz nicht fuer die breite Fassung reicht. Zusammen mit Hide und den Luecken sind das
     * 250 Pixel - genau so viel wie die Zeilen vor dem Map-Knopf brauchten: Wo die Liste vorher passte, passt sie weiter.
     */
    private static final int TIME_WIDTH_TIGHT = 50;
    private static final int TOGGLE_WIDTH_TIGHT = 54;
    private static final int MAP_WIDTH_TIGHT = 36;
    /** Was rechts vom Namen steht, in der breiten Fassung: Zeit, zwei Haken, Hide, Map und die Luecken dazwischen (8 + 8 + 4) */
    private static final int REST_WIDE = TIME_WIDTH + 2 * TOGGLE_WIDTH + HIDE_WIDTH + MAP_WIDTH + 20;
    /** So schmal darf die Namensspalte in der breiten Fassung werden; reicht der Platz dafuer nicht, rueckt der Rest enger zusammen */
    private static final int NAME_MIN = 96;
    /** Die Reiter sitzen unter der Ueberschrift, die Liste darunter */
    private static final int TAB_Y = 46;
    private static final int TAB_WIDTH = 84;
    private static final int LIST_TOP = 70;
    /** Unten bleiben zwei Knopfleisten frei: Auswahl und Seiten, darunter die Schalter */
    private static final int BOTTOM_SPACE = 84;
    private static final int GRID_WIDTH = NAME_WIDTH + REST_WIDE;
    /** Links neben der Liste: die Voreinstellungen, ein Knopf je Platz */
    private static final int PRESET_X = 6;
    private static final int PRESET_WIDTH = 80;
    private static final int PRESET_WIDTH_NARROW = 62;
    private static final int PRESET_GAP = 12;
    /** Schmaler als das (in GUI-Pixeln) rueckt alles enger zusammen */
    private static final int NARROW_BELOW = 480;
    private static final int PRESET_TOP = 84;
    private static final int PRESET_STEP = 20;

    private final Screen parent;
    private static int page = 0;
    /** Zeigt die Liste die versteckten Effekte statt der gemerkten? */
    private static boolean showHidden = false;
    /** Welcher Reiter gilt - ueberdauert das Schliessen, wer bei den Mixins war, will dort weitermachen */
    private static EffectsTracker.Tab tab = EffectsTracker.Tab.ALL;

    public EffectsScreen(Screen parent) {
        super(Component.literal("Effects"));
        this.parent = parent;
        showHidden = false;
        // Die bekannten Mixins stehen im Reiter, bevor man sie getrunken hat
        EffectsTracker.seedCatalogue();
    }

    private boolean narrow() {
        return width < NARROW_BELOW;
    }

    private int presetWidth() {
        return narrow() ? PRESET_WIDTH_NARROW : PRESET_WIDTH;
    }

    /** Wo die Liste beginnt: in der Mitte, aber nie unter der Spalte der Voreinstellungen */
    private int left() {
        return Math.max(width / 2 - GRID_WIDTH / 2, PRESET_X + presetWidth() + (narrow() ? 8 : PRESET_GAP));
    }

    /**
     * Reicht der Platz nicht fuer die breite Fassung mit dem Namen in voller Breite? Dann rueckt der Rest enger zusammen:
     * Zeit, die beiden Haken und der Map-Knopf werden schmaler, Hide bleibt.
     */
    private boolean tight() {
        return narrow() || width - left() - 6 - REST_WIDE < NAME_MIN;
    }

    private int timeWidth() {
        return tight() ? TIME_WIDTH_TIGHT : TIME_WIDTH;
    }

    private int toggleWidth() {
        return tight() ? TOGGLE_WIDTH_TIGHT : TOGGLE_WIDTH;
    }

    private int mapWidth() {
        return tight() ? MAP_WIDTH_TIGHT : MAP_WIDTH;
    }

    /** Was rechts vom Namen steht, samt den Luecken: Zeit, zwei Haken, Hide, Map */
    private int restWidth() {
        return timeWidth() + 2 * toggleWidth() + HIDE_WIDTH + mapWidth() + 20;
    }

    /** Die Spalte mit den Namen gibt nach, wenn das Fenster zu schmal fuer die ganze Liste ist - der Rest bleibt, wie er ist */
    private int nameWidth() {
        return Math.clamp(width - left() - (narrow() ? 4 : 6) - restWidth(), narrow() ? 70 : NAME_MIN, NAME_WIDTH);
    }

    private int tabWidth() {
        return narrow() ? 70 : TAB_WIDTH;
    }

    private int gridWidth() {
        return nameWidth() + restWidth();
    }

    private int rowsPerPage() {
        return Math.max(1, (height - BOTTOM_SPACE - LIST_TOP) / ROW_HEIGHT);
    }

    /**
     * Eine Zeile der Liste: entweder die Ueberschrift einer Gruppe oder ein Effekt - samt den Texten, die das Bild zeichnet (gekuerzter Name,
     * Restzeit mit Farbe). Die Texte werden mit der Zeile gebaut, nicht in jedem Bild.
     */
    private static final class Row {
        private final String header;
        private final EffectEntry entry;
        /** Der Name, auf die Spaltenbreite gekuerzt (nur bei Effekten) */
        String name = "";
        /** Die Restzeit als Text und ihre Farbe */
        String time = "";
        int timeColour = 0xFFAAAAAA;
        /** Zu welcher Sekunde der Restzeit {@link #time} gehoert; Long.MIN_VALUE: noch nicht gebaut */
        private long timeKey = Long.MIN_VALUE;

        Row(String header, EffectEntry entry) {
            this.header = header;
            this.entry = entry;
        }

        String header() {
            return header;
        }

        EffectEntry entry() {
            return entry;
        }

        /**
         * Die Restzeit als Text, neu gebaut nur, wenn sich ihre Sekunde aendert (oder der Effekt ausgeht) - nicht in jedem Bild. Dieselbe
         * Rechnung wie {@code EffectsTracker.remaining}: ohne Ende "?", ab 0 "off", sonst die Restzeit; ohne den Long, den remaining
         * je Bild neu verpackte.
         */
        void timeUpdate(long now) {
            long rest = entry.expiry - now;
            long key = entry.expiry <= 0L ? -2L : rest <= 0L ? -1L : rest / 1000L;
            if (key == timeKey) return;
            timeKey = key;
            if (key == -2L) {
                time = "?";
                timeColour = 0xFFAAAAAA;
            } else if (key == -1L) {
                time = "off";
                timeColour = 0xFFFF5555;
            } else {
                time = EffectsHud.format(rest);
                timeColour = 0xFF55FF55;
            }
        }
    }

    private List<EffectEntry> shown() {
        return showHidden ? EffectsTracker.hiddenEntries() : EffectsTracker.entries();
    }

    // Die Seiten der Liste, einmal gebaut und gehalten. Neu gebaut bei jedem init() (Knopf, Reiter, Groessenaenderung - jede Eingabe der Liste
    // laeuft ueber rebuild()), bei geaenderter Zahl der gemerkten Effekte oder Zeilen je Seite und sonst spaetestens nach einer Sekunde: Der
    // Tracker liest im Hintergrund /effects und die Tab-Liste und kann einem Mixin im Lauf den echten Namen geben (Sortierung, Name).
    private List<List<Row>> pagesCache;
    private int pagesListSize = -1;
    private int pagesPer = -1;
    private long pagesAt;
    private static final long PAGES_MILLIS = 1_000L;
    /** Wie viele Effekte der gewaehlte Reiter zeigt (statt EffectsTracker.count in jedem Bild) - gezaehlt zusammen mit den Seiten */
    private int pagesEntries;
    /** Was bei leerer Liste steht - gewaehlt zusammen mit den Seiten */
    private String emptyText = "";

    /**
     * Die Seiten der Liste. "All" zeigt zwei Gruppen (ohne God Potion, God Potion), jede mit ihrer Ueberschrift; die anderen Reiter
     * zeigen eine Art (Mixins, der Rest, God Potion). Eine Ueberschrift steht nie als letzte Zeile einer Seite, und eine Gruppe, die auf der
     * naechsten Seite weitergeht, bekommt dort ihre Ueberschrift noch einmal.
     */
    private List<List<Row>> pages() {
        long now = System.currentTimeMillis();
        if (pagesCache == null || pagesPer != rowsPerPage() || pagesListSize != ModConfig.INSTANCE.effects.list.size()
                || now - pagesAt >= PAGES_MILLIS) {
            pagesBauen(now);
        }
        return pagesCache;
    }

    private void pagesBauen(long now) {
        List<EffectsTracker.Group> groups = EffectsTracker.groups(showHidden, tab);
        int per = rowsPerPage();
        int nameMax = nameWidth() - 8;

        List<List<Row>> pages = new ArrayList<>();
        List<Row> page = new ArrayList<>();
        int entries = 0;
        for (EffectsTracker.Group group : groups) {
            if (page.size() >= per - 1 && per > 1) {
                pages.add(page);
                page = new ArrayList<>();
            }
            page.add(new Row(group.name(), null));
            for (EffectEntry e : group.entries()) {
                if (page.size() >= per) {
                    pages.add(page);
                    page = new ArrayList<>();
                    if (per > 1) page.add(new Row(group.name() + " (cont.)", null));
                }
                Row row = new Row(null, e);
                row.name = font.plainSubstrByWidth(e.name, nameMax);
                page.add(row);
                entries++;
            }
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(page);

        // Der Satz bei leerer Liste: nur dann gebraucht, und nur hier gewaehlt statt in jedem Bild
        String empty = "";
        if (entries == 0) {
            empty = showHidden ? "Nothing hidden"
                    : tab != EffectsTracker.Tab.ALL && EffectsTracker.count(false, EffectsTracker.Tab.ALL) > 0 ? "Nothing in this tab"
                    : EffectsTracker.hiddenEntries().isEmpty() ? "Nothing remembered yet"
                    : "Everything is hidden - see Hidden below";
        }

        pagesCache = pages;
        pagesEntries = entries;
        emptyText = empty;
        pagesListSize = ModConfig.INSTANCE.effects.list.size();
        pagesPer = per;
        pagesAt = now;
    }

    private int pageCount() {
        return Math.max(1, pages().size());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static Button withTip(Button button, String tip) {
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        return button;
    }

    /** Die Auswahl der Maps eines Effekts: derselbe Waehler wie "Areas" im HUD-Editor, gespeichert wird bei jedem Klick */
    private void openMaps(EffectEntry e) {
        if (minecraft == null) return;
        minecraft.setScreenAndShow(new AreaPickerScreen(this, "Map: " + e.name, EffectsTracker.areasOf(e),
                "Nothing selected - the effect shows on every map", ModConfig.INSTANCE::saveNow));
    }

    /** Was der Map-Knopf sagt: wo der Effekt steht, und dass nur der Kasten gemeint ist, nicht der Alarm */
    private static String mapTip(List<String> maps) {
        String where;
        if (maps.isEmpty()) {
            where = "Nothing selected: it shows on every map.";
        } else {
            int shown = Math.min(maps.size(), 6);
            where = "Shows only on: " + String.join(", ", maps.subList(0, shown))
                    + (maps.size() > shown ? " (+" + (maps.size() - shown) + " more)" : "") + ".";
        }
        return "The maps where this effect may appear in the panel. " + where + " The alert is not affected.";
    }

    @Override
    protected void init() {
        // Jeder Neuaufbau heisst: Knopf, Reiter oder Groesse haben sich geaendert - die Seiten neu bauen lassen
        pagesCache = null;
        List<List<Row>> pages = pages();
        page = Math.clamp(page, 0, pages.size() - 1);
        List<Row> rows = pages.get(page);
        int left = left();

        // Die Reiter: derselbe Knopf, der gerade gilt, ist abgeschaltet; die Zahl sagt, wie viele Effekte darin stehen
        int tabX = left;
        for (EffectsTracker.Tab eintrag : EffectsTracker.Tab.values()) {
            Button knopf = Button.builder(
                    Component.literal(eintrag.label + " (" + EffectsTracker.count(showHidden, eintrag) + ")"),
                    button -> {
                        tab = eintrag;
                        page = 0;
                        rebuild();
                    }).bounds(tabX, TAB_Y, tabWidth(), 18).build();
            knopf.active = tab != eintrag;
            addRenderableWidget(knopf);
            tabX += tabWidth() + 4;
        }

        for (int i = 0; i < rows.size(); i++) {
            EffectEntry e = rows.get(i).entry();
            // Eine Ueberschrift hat keine Knoepfe
            if (e == null) continue;
            int y = LIST_TOP + i * ROW_HEIGHT;
            int x = left + nameWidth() + timeWidth() + 8;
            int toggle = toggleWidth();

            if (showHidden) {
                addRenderableWidget(withTip(Button.builder(Component.literal("Show").withStyle(ChatFormatting.GREEN),
                        button -> {
                            EffectsTracker.unhide(e);
                            rebuild();
                        }).bounds(x, y, toggle, 20).build(),
                        "Brings the effect back into the list. HUD and Alert stay off until you tick them."));
                addRenderableWidget(withTip(Button.builder(Component.literal("Forget").withStyle(ChatFormatting.RED),
                        button -> {
                            EffectsTracker.remove(e);
                            rebuild();
                        }).bounds(x + toggle + 4, y, toggle, 20).build(),
                        "Deletes the entry. It is remembered again the next time /effects lists it."));
                continue;
            }

            addRenderableWidget(Button.builder(
                    Component.literal(e.hud ? "HUD ☑" : "HUD ☐")
                            .withStyle(e.hud ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                    button -> {
                        EffectsTracker.toggleHud(e);
                        rebuild();
                    }).bounds(x, y, toggle, 20).build());
            addRenderableWidget(Button.builder(
                    Component.literal(e.alert ? "Alert ☑" : "Alert ☐")
                            .withStyle(e.alert ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY),
                    button -> {
                        EffectsTracker.toggleAlert(e);
                        rebuild();
                    }).bounds(x + toggle + 4, y, toggle, 20).build());
            int hideX = x + 2 * toggle + 8;
            addRenderableWidget(withTip(Button.builder(Component.literal("Hide").withStyle(ChatFormatting.RED),
                    button -> {
                        EffectsTracker.hide(e);
                        rebuild();
                    }).bounds(hideX, y, HIDE_WIDTH, 20).build(),
                    "Hides the effect: no list entry, no panel row, no alert - and it stays hidden when /effects lists it again."));

            // Auf welchen Maps der Effekt im Kasten steht - derselbe Waehler wie "Areas" im HUD-Editor
            List<String> maps = EffectsTracker.areasOf(e);
            addRenderableWidget(withTip(Button.builder(
                    Component.literal(maps.isEmpty() ? "Map" : "Map " + maps.size())
                            .withStyle(maps.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN),
                    button -> openMaps(e)).bounds(hideX + HIDE_WIDTH + 4, y, mapWidth(), 20).build(),
                    mapTip(maps)));
        }

        // Auswahl: wegraeumen, was nicht gebraucht wird, und die versteckten ansehen
        int barY = height - 52;
        if (showHidden) {
            addRenderableWidget(Button.builder(Component.literal("◀ Back to the list"), button -> {
                showHidden = false;
                page = 0;
                rebuild();
            }).bounds(left, barY, 110, 20).build());
        } else {
            addRenderableWidget(withTip(Button.builder(Component.literal("Hide unused"), button -> {
                EffectsTracker.hideUnused();
                page = 0;
                rebuild();
            }).bounds(left, barY, 90, 20).build(),
                    "Hides every effect that has neither HUD nor Alert ticked. You can bring them back under Hidden."));

            int hidden = EffectsTracker.hiddenEntries().size();
            Button hiddenButton = Button.builder(Component.literal("Hidden (" + hidden + ")"), button -> {
                showHidden = true;
                page = 0;
                rebuild();
            }).bounds(left + 96, barY, 90, 20).build();
            hiddenButton.active = hidden > 0;
            addRenderableWidget(hiddenButton);
        }

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + gridWidth() - 96, barY, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + gridWidth() - 20, barY, 20, 20).build());
        }

        // Oeffnet /effects, damit die Mod es liest - ohne den Befehl selbst zu tippen
        int gap = narrow() ? 4 : 6;
        int openW = narrow() ? 92 : 110;
        int enabledW = narrow() ? 76 : 90;
        int soundW = narrow() ? 52 : 70;
        int doneW = narrow() ? 56 : 80;
        addRenderableWidget(Button.builder(Component.literal("Open /effects"), button -> {
            if (minecraft != null && minecraft.getConnection() != null) {
                minecraft.setScreenAndShow(null);
                minecraft.getConnection().sendCommand("effects");
            }
        }).bounds(left, height - 28, openW, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(ModConfig.INSTANCE.effects.enabled ? "Enabled ☑" : "Enabled ☐")
                        .withStyle(ModConfig.INSTANCE.effects.enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
                button -> {
                    ModConfig.INSTANCE.effects.enabled = !ModConfig.INSTANCE.effects.enabled;
                    ModConfig.INSTANCE.saveNow();
                    rebuild();
                }).bounds(left + openW + gap, height - 28, enabledW, 20).build());

        // Der Ton des Alarms: eine eigene Datei aus config/shokimod/sounds, leer heisst stumm
        boolean hasSound = ModConfig.INSTANCE.effects.sound != null && !ModConfig.INSTANCE.effects.sound.isBlank();
        addRenderableWidget(Button.builder(
                Component.literal(hasSound ? "Sound ♪" : "Sound").withStyle(
                        hasSound ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                button -> {
                    if (minecraft != null) {
                        minecraft.setScreenAndShow(new SoundPickerScreen(this,
                                () -> ModConfig.INSTANCE.effects.sound,
                                picked -> ModConfig.INSTANCE.effects.sound = picked,
                                1.0f));
                    }
                }).bounds(left + openW + gap + enabledW + gap, height - 28, soundW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth() - doneW, height - 28, doneW, 20).build());

        addPresetColumn();
    }

    /**
     * Die Voreinstellungen: ein Knopf je Platz (fuenf von Anfang an, mit "+ New" bis acht), darueber der Name des gewaehlten. Wer einen anderen
     * Platz waehlt, sichert den bisherigen und bekommt dessen Haken - so stehen fuer Farming andere Effekte im Kasten als fuer Mining.
     */
    private void addPresetColumn() {
        java.util.List<ModConfig.EffectPreset> slots = EffectsTracker.presets();
        int active = EffectsTracker.activePreset();

        // Der Name des gewaehlten Platzes: tippen aendert ihn, gespeichert wird beim Schliessen
        EditBox nameBox = new EditBox(font, PRESET_X, PRESET_TOP - 22, presetWidth(), 16, Component.literal("Preset name"));
        nameBox.setMaxLength(EffectsTracker.PRESET_NAME_MAX);
        nameBox.setValue(slots.get(active).name);
        nameBox.setTooltip(Tooltip.create(Component.literal("The name of the selected preset - type to change it")));
        nameBox.setResponder(text -> EffectsTracker.renamePreset(active, text));
        addRenderableWidget(nameBox);

        for (int i = 0; i < slots.size(); i++) {
            final int slot = i;
            ModConfig.EffectPreset p = slots.get(i);
            String label = font.plainSubstrByWidth((i + 1) + " " + p.name, presetWidth() - 8);
            Button button = Button.builder(Component.literal(label)
                            .withStyle(i == active ? ChatFormatting.GOLD : p.used ? ChatFormatting.WHITE : ChatFormatting.GRAY),
                    b -> {
                        EffectsTracker.selectPreset(slot);
                        page = 0;
                        rebuild();
                    }).bounds(PRESET_X, PRESET_TOP + i * PRESET_STEP, presetWidth(), 18).build();
            button.active = i != active;
            button.setTooltip(Tooltip.create(Component.literal(p.name + (p.used
                    ? ": " + p.hud.size() + " in the panel, " + p.alert.size() + " with alert"
                    : ": empty - choosing it switches everything off, so you can pick what this one should show"))));
            addRenderableWidget(button);
        }
        if (slots.size() < EffectsTracker.PRESET_MAX) {
            addRenderableWidget(withTip(Button.builder(Component.literal("+ New"), b -> {
                EffectsTracker.addPreset();
                page = 0;
                rebuild();
            }).bounds(PRESET_X, PRESET_TOP + slots.size() * PRESET_STEP, presetWidth(), 18).build(),
                    "Adds a preset (up to " + EffectsTracker.PRESET_MAX + ") and switches to it - empty at first"));
        }
    }

    @Override
    public void onClose() {
        // Name und Haken des gewaehlten Platzes bleiben stehen
        EffectsTracker.captureActive();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.text(font, "Presets", PRESET_X + 2, PRESET_TOP - 36, 0xFFFFAA00, false);
        // Feste Texte: je Zeichenkette einmal gebaut und geteilt, nicht in jedem Bild
        graphics.centeredText(font, FensterText.gold(showHidden ? "Effects - hidden" : "Effects"), centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, FensterText.dunkelgrau(showHidden
                        ? "Hidden effects stay out of the list, the panel and the alerts"
                        : "Tabs sort the list - the known Mixins are listed before you have used them"),
                centerX, 26, 0xFF888888);
        if (!ModConfig.INSTANCE.effects.enabled) {
            graphics.centeredText(font, FensterText.rot("Panel and alerts are off - switch Enabled on below"),
                    centerX, 38, 0xFFFF5555);
        }

        // Die gehaltenen Seiten (neu gebaut nur bei Aenderung, siehe pages()); die Zahl der Effekte und der Satz bei leerer Liste kommen mit
        List<List<Row>> pages = pages();
        if (pagesEntries == 0) {
            graphics.centeredText(font, FensterText.grau(emptyText), centerX, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }
        int left = left();
        int nameW = nameWidth();
        int gridW = gridWidth();
        long now = System.currentTimeMillis();
        List<Row> rows = pages.get(Math.clamp(page, 0, pages.size() - 1));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT + 6;
            if (row.entry() == null) {
                // Die Ueberschrift einer Gruppe, mit einem Strich darunter
                graphics.text(font, row.header(), left, y, 0xFFFFAA00, false);
                graphics.fill(left, y + 10, left + gridW, y + 11, 0x66FFAA00);
                continue;
            }
            // Name und Restzeit liegen in der Zeile; die Restzeit wird nur neu gebaut, wenn eine Sekunde vergangen ist
            row.timeUpdate(now);
            graphics.text(font, row.name, left + 6, y, 0xFFFFFFFF, false);
            graphics.text(font, row.time, left + nameW + 4, y, row.timeColour, false);
        }
        int pageTotal = Math.max(1, pages.size());
        if (pageTotal > 1) {
            graphics.centeredText(font, FensterText.seite(page, pageTotal), left + gridW - 48, height - 46, 0xFFAAAAAA);
        }
    }
}
