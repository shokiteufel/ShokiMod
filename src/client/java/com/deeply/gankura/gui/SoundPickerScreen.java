package com.deeply.gankura.gui;

import com.deeply.gankura.data.ChatRule;
import com.deeply.gankura.util.CustomSoundPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Auswahl einer eigenen Sounddatei.
 *
 * Zeigt den ganzen Ordner auf einmal, statt durchzublättern - ab einer Handvoll
 * Dateien ist das der einzige Weg, der noch übersichtlich bleibt. Ein Klick wählt
 * aus und spielt sofort an, damit man nicht raten muss, welche Datei welche ist.
 */
public class SoundPickerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int COLUMNS = 2;
    private static final int COLUMN_WIDTH = 210;
    private static final int LIST_TOP = 52;

    private final Screen parent;
    private final ChatRule rule;
    private int page;

    public SoundPickerScreen(Screen parent, ChatRule rule) {
        super(Component.literal("Sound file"));
        this.parent = parent;
        this.rule = rule;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int perPage() {
        return rowsPerPage() * COLUMNS;
    }

    private int pageCount() {
        return Math.max(1, (CustomSoundPlayer.availableSounds().size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);

        List<String> files = CustomSoundPlayer.availableSounds();
        int gridWidth = COLUMNS * (COLUMN_WIDTH + 4) - 4;
        int left = width / 2 - gridWidth / 2;
        int start = page * perPage();

        for (int i = 0; i < perPage() && start + i < files.size(); i++) {
            String file = files.get(start + i);
            int x = left + (i % COLUMNS) * (COLUMN_WIDTH + 4);
            int y = LIST_TOP + (i / COLUMNS) * ROW_HEIGHT;

            Button button = Button.builder(fileLabel(file), b -> {
                rule.soundFile = file;
                CustomSoundPlayer.play(file, rule.volume);
                rebuild();
            }).bounds(x, y, COLUMN_WIDTH, 20).build();
            button.setTooltip(Tooltip.create(Component.literal("Click to pick and hear it")));
            addRenderableWidget(button);
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("No sound"), button -> {
            rule.soundFile = "";
            rebuild();
        }).bounds(left, y, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Open folder"), button ->
                net.minecraft.util.Util.getPlatform().openPath(CustomSoundPlayer.SOUND_DIRECTORY)
        ).bounds(left + 94, y, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Rescan"), button -> rebuild())
                .bounds(left + 198, y, 80, 20).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + gridWidth - 138, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + gridWidth - 114, y, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 90, y, 90, 20).build());
    }

    private Component fileLabel(String file) {
        boolean selected = file.equalsIgnoreCase(rule.soundFile);
        Component name = Component.literal(file)
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY);
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

        int count = CustomSoundPlayer.availableSounds().size();
        graphics.centeredText(font, Component.literal(
                        count + " file(s) in config/gankura/sounds  ·  wav, mp3, ogg")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 28, 0xFF808080);

        if (count == 0) {
            graphics.centeredText(font, Component.literal(
                            "Folder is empty - put a file in, then press Rescan")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 14, 0xFFAAAAAA);
        }

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
