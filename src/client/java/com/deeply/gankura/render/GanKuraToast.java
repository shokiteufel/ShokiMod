package com.deeply.gankura.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Einblendung oben rechts, wahlweise mit Item als Symbol.
 *
 * Aufbau nach Skyblockers BasicToast (LGPL-3.0), aber ohne eigene Textur:
 * der Hintergrund wird gefuellt gezeichnet, damit keine Ressourcendatei noetig ist.
 */
public class GanKuraToast implements Toast {

    private static final int BACKGROUND = 0xE0101010;
    private static final int BORDER = 0xFF5555FF;

    private final long displayDuration;
    private final @Nullable ItemStack icon;
    private final List<FormattedCharSequence> lines;
    private final int width;

    private Visibility visibility = Visibility.SHOW;

    public GanKuraToast(Component message, long displayDuration, @Nullable ItemStack icon) {
        Font font = Minecraft.getInstance().font;
        this.lines = font.split(message, 200);
        this.displayDuration = displayDuration;
        this.icon = icon;
        this.width = lines.stream().mapToInt(font::width).max().orElse(200) + (icon == null ? 10 : 30);
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (time > displayDuration) visibility = Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long startTime) {
        graphics.fill(0, 0, width(), height(), BACKGROUND);
        graphics.fill(0, 0, width(), 1, BORDER);
        graphics.fill(0, height() - 1, width(), height(), BORDER);

        int offset = 0;
        if (icon != null) {
            graphics.fakeItem(icon, 4, 4);
            offset = 20;
        }
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), 4 + offset, 8 + i * 12, -1, false);
        }
    }

    @Override
    public int height() {
        return 8 + 4 + Math.max(lines.size(), 1) * 12;
    }

    @Override
    public int width() {
        return width;
    }
}
