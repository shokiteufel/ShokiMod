package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.CollectionData;
import com.shokiteufel.shokimod.util.CollectionData.Collection;

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
 * Welche Collections der Tracker fuehrt: Mehrfachauswahl zum Anhaken.
 *
 * Die Liste kommt aus Hypixels eigener Aufstellung, geordnet nach Kategorie. Ein
 * Suchfeld daneben, weil neunzig Eintraege auf keine Seite passen.
 */
public class CollectionPickerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int COLUMNS = 3;
    private static final int COLUMN_WIDTH = 160;
    private static final int LIST_TOP = 62;

    private final Screen parent;
    private String filter = "";
    private int page;

    public CollectionPickerScreen(Screen parent) {
        super(Component.literal("Collections"));
        this.parent = parent;
    }

    private static List<String> selected() {
        List<String> list = ModConfig.INSTANCE.collections.tracker.selected;
        if (list == null) ModConfig.INSTANCE.collections.tracker.selected = list = new ArrayList<>();
        return list;
    }

    private List<Collection> visible() {
        List<Collection> out = new ArrayList<>();
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        for (Collection collection : CollectionData.all()) {
            if (needle.isEmpty() || collection.name().toLowerCase(Locale.ROOT).contains(needle)
                    || collection.category().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(collection);
            }
        }
        return out;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int perPage() {
        return rowsPerPage() * COLUMNS;
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        CollectionData.prefetch();
        page = Math.min(page, pageCount() - 1);

        List<Collection> items = visible();
        int gridWidth = COLUMNS * (COLUMN_WIDTH + 4) - 4;
        int left = width / 2 - gridWidth / 2;

        EditBox search = new EditBox(font, left, 34, 200, 20, Component.literal("Search"));
        search.setValue(filter);
        search.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(value -> {
            filter = value;
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < items.size(); i++) {
            Collection collection = items.get(start + i);
            int x = left + (i % COLUMNS) * (COLUMN_WIDTH + 4);
            int y = LIST_TOP + (i / COLUMNS) * ROW_HEIGHT;
            Button row = Button.builder(label(collection), button -> {
                toggle(collection.id());
                button.setMessage(label(collection));
            }).bounds(x, y, COLUMN_WIDTH, 20).build();
            addRenderableWidget(row);
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Clear all"), button -> {
            selected().clear();
            rebuild();
        }).bounds(left, y, 90, 20).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + 98, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 122, y, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 90, y, 90, 20).build());
    }

    private static Component label(Collection collection) {
        boolean on = selected().contains(collection.id());
        return Component.literal((on ? "☑ " : "☐ ") + collection.name())
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private void toggle(String id) {
        if (!selected().remove(id)) selected().add(id);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        graphics.centeredText(font, Component.literal("Collections in the tracker"), centerX, 14, 0xFFFFFFFF);

        String note = CollectionData.ready()
                ? (selected().isEmpty() ? "Nothing selected - everything that comes in is counted"
                                        : selected().size() + " selected")
                : "Loading the list from Hypixel...";
        graphics.centeredText(font, Component.literal(note).withStyle(
                selected().isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN), centerX, height - 46, 0xFFAAAAAA);

        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 58, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
