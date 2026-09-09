package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ChatRule;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.KnownAreas;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Mehrfachauswahl der Gebiete, in denen eine Chatregel greifen soll.
 *
 * Ist nichts angehakt, gilt die Regel überall - genauso hält es Skyblocker.
 */
public class AreaPickerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int COLUMNS = 3;
    private static final int COLUMN_WIDTH = 150;
    private static final int LIST_TOP = 56;

    private final Screen parent;
    /** Die Liste, die bearbeitet wird - bei Chatregeln die der Regel, beim HUD die des Kastens */
    private final List<String> selection;
    private final String emptyHint;
    private final Runnable onChange;
    private int page;

    public AreaPickerScreen(Screen parent, ChatRule rule) {
        this(parent, "Areas", rule.areas, "Nothing selected - the rule works everywhere", null);
    }

    /**
     * Derselbe Waehler fuer jede Gebietsliste.
     *
     * @param onChange wird nach jeder Aenderung gerufen - dort gehoert das Speichern hin
     */
    public AreaPickerScreen(Screen parent, String title, List<String> selection,
                            String emptyHint, Runnable onChange) {
        super(Component.literal(title));
        this.parent = parent;
        this.selection = selection;
        this.emptyHint = emptyHint;
        this.onChange = onChange;
    }

    private void changed() {
        if (onChange != null) onChange.run();
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int perPage() {
        return rowsPerPage() * COLUMNS;
    }

    private int pageCount() {
        return Math.max(1, (KnownAreas.all().size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);

        List<String> areas = KnownAreas.all();
        int gridWidth = COLUMNS * (COLUMN_WIDTH + 4) - 4;
        int left = width / 2 - gridWidth / 2;
        int start = page * perPage();

        for (int i = 0; i < perPage() && start + i < areas.size(); i++) {
            String area = areas.get(start + i);
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            int x = left + column * (COLUMN_WIDTH + 4);
            int y = LIST_TOP + row * ROW_HEIGHT;

            addRenderableWidget(Button.builder(areaLabel(area), button -> {
                toggle(area);
                button.setMessage(areaLabel(area));
            }).bounds(x, y, COLUMN_WIDTH, 20).build());
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Clear all"), button -> {
            selection.clear();
            changed();
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

    private void toggle(String area) {
        if (!selection.removeIf(entry -> entry.equalsIgnoreCase(area))) selection.add(area);
        changed();
    }

    private Component areaLabel(String area) {
        boolean selected = selection.stream().anyMatch(entry -> entry.equalsIgnoreCase(area));
        boolean here = area.equals(GameState.Server.map);

        Component name = Component.literal((here ? "▸ " : "") + area)
                .withStyle(selected ? ChatFormatting.GREEN
                        : here ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        return selected ? Component.literal("✔ ").withStyle(ChatFormatting.GREEN).append(name) : name;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        graphics.centeredText(font, this.title, centerX, 14, 0xFFFFFFFF);

        String hint = selection.isEmpty() ? emptyHint : selection.size() + " area(s) selected";
        graphics.centeredText(font, Component.literal(hint).withStyle(
                        selection.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN),
                centerX, 28, 0xFFAAAAAA);

        graphics.centeredText(font, Component.literal(
                        "▸ marks where you are now  ·  " + KnownAreas.discoveredCount()
                                + " area(s) added by visiting them")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 40, 0xFF808080);

        if (pageCount() > 1) {
            graphics.centeredText(font,
                    Component.literal((page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY),
                    centerX, height - 46, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
