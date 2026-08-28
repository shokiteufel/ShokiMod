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

/**
 * Welche anderen Regeln gesperrt werden, sobald diese hier zuschlaegt.
 *
 * Gedacht fuer Zeilen, auf die mehrere Regeln passen und von denen man nur eine
 * Reaktion will. Wer zuerst drankommt, bestimmt die Reihenfolge der Regelliste -
 * eine gesperrte Regel weiter oben kann also nicht mehr ausgesperrt werden.
 */
public class RuleBlockScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int ROW_WIDTH = 300;
    private static final int LIST_TOP = 52;

    private final Screen parent;
    private final ChatRule rule;
    private int page;

    public RuleBlockScreen(Screen parent, ChatRule rule) {
        super(Component.literal("Blocked rules"));
        this.parent = parent;
        this.rule = rule;
    }

    private List<ChatRule> others() {
        return ModConfig.INSTANCE.chat.chatRules.stream()
                .filter(other -> !other.id.equals(rule.id))
                .toList();
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (others().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);

        List<ChatRule> list = others();
        int left = width / 2 - ROW_WIDTH / 2;
        int start = page * rowsPerPage();

        for (int i = 0; i < rowsPerPage() && start + i < list.size(); i++) {
            ChatRule other = list.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            Button button = Button.builder(label(other), b -> {
                toggle(other);
                b.setMessage(label(other));
            }).bounds(left, y, ROW_WIDTH, 20).build();
            button.setTooltip(Tooltip.create(Component.literal("Filter: " + other.filter)));
            addRenderableWidget(button);
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Block none"), button -> {
            rule.blocks.clear();
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
                .bounds(left + ROW_WIDTH - 90, y, 90, 20).build());
    }

    private void toggle(ChatRule other) {
        if (!rule.blocks.remove(other.id)) rule.blocks.add(other.id);
    }

    private Component label(ChatRule other) {
        boolean blocked = rule.blocks.contains(other.id);
        Component name = Component.literal(other.label())
                .withStyle(blocked ? ChatFormatting.RED : ChatFormatting.GRAY);
        return blocked
                ? Component.literal("✕ ").withStyle(ChatFormatting.RED).append(name)
                : name;
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
        graphics.centeredText(font, Component.literal(
                        "These rules stay silent when " + rule.label() + " fires")
                .withStyle(ChatFormatting.GRAY), centerX, 28, 0xFFAAAAAA);

        if (others().isEmpty()) {
            graphics.centeredText(font, Component.literal("No other rules yet.")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 12, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font,
                    Component.literal((page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY),
                    centerX, height - 46, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
