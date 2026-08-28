package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ChatRule;
import com.shokiteufel.shokimod.data.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Liste der Chatregeln. Bearbeitet wird eine Regel im ChatRuleEditScreen. */
public class ChatRuleScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 392;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LIST_TOP = 48;

    private final Screen parent;
    private int page;

    public ChatRuleScreen(Screen parent) {
        super(Component.literal("Chat Rules"));
        this.parent = parent;
    }

    private List<ChatRule> rules() {
        return ModConfig.INSTANCE.chat.chatRules;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 60 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (rules().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);

        int left = width / 2 - ROW_WIDTH / 2;
        int rows = rowsPerPage();
        int start = page * rows;
        List<ChatRule> list = rules();

        for (int i = 0; i < rows && start + i < list.size(); i++) {
            ChatRule rule = list.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            addRenderableWidget(Button.builder(onOff(rule.enabled), button -> {
                rule.enabled = !rule.enabled;
                button.setMessage(onOff(rule.enabled));
            }).bounds(left, y, 40, WIDGET_HEIGHT).build());

            boolean needsFilter = rule.filter == null || rule.filter.isBlank();
            Button edit = Button.builder(Component.literal(
                    (needsFilter ? "§c⚠ §r" : "") + rule.label() + "  §8" + summary(rule)),
                    button -> minecraft.setScreen(new ChatRuleEditScreen(this, rule)))
                    .bounds(left + 44, y, 252, WIDGET_HEIGHT).build();
            edit.setTooltip(Tooltip.create(needsFilter
                    ? Component.literal("No filter yet - the rule never triggers")
                            .withStyle(ChatFormatting.RED)
                    : Component.literal("Filter: " + rule.filter)));
            addRenderableWidget(edit);

            // Die Reihenfolge entscheidet, welche Regel zuerst zuschlaegt - und damit,
            // welche eine andere aussperren kann
            int index = start + i;
            Button up = Button.builder(Component.literal("▲"), button -> {
                swap(index, index - 1);
                rebuild();
            }).bounds(left + 300, y, 20, WIDGET_HEIGHT).build();
            up.active = index > 0;
            addRenderableWidget(up);

            Button down = Button.builder(Component.literal("▼"), button -> {
                swap(index, index + 1);
                rebuild();
            }).bounds(left + 324, y, 20, WIDGET_HEIGHT).build();
            down.active = index < list.size() - 1;
            addRenderableWidget(down);

            addRenderableWidget(Button.builder(
                    Component.literal("✕").withStyle(ChatFormatting.RED), button -> {
                        rules().remove(rule);
                        rebuild();
                    }).bounds(left + 352, y, 20, WIDGET_HEIGHT).build());
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("New rule"), button -> {
            ChatRule fresh = new ChatRule();
            rules().add(fresh);
            minecraft.setScreen(new ChatRuleEditScreen(this, fresh));
        }).bounds(left, y, 100, WIDGET_HEIGHT).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + 108, y, 20, WIDGET_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 132, y, 20, WIDGET_HEIGHT).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + ROW_WIDTH - 90, y, 90, WIDGET_HEIGHT).build());
    }

    /** Zwei Regeln tauschen. Ausserhalb der Liste passiert nichts */
    private void swap(int from, int to) {
        List<ChatRule> list = rules();
        if (from < 0 || to < 0 || from >= list.size() || to >= list.size()) return;
        list.add(to, list.remove(from));
    }

    /** Kurzzeichen, welche Reaktionen die Regel auslöst */
    private static String summary(ChatRule rule) {
        StringBuilder out = new StringBuilder();
        if (rule.hideMessage) out.append("hide ");
        if (!rule.replacement.isBlank()) out.append("replace ");
        if (!rule.actionBar.isBlank()) out.append("bar ");
        if (!rule.announcement.isBlank()) out.append("banner ");
        if (!rule.toast.isBlank()) out.append("toast ");
        if (!rule.soundId.isBlank()) out.append("sound ");
        if (!rule.soundFile.isBlank()) out.append("file ");
        return out.isEmpty() ? "no output" : out.toString().trim();
    }

    private static Component onOff(boolean on) {
        return on ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.DARK_GRAY);
    }

    void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.centeredText(font, this.title, centerX, 16, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(
                        "React to words in chat  ·  the order decides who goes first")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 30, 0xFF808080);

        if (rules().isEmpty()) {
            graphics.centeredText(font, Component.literal("No rules yet - add one below.")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 12, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font,
                    Component.literal((page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY),
                    centerX, height - 46, 0xFFAAAAAA);
        }
    }

    /**
     * Hier wird gespeichert, nicht in removed(): removed() laeuft bei jedem Bildschirmwechsel,
     * also auch beim Sprung in den Editor - eine gerade angelegte Regel waere damit weg, bevor
     * man das erste Zeichen tippen kann.
     */
    @Override
    public void onClose() {
        rules().removeIf(ChatRule::isUntouched);
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
