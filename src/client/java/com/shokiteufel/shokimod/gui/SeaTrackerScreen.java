package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.handler.SeaCreatureTracker;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Welche Meeresbewohner im Tracker eine eigene Zeile bekommen - einer je Zeile.
 *
 * Alle, die hier nicht angehakt sind, werden unter "Others" zusammengezaehlt. Aufbau
 * und Bedienung wie bei der Auswahl fuer den Alarm: Suchfeld, "All on" / "Off" und
 * Seiten, denn neunzig Bewohner passen nicht auf einen Bildschirm.
 */
public class SeaTrackerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 200;
    private static final int COUNT_WIDTH = 90;
    private static final int LIST_TOP = 74;

    private final Screen parent;
    private static String filter = "";
    private static boolean onlyPicked = false;
    private static int page = 0;

    public SeaTrackerScreen(Screen parent) {
        super(Component.literal("Sea creature tracker"));
        this.parent = parent;
    }

    private List<SeaCreatures.Creature> visible() {
        String suche = filter.trim().toLowerCase(Locale.ROOT);
        List<SeaCreatures.Creature> out = new ArrayList<>();
        for (SeaCreatures.Creature c : SeaCreatures.all()) {
            if (!suche.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(suche)) continue;
            if (onlyPicked && !SeaCreatureTracker.picked(c)) continue;
            out.add(c);
        }
        return out;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        page = Math.clamp(page, 0, pageCount() - 1);

        List<SeaCreatures.Creature> liste = visible();
        int gridWidth = NAME_WIDTH + 4 + COUNT_WIDTH;
        int left = width / 2 - gridWidth / 2;

        EditBox search = new EditBox(font, left, 34, NAME_WIDTH, 20, Component.literal("Search"));
        search.setValue(filter);
        search.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(value -> {
            filter = value;
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(
                Component.literal(onlyPicked ? "Picked" : "All").withStyle(ChatFormatting.AQUA),
                button -> {
                    onlyPicked = !onlyPicked;
                    page = 0;
                    rebuild();
                }).bounds(left + NAME_WIDTH + 4, 34, COUNT_WIDTH / 2 - 2, 20).build());

        // Alles an oder alles aus: bei neunzig Zeilen spart das viel Klicken
        addRenderableWidget(Button.builder(Component.literal("All on"), button -> {
            for (SeaCreatures.Creature c : visible()) {
                if (!SeaCreatureTracker.picked(c)) SeaCreatureTracker.togglePicked(c);
            }
            rebuild();
        }).bounds(left, 56, NAME_WIDTH / 2 - 2, 14).build());

        addRenderableWidget(Button.builder(Component.literal("Off"), button -> {
            for (SeaCreatures.Creature c : visible()) {
                if (SeaCreatureTracker.picked(c)) SeaCreatureTracker.togglePicked(c);
            }
            rebuild();
        }).bounds(left + NAME_WIDTH / 2 + 2, 56, NAME_WIDTH / 2 - 2, 14).build());

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            SeaCreatures.Creature c = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            boolean an = SeaCreatureTracker.picked(c);

            addRenderableWidget(Button.builder(
                    Component.literal((an ? "☑ " : "☐ ") + c.name())
                            .withStyle(style -> style.withColor(an ? c.colour() : 0x777777)),
                    button -> {
                        SeaCreatureTracker.togglePicked(c);
                        rebuild();
                    }).bounds(left, y, NAME_WIDTH, 20).build());

            addRenderableWidget(Button.builder(
                    Component.literal(an ? "own line" : "Others")
                            .withStyle(an ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                    button -> {
                        SeaCreatureTracker.togglePicked(c);
                        rebuild();
                    }).bounds(left + NAME_WIDTH + 4, y, COUNT_WIDTH, 20).build());
        }

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

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 80, height - 28, 80, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;

        graphics.centeredText(font, Component.literal("Sea creature tracker").withStyle(ChatFormatting.GOLD),
                centerX, 14, 0xFFFFAA00);

        int gewaehlt = 0;
        for (SeaCreatures.Creature c : SeaCreatures.all()) {
            if (SeaCreatureTracker.picked(c)) gewaehlt++;
        }
        graphics.centeredText(font, Component.literal(
                        gewaehlt + " of " + SeaCreatures.all().size()
                                + " get their own line  ·  the rest is counted under Others")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 14 + 12, 0xFF888888);

        if (visible().isEmpty()) {
            graphics.centeredText(font, Component.literal("Nothing found for \"" + filter + "\"")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }
}
