package com.deeply.gankura.gui;

import com.deeply.gankura.data.ModConfig;
import com.deeply.gankura.render.AlertBanner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Alternative Darstellung der Marker-Einstellungen.
 *
 * In der Config braucht jede Option eine eigene Zeile, weil MoulConfig pro Eintrag
 * nur ein Bedienelement rendert. Hier liegen Schalter, Name und Farbe nebeneinander,
 * so wie in der Liste der eigenen Mobs.
 */
public class MarkerSettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 26;
    private static final int ROW_WIDTH = 344;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LIST_TOP = 48;

    private static final int COLUMN_TOGGLE = 0;
    private static final int COLUMN_LABEL = 52;
    private static final int COLUMN_TEST = 252;
    private static final int COLUMN_SWATCH = 300;

    private final Screen parent;

    /** Eine Zeile bindet Schalter und Farbe eines Markers zusammen */
    private record Row(String label, String hint,
                       BooleanSupplier enabled, Consumer<Boolean> setEnabled,
                       IntSupplier colour, IntConsumer setColour,
                       /** Optional: zeigt die Wirkung sofort, ohne auf den Anlass zu warten */
                       Runnable preview) {
    }

    public MarkerSettingsScreen(Screen parent) {
        super(Component.literal("Marker settings"));
        this.parent = parent;
    }

    private static List<Row> rows() {
        ModConfig.CustomizeCategory c = ModConfig.INSTANCE.customize;
        return List.of(
                new Row("Sparkling critters", "Rare critters, found by their name prefix",
                        () -> c.sparklingEnabled, v -> c.sparklingEnabled = v,
                        c::sparklingColorRGB, rgb -> c.sparklingColor = hex(rgb), null),
                new Row("Breakable walls", "Snooper walls in the Cavern, Troodon walls in the Icy biome",
                        () -> c.highlightSafariWalls, v -> c.highlightSafariWalls = v,
                        c::wallColorRGB, rgb -> c.wallColor = hex(rgb), null),
                new Row("Rockmite mounds", "Found by their hitbox instead of their texture",
                        () -> c.highlightMounds, v -> c.highlightMounds = v,
                        c::moundColorRGB, rgb -> c.moundColor = hex(rgb), null),
                new Row("Shiny alert", "Full-screen banner when a rare critter shows up",
                        () -> c.shinyAlertEnabled, v -> c.shinyAlertEnabled = v,
                        c::shinyColorRGB, rgb -> c.shinyColor = hex(rgb),
                        MarkerSettingsScreen::previewShiny),
                // Zeile ohne Farbe: colour bleibt null, dann wird kein Farbfeld gebaut
                new Row("Only in matching biome", "Show Safari markers only while you are in their biome",
                        () -> c.safariBiomeOnly, v -> c.safariBiomeOnly = v,
                        null, null, null)
        );
    }

    /** Zeigt das Banner mit Beispieldaten, damit man Farbe und Groesse beurteilen kann */
    private static void previewShiny() {
        AlertBanner.show("SHINY!", "Timil", "-87 42 156",
                ModConfig.INSTANCE.customize.shinyColorRGB(), 7000L);
    }

    private static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    @Override
    protected void init() {
        int left = width / 2 - ROW_WIDTH / 2;
        List<Row> rows = rows();

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            addRenderableWidget(Button.builder(onOff(row.enabled().getAsBoolean()), button -> {
                boolean next = !row.enabled().getAsBoolean();
                row.setEnabled().accept(next);
                button.setMessage(onOff(next));
            }).bounds(left + COLUMN_TOGGLE, y, 44, WIDGET_HEIGHT).build());

            if (row.preview() != null) {
                Button test = Button.builder(Component.literal("Test"),
                                button -> row.preview().run())
                        .bounds(left + COLUMN_TEST, y, 44, WIDGET_HEIGHT).build();
                test.setTooltip(Tooltip.create(Component.literal(
                        "Shows the banner right now, with the colour set here.")));
                addRenderableWidget(test);
            }

            if (row.colour() != null) {
                addRenderableWidget(new ColorSwatchButton(left + COLUMN_SWATCH, y, 44, WIDGET_HEIGHT,
                        row.colour(), () -> 255, () -> openPicker(row)));
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 50, height - 32, 100, WIDGET_HEIGHT).build());
    }

    private void openPicker(Row row) {
        if (minecraft == null) return;
        minecraft.setScreen(new ColorPickerScreen(this, row.colour().getAsInt(), 255,
                (rgb, fillAlpha) -> row.setColour().accept(rgb)));
    }

    private static Component onOff(boolean on) {
        return on ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.DARK_GRAY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int left = centerX - ROW_WIDTH / 2;
        graphics.centeredText(font, this.title, centerX, 16, 0xFFFFFFFF);
        graphics.centeredText(font,
                Component.literal("Switch and colour side by side").withStyle(ChatFormatting.DARK_GRAY),
                centerX, 30, 0xFF808080);

        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            graphics.text(font, Component.literal(row.label()), left + COLUMN_LABEL, y + 2, 0xFFFFFFFF);
            graphics.text(font, Component.literal(row.hint()).withStyle(ChatFormatting.DARK_GRAY),
                    left + COLUMN_LABEL, y + 12, 0xFF707070);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
