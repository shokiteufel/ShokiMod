package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.EffectEntry;
import com.shokiteufel.shokimod.handler.EffectsTracker;
import com.shokiteufel.shokimod.render.hud.EffectsHud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Die gemerkten Effekte, einer je Zeile: Name, Restzeit, und je Effekt ob er im Kasten steht
 * und ob er Alarm gibt. Gemerkt wird, sobald man /effects oeffnet.
 *
 * Was nicht gebraucht wird, kann versteckt werden: Ein versteckter Effekt steht nicht mehr in der
 * Liste, im Kasten und gibt keinen Alarm - und er kommt nicht zurueck, wenn /effects ihn das
 * naechste Mal zeigt. Unter "Hidden" lassen sich alle wieder holen oder ganz vergessen.
 */
public class EffectsScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 170;
    private static final int TIME_WIDTH = 74;
    private static final int TOGGLE_WIDTH = 62;
    private static final int HIDE_WIDTH = 36;
    private static final int LIST_TOP = 50;
    /** Unten bleiben zwei Knopfleisten frei: Auswahl und Seiten, darunter die Schalter */
    private static final int BOTTOM_SPACE = 84;
    private static final int GRID_WIDTH = NAME_WIDTH + TIME_WIDTH + 2 * TOGGLE_WIDTH + HIDE_WIDTH + 16;

    private final Screen parent;
    private static int page = 0;
    /** Zeigt die Liste die versteckten Effekte statt der gemerkten? */
    private static boolean showHidden = false;

    public EffectsScreen(Screen parent) {
        super(Component.literal("Effects"));
        this.parent = parent;
        showHidden = false;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - BOTTOM_SPACE - LIST_TOP) / ROW_HEIGHT);
    }

    private List<EffectEntry> shown() {
        return showHidden ? EffectsTracker.hiddenEntries() : EffectsTracker.entries();
    }

    private int pageCount() {
        return Math.max(1, (shown().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static Button withTip(Button button, String tip) {
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        return button;
    }

    @Override
    protected void init() {
        page = Math.clamp(page, 0, pageCount() - 1);
        List<EffectEntry> liste = shown();
        int left = width / 2 - GRID_WIDTH / 2;

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            EffectEntry e = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            int x = left + NAME_WIDTH + TIME_WIDTH + 8;

            if (showHidden) {
                addRenderableWidget(withTip(Button.builder(Component.literal("Show").withStyle(ChatFormatting.GREEN),
                        button -> {
                            EffectsTracker.unhide(e);
                            rebuild();
                        }).bounds(x, y, TOGGLE_WIDTH, 20).build(),
                        "Brings the effect back into the list. HUD and Alert stay off until you tick them."));
                addRenderableWidget(withTip(Button.builder(Component.literal("Forget").withStyle(ChatFormatting.RED),
                        button -> {
                            EffectsTracker.remove(e);
                            rebuild();
                        }).bounds(x + TOGGLE_WIDTH + 4, y, TOGGLE_WIDTH, 20).build(),
                        "Deletes the entry. It is remembered again the next time /effects lists it."));
                continue;
            }

            addRenderableWidget(Button.builder(
                    Component.literal(e.hud ? "HUD ☑" : "HUD ☐")
                            .withStyle(e.hud ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                    button -> {
                        EffectsTracker.toggleHud(e);
                        rebuild();
                    }).bounds(x, y, TOGGLE_WIDTH, 20).build());
            addRenderableWidget(Button.builder(
                    Component.literal(e.alert ? "Alert ☑" : "Alert ☐")
                            .withStyle(e.alert ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY),
                    button -> {
                        EffectsTracker.toggleAlert(e);
                        rebuild();
                    }).bounds(x + TOGGLE_WIDTH + 4, y, TOGGLE_WIDTH, 20).build());
            addRenderableWidget(withTip(Button.builder(Component.literal("Hide").withStyle(ChatFormatting.RED),
                    button -> {
                        EffectsTracker.hide(e);
                        rebuild();
                    }).bounds(x + 2 * TOGGLE_WIDTH + 8, y, HIDE_WIDTH, 20).build(),
                    "Hides the effect: no list entry, no panel row, no alert - and it stays hidden when /effects lists it again."));
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
            }).bounds(left + GRID_WIDTH - 96, barY, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + GRID_WIDTH - 20, barY, 20, 20).build());
        }

        // Oeffnet /effects, damit die Mod es liest - ohne den Befehl selbst zu tippen
        addRenderableWidget(Button.builder(Component.literal("Open /effects"), button -> {
            if (minecraft != null && minecraft.getConnection() != null) {
                minecraft.setScreen(null);
                minecraft.getConnection().sendCommand("effects");
            }
        }).bounds(left, height - 28, 110, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(ModConfig.INSTANCE.effects.enabled ? "Enabled ☑" : "Enabled ☐")
                        .withStyle(ModConfig.INSTANCE.effects.enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
                button -> {
                    ModConfig.INSTANCE.effects.enabled = !ModConfig.INSTANCE.effects.enabled;
                    ModConfig.INSTANCE.saveNow();
                    rebuild();
                }).bounds(left + 116, height - 28, 90, 20).build());

        // Der Ton des Alarms: eine eigene Datei aus config/shokimod/sounds, leer heisst stumm
        boolean hasSound = ModConfig.INSTANCE.effects.sound != null && !ModConfig.INSTANCE.effects.sound.isBlank();
        addRenderableWidget(Button.builder(
                Component.literal(hasSound ? "Sound ♪" : "Sound").withStyle(
                        hasSound ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                button -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new SoundPickerScreen(this,
                                () -> ModConfig.INSTANCE.effects.sound,
                                picked -> ModConfig.INSTANCE.effects.sound = picked,
                                1.0f));
                    }
                }).bounds(left + 212, height - 28, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + GRID_WIDTH - 80, height - 28, 80, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.centeredText(font, Component.literal(showHidden ? "Effects - hidden" : "Effects")
                .withStyle(ChatFormatting.GOLD), centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, Component.literal(showHidden
                        ? "Hidden effects stay out of the list, the panel and the alerts"
                        : "Open /effects once - every effect in it is remembered here")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 26, 0xFF888888);
        if (!ModConfig.INSTANCE.effects.enabled) {
            graphics.centeredText(font, Component.literal(
                            "Panel and alerts are off - switch Enabled on below")
                    .withStyle(ChatFormatting.RED), centerX, 38, 0xFFFF5555);
        }

        List<EffectEntry> liste = shown();
        if (liste.isEmpty()) {
            String empty = showHidden ? "Nothing hidden"
                    : EffectsTracker.hiddenEntries().isEmpty() ? "Nothing remembered yet"
                    : "Everything is hidden - see Hidden below";
            graphics.centeredText(font, Component.literal(empty).withStyle(ChatFormatting.GRAY),
                    centerX, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }
        int left = width / 2 - GRID_WIDTH / 2;
        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            EffectEntry e = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT + 6;
            graphics.text(font, e.name, left, y, 0xFFFFFFFF, false);
            Long rest = EffectsTracker.remaining(e);
            String time = rest == null ? "?" : rest <= 0L ? "off" : EffectsHud.format(rest);
            int colour = rest == null ? 0xFFAAAAAA : rest <= 0L ? 0xFFFF5555 : 0xFF55FF55;
            graphics.text(font, time, left + NAME_WIDTH + 4, y, colour, false);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), left + GRID_WIDTH - 48, height - 46, 0xFFAAAAAA);
        }
    }
}
