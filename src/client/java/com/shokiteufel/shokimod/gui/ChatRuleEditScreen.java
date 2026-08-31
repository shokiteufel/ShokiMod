package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ChatRule;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Editor einer einzelnen Chatregel.
 *
 * Links die Bedingung, rechts die Reaktionen - jede fuer sich abschaltbar,
 * indem man das Feld leer laesst.
 */
public class ChatRuleEditScreen extends Screen {

    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW = 24;
    private static final int COLUMN_WIDTH = 220;

    private final ChatRuleScreen parent;
    /** Voll deckendes Rot - 26.1 liest die Textfarbe als ARGB, ohne Alpha bleibt nichts uebrig */
    private static final int INVALID_COLOR = 0xFFFF5555;

    private final ChatRule rule;

    public ChatRuleEditScreen(ChatRuleScreen parent, ChatRule rule) {
        super(Component.literal("Edit rule"));
        this.parent = parent;
        this.rule = rule;
    }

    @Override
    protected void init() {
        int leftColumn = width / 2 - COLUMN_WIDTH - 8;
        int rightColumn = width / 2 + 8;
        int top = 46;

        // ---------- Bedingung ----------
        int y = top;
        addRenderableWidget(box(leftColumn, y, "Name", rule.label, 48,
                value -> rule.label = value, null));
        y += ROW;

        EditBox filterBox = box(leftColumn, y, "Filter", rule.filter, 256,
                value -> rule.filter = value, "The text or pattern to look for");
        filterBox.setResponder(value -> {
            rule.filter = value;
            filterBox.setTextColor(rule.filterIsValid() ? EditBox.DEFAULT_TEXT_COLOR : INVALID_COLOR);
        });
        addRenderableWidget(filterBox);
        y += ROW;

        EditBox exceptBox = box(leftColumn, y, "Except", rule.except, 256,
                value -> rule.except = value,
                "The rule stays silent when the line contains this. Handy for a filter that is "
                        + "deliberately broad. Uses the same regex and case settings as the filter.");
        exceptBox.setResponder(value -> {
            rule.except = value;
            exceptBox.setTextColor(rule.exceptIsValid() ? EditBox.DEFAULT_TEXT_COLOR : INVALID_COLOR);
        });
        addRenderableWidget(exceptBox);
        y += ROW;

        addRenderableWidget(toggle(leftColumn, y, "Regex", rule.regex,
                value -> rule.regex = value,
                "Off: plain text.  On: the filter is a regular expression, and $1, $2 can be used in the outputs."));
        y += ROW;
        addRenderableWidget(toggle(leftColumn, y, "Partial match", rule.partialMatch,
                value -> rule.partialMatch = value,
                "Off: the whole line must match the filter."));
        y += ROW;
        addRenderableWidget(toggle(leftColumn, y, "Ignore case", rule.ignoreCase,
                value -> rule.ignoreCase = value, null));
        y += ROW;
        addRenderableWidget(toggle(leftColumn, y, "Include formatting", rule.includeFormatting,
                value -> rule.includeFormatting = value,
                "On: colour codes stay in the text that is compared."));
        y += ROW;

        Button areaButton = Button.builder(areaLabel(), button ->
                minecraft.setScreen(new AreaPickerScreen(this, rule)))
                .bounds(leftColumn, y, COLUMN_WIDTH, WIDGET_HEIGHT).build();
        areaButton.setTooltip(Tooltip.create(Component.literal(
                "Pick the areas the rule works in. Nothing selected means everywhere. "
                        + "The list grows as you visit new areas.")));
        addRenderableWidget(areaButton);

        // ---------- Reaktionen ----------
        y = top;
        addRenderableWidget(toggle(rightColumn, y, "Hide message", rule.hideMessage,
                value -> rule.hideMessage = value, "Removes the original line from chat."));
        y += ROW;
        addRenderableWidget(box(rightColumn, y, "Replace with", rule.replacement, 256,
                value -> rule.replacement = value, "Leave empty to keep the original. & works as colour code."));
        y += ROW;
        addRenderableWidget(box(rightColumn, y, "Action bar", rule.actionBar, 256,
                value -> rule.actionBar = value, "Text above the hotbar."));
        y += ROW;
        addRenderableWidget(box(rightColumn, y, "Banner", rule.announcement, 256,
                value -> rule.announcement = value, "Large text across the screen."));
        y += ROW;
        addRenderableWidget(number(rightColumn, y, "Banner ms", rule.announcementMillis,
                value -> rule.announcementMillis = value));
        y += ROW;
        addRenderableWidget(box(rightColumn, y, "Toast", rule.toast, 256,
                value -> rule.toast = value, "Small box in the top right corner."));
        y += ROW;
        addRenderableWidget(box(rightColumn, y, "Toast icon", rule.toastIcon, 128,
                value -> rule.toastIcon = value, "Item id like minecraft:diamond"));
        y += ROW;
        addRenderableWidget(box(rightColumn, y, "Sound id", rule.soundId, 128,
                value -> rule.soundId = value, "Minecraft sound like block.note_block.pling"));
        y += ROW;

        Button fileButton = Button.builder(fileLabel(), button -> {
            if (minecraft != null) minecraft.setScreen(new SoundPickerScreen(this, () -> rule.soundFile, file -> rule.soundFile = file, rule.volume));
        }).bounds(rightColumn, y, COLUMN_WIDTH, WIDGET_HEIGHT).build();
        fileButton.setTooltip(Tooltip.create(Component.literal(
                "Opens the list of your own files in config/shokimod/sounds.")));
        addRenderableWidget(fileButton);
        y += ROW;

        addVolumeRow(rightColumn, y);
        y += ROW;

        Button blocksButton = Button.builder(blocksLabel(), button -> {
            if (minecraft != null) minecraft.setScreen(new RuleBlockScreen(this, rule));
        }).bounds(rightColumn, y, COLUMN_WIDTH, WIDGET_HEIGHT).build();
        blocksButton.setTooltip(Tooltip.create(Component.literal(
                "Rules that stay silent when this one fires. The list order decides who goes first.")));
        addRenderableWidget(blocksButton);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, WIDGET_HEIGHT).build());
    }

    // ---------- Bausteine ----------

    private EditBox box(int x, int y, String hint, String value, int maxLength,
                        java.util.function.Consumer<String> setter, String tooltip) {
        EditBox editBox = new EditBox(font, x, y, COLUMN_WIDTH, WIDGET_HEIGHT, Component.literal(hint));
        editBox.setMaxLength(maxLength);
        editBox.setValue(value == null ? "" : value);
        editBox.setHint(Component.literal(hint));
        editBox.setResponder(setter);
        if (tooltip != null) editBox.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return editBox;
    }

    private Button toggle(int x, int y, String name, boolean current,
                          java.util.function.Consumer<Boolean> setter, String tooltip) {
        boolean[] state = {current};
        Button button = Button.builder(toggleLabel(name, state[0]), b -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            b.setMessage(toggleLabel(name, state[0]));
        }).bounds(x, y, COLUMN_WIDTH, WIDGET_HEIGHT).build();
        if (tooltip != null) button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return button;
    }

    private EditBox number(int x, int y, String hint, int value,
                           java.util.function.IntConsumer setter) {
        EditBox editBox = new EditBox(font, x, y, COLUMN_WIDTH, WIDGET_HEIGHT, Component.literal(hint));
        editBox.setMaxLength(6);
        editBox.setValue(String.valueOf(value));
        editBox.setHint(Component.literal(hint));
        editBox.setResponder(text -> {
            try {
                setter.accept(Math.max(200, Integer.parseInt(text.trim())));
                editBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            } catch (NumberFormatException e) {
                editBox.setTextColor(INVALID_COLOR);
            }
        });
        return editBox;
    }

    /**
     * Lautstaerke: Schieberegler und Zahlenfeld nebeneinander, beide auf denselben Wert.
     * Intern rechnet der Abspieler mit 0.0 bis 1.0, angezeigt wird 1 bis 100.
     */
    private void addVolumeRow(int x, int y) {
        int sliderWidth = 148;
        int boxWidth = COLUMN_WIDTH - sliderWidth - 6;

        EditBox box = new EditBox(font, x + sliderWidth + 6, y, boxWidth, WIDGET_HEIGHT,
                Component.literal("Volume"));
        // Reichlich Platz, sonst laesst sich bei vollem Feld nichts mehr tippen
        box.setMaxLength(8);
        box.setValue(String.valueOf(percent()));
        box.setTooltip(Tooltip.create(Component.literal("Volume from 1 to 100")));

        VolumeSlider slider = new VolumeSlider(x, y, sliderWidth, box);
        box.setResponder(text -> {
            try {
                int value = Integer.parseInt(text.trim());
                if (value < 1 || value > 100) {
                    box.setTextColor(INVALID_COLOR);
                    return;
                }
                rule.volume = value / 100f;
                box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                slider.syncFromRule();
            } catch (NumberFormatException e) {
                box.setTextColor(INVALID_COLOR);
            }
        });

        addRenderableWidget(slider);
        addRenderableWidget(box);
    }

    private int percent() {
        return Math.clamp(Math.round(rule.volume * 100f), 1, 100);
    }

    /** Schieberegler, der beim Loslassen das Zahlenfeld nachzieht */
    private class VolumeSlider extends AbstractSliderButton {
        private final EditBox box;

        VolumeSlider(int x, int y, int width, EditBox box) {
            super(x, y, width, WIDGET_HEIGHT, Component.empty(), (percent() - 1) / 99.0);
            this.box = box;
            updateMessage();
        }

        void syncFromRule() {
            this.value = (percent() - 1) / 99.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Volume: " + percent()));
        }

        @Override
        protected void applyValue() {
            int percent = (int) Math.round(1 + value * 99.0);
            rule.volume = Math.clamp(percent, 1, 100) / 100f;
            // Nur den Text setzen, nicht den Responder ausloesen - sonst schieben
            // sich Regler und Feld gegenseitig hin und her
            box.setValue(String.valueOf(percent));
            box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
        }
    }

    private static Component toggleLabel(String name, boolean on) {
        return Component.literal(name + ": ")
                .append(Component.literal(on ? "on" : "off")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
    }

    private Component blocksLabel() {
        int count = rule.blocks == null ? 0 : rule.blocks.size();
        return count == 0
                ? Component.literal("Blocks: none").withStyle(ChatFormatting.GRAY)
                : Component.literal("Blocks: " + count).withStyle(ChatFormatting.YELLOW);
    }

    private Component areaLabel() {
        if (rule.areas == null || rule.areas.isEmpty()) {
            return Component.literal("Area: everywhere").withStyle(ChatFormatting.GRAY);
        }
        String first = rule.areas.getFirst();
        String extra = rule.areas.size() > 1 ? " +" + (rule.areas.size() - 1) : "";
        return Component.literal("Area: " + first + extra).withStyle(ChatFormatting.YELLOW);
    }

    private Component fileLabel() {
        return rule.soundFile == null || rule.soundFile.isBlank()
                ? Component.literal("Sound file: none").withStyle(ChatFormatting.GRAY)
                : Component.literal("Sound file: " + rule.soundFile).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.centeredText(font, this.title, centerX, 14, 0xFFFFFFFF);

        graphics.text(font, Component.literal("When").withStyle(ChatFormatting.DARK_GRAY),
                centerX - COLUMN_WIDTH - 8, 34, 0xFF808080);
        graphics.text(font, Component.literal("Then").withStyle(ChatFormatting.DARK_GRAY),
                centerX + 8, 34, 0xFF808080);

        if (!rule.filterIsValid()) {
            graphics.centeredText(font, Component.literal("Invalid regex - the rule will not fire")
                    .withStyle(ChatFormatting.RED), centerX, height - 44, 0xFFFF5555);
        } else if (!rule.exceptIsValid()) {
            // Eine kaputte Ausnahme haelt nichts auf - die Regel feuert dann wieder ueberall
            graphics.centeredText(font, Component.literal("Invalid regex in Except - nothing is excluded")
                    .withStyle(ChatFormatting.RED), centerX, height - 44, 0xFFFF5555);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) {
            minecraft.setScreen(parent);
            parent.rebuild();
        }
    }
}
