package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.render.hud.HudPanel;
import com.shokiteufel.shokimod.render.hud.SafariHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Die Kaesten und die Fund-Banner an die gewuenschte Stelle ziehen.
 *
 * Ziehen verschiebt, Scrollen ueber einem Element aendert seine Groesse. Die
 * Kaesten werden mit Beispielinhalt gezeichnet.
 *
 * Bei den Bannern wird immer genau eines bearbeitet: das unten ausgewaehlte. Jeder
 * der zwanzig Stile hat seinen eigenen Ort, seine Groesse und Farbe - und die
 * gelten dann ueberall, wo dieser Stil eingesetzt wird, egal in welcher Stufe.
 * Die Stile 1 bis 5 haben ihren festen Platz und nehmen von hier nur die Farbe.
 */
public class HudEditorScreen extends Screen {

    private static final int BANNER_BASE_WIDTH = 120;
    private static final int BANNER_BASE_HEIGHT = 26;
    private static final DropBanner.Style[] STYLES = DropBanner.Style.values();

    private final Screen parent;
    private final Map<SafariHud.Panel, HudPanel> content = new EnumMap<>(SafariHud.Panel.class);

    private SafariHud.Panel draggingPanel = null;
    private boolean draggingBanner = false;
    private int grabX = 0;
    private int grabY = 0;

    /** Der Kasten, auf den sich der Regler bezieht. Ein Klick waehlt ihn aus */
    private SafariHud.Panel selectedPanel = SafariHud.Panel.PROGRESS;
    /** Der Stil, dessen Banner gerade bearbeitet wird */
    private int styleIndex = 0;
    private OpacitySlider opacity;
    private Button styleButton;
    private Button colourReset;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Move the panels and the drop banners"));
        this.parent = parent;
    }

    private static ModConfig.BannerCategory banners() {
        return ModConfig.INSTANCE.chat.banner;
    }

    private DropBanner.Style style() {
        return STYLES[styleIndex];
    }

    private ModConfig.BannerCategory.BannerLook look() {
        return banners().look(style());
    }

    @Override
    protected void init() {
        rebuildContent();

        opacity = new OpacitySlider(width / 2 - 105, height - 102, 210);
        addRenderableWidget(opacity);

        // Welches Banner: mit den Pfeilen durch die zwanzig Stile
        addRenderableWidget(Button.builder(Component.literal("◀"), button -> selectStyle(styleIndex - 1))
                .bounds(width / 2 - 105, height - 78, 20, 20).build());
        styleButton = Button.builder(styleLabel(), button -> selectStyle(styleIndex + 1))
                .bounds(width / 2 - 83, height - 78, 166, 20).build();
        styleButton.setTooltip(Tooltip.create(Component.literal(
                "The banner being edited. Its place, size and colour apply wherever this style is used.")));
        addRenderableWidget(styleButton);
        addRenderableWidget(Button.builder(Component.literal("▶"), button -> selectStyle(styleIndex + 1))
                .bounds(width / 2 + 85, height - 78, 20, 20).build());

        ColorSwatchButton swatch = new ColorSwatchButton(width / 2 - 105, height - 54, 100, 20,
                this::bannerColour, () -> 255, this::openBannerColour);
        swatch.setTooltip(Tooltip.create(Component.literal("Colour of this banner")));
        addRenderableWidget(swatch);

        colourReset = Button.builder(Component.literal("Tier colour"), button -> {
            look().colour = "";
            refreshColourReset();
        }).bounds(width / 2 + 5, height - 54, 100, 20).build();
        colourReset.setTooltip(Tooltip.create(Component.literal(
                "No own colour: the banner takes the colour of the tier that fires it")));
        addRenderableWidget(colourReset);
        refreshColourReset();

        addRenderableWidget(Button.builder(Component.literal("Reset positions"), button -> {
            SafariHud.Panel.PROGRESS.setPosition(0.01f, 0.02f);
            SafariHud.Panel.PROGRESS.setScale(1.0f);
            SafariHud.Panel.MISSING.setPosition(0.75f, 0.02f);
            SafariHud.Panel.MISSING.setScale(1.0f);
            SafariHud.Panel.CONTEST.setPosition(0.4f, 0.02f);
            SafariHud.Panel.CONTEST.setScale(1.0f);
            SafariHud.Panel.NEARBY.setPosition(0.01f, 0.35f);
            SafariHud.Panel.NEARBY.setScale(1.0f);
            SafariHud.Panel.HUNTING.setPosition(0.75f, 0.4f);
            SafariHud.Panel.HUNTING.setScale(1.0f);
            for (SafariHud.Panel panel : SafariHud.Panel.values()) {
                panel.setAlpha(1.0f);
            }
            banners().looks.clear();
            opacity.sync();
            refreshColourReset();
        }).bounds(width / 2 - 105, height - 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    private void selectStyle(int index) {
        styleIndex = Math.floorMod(index, STYLES.length);
        styleButton.setMessage(styleLabel());
        refreshColourReset();
    }

    private Component styleLabel() {
        return Component.literal("Banner " + style());
    }

    /** Der Inhalt aendert sich waehrend des Einrichtens nicht - einmal bauen genuegt */
    private void rebuildContent() {
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            content.put(panel, panel.build());
        }
    }

    private void refreshColourReset() {
        if (colourReset == null) return;
        String own = look().colour;
        colourReset.active = own != null && !own.isBlank();
    }

    private int bannerColour() {
        return DropBanner.parseColour(look().colour, ModConfig.RareLootCategory.defaultTierColour(2));
    }

    private void openBannerColour() {
        if (minecraft == null) return;
        ModConfig.BannerCategory.BannerLook target = look();
        minecraft.setScreen(new ColorPickerScreen(this, bannerColour(), 255,
                (rgb, fillAlpha) -> target.colour = String.format("%06X", rgb & 0xFFFFFF)));
    }

    // ---- Lage des Banner-Rahmens ----

    private int bannerWidth() {
        return (int) (BANNER_BASE_WIDTH * look().scale);
    }

    private int bannerHeight() {
        return (int) (BANNER_BASE_HEIGHT * look().scale);
    }

    private int bannerLeft() {
        return (int) (look().x * width) - bannerWidth() / 2;
    }

    private int bannerTop() {
        return (int) (look().y * height) - bannerHeight() / 2;
    }

    private boolean isOverBanner(double mouseX, double mouseY) {
        int left = bannerLeft();
        int top = bannerTop();
        return mouseX >= left && mouseX <= left + bannerWidth()
                && mouseY >= top && mouseY <= top + bannerHeight();
    }

    private boolean isOver(SafariHud.Panel panel, double mouseX, double mouseY) {
        HudPanel built = content.get(panel);
        if (built == null || built.isEmpty()) return false;

        int left = SafariHud.originX(panel);
        int top = SafariHud.originY(panel);
        return mouseX >= left && mouseX <= left + SafariHud.scaledWidth(panel, built, font)
                && mouseY >= top && mouseY <= top + SafariHud.scaledHeight(panel, built);
    }

    // ---- Maus ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isOverBanner(event.x(), event.y())) {
            draggingBanner = true;
            grabX = (int) event.x() - (bannerLeft() + bannerWidth() / 2);
            grabY = (int) event.y() - (bannerTop() + bannerHeight() / 2);
            return true;
        }
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!isOver(panel, event.x(), event.y())) continue;
            draggingPanel = panel;
            selectedPanel = panel;
            opacity.sync();
            grabX = (int) event.x() - SafariHud.originX(panel);
            grabY = (int) event.y() - SafariHud.originY(panel);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBanner) {
            // Der Rahmen haengt an seiner Mitte, so wie das Banner spaeter auch
            look().x = Math.clamp((float) (event.x() - grabX) / width, 0f, 1f);
            look().y = Math.clamp((float) (event.y() - grabY) / height, 0f, 1f);
            return true;
        }
        if (draggingPanel == null) return super.mouseDragged(event, dragX, dragY);

        float x = (float) (event.x() - grabX) / width;
        float y = (float) (event.y() - grabY) / height;
        draggingPanel.setPosition(Math.clamp(x, 0f, 0.98f), Math.clamp(y, 0f, 0.98f));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPanel = null;
        draggingBanner = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOverBanner(mouseX, mouseY)) {
            look().scale = Math.clamp(look().scale + (float) scrollY * 0.1f, 0.5f, 3.0f);
            return true;
        }
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!isOver(panel, mouseX, mouseY)) continue;
            panel.setScale(panel.scale() + (float) scrollY * 0.1f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- Zeichnen ----

    /**
     * Kein Abdunkeln, kein Weichzeichner: beides wuerde die halbdurchsichtigen
     * Kaesten blasser zeigen, als sie im Spiel sind.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            HudPanel built = content.get(panel);
            if (built == null || built.isEmpty()) continue;

            int left = SafariHud.originX(panel);
            int top = SafariHud.originY(panel);
            int right = left + SafariHud.scaledWidth(panel, built, font);
            int bottom = top + SafariHud.scaledHeight(panel, built);
            int colour = panel == selectedPanel ? 0xFFFFAA00
                    : isOver(panel, mouseX, mouseY) ? 0xC0FFFFFF : 0x50FFFFFF;
            frame(graphics, left, top, right, bottom, colour);

            SafariHud.draw(graphics, font, panel, built);
        }

        // Das eine Banner, das gerade bearbeitet wird
        int left = bannerLeft();
        int top = bannerTop();
        int right = left + bannerWidth();
        int bottom = top + bannerHeight();
        int colour = 0xFF000000 | bannerColour();
        graphics.fill(left, top, right, bottom, 0xA0101010);
        frame(graphics, left, top, right, bottom, isOverBanner(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFFFAA00);
        graphics.fill(left, top, left + 3, bottom, colour);
        graphics.centeredText(font, "Banner " + (styleIndex + 1), (left + right) / 2, (top + bottom) / 2 - 4, 0xFFFFFFFF);

        graphics.centeredText(font, this.title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(
                        "Drag to move  ·  scroll to resize  ·  pick the banner below; styles 1-5 keep their place and take only the colour")
                .withStyle(ChatFormatting.GRAY), width / 2, 26, 0xFFAAAAAA);

        if (isOverBanner(mouseX, mouseY)) {
            graphics.centeredText(font, Component.literal(String.format("%s   size %.0f%%",
                            style(), look().scale * 100))
                    .withStyle(ChatFormatting.YELLOW), width / 2, 38, 0xFFFFFF55);
            return;
        }
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!isOver(panel, mouseX, mouseY)) continue;
            graphics.centeredText(font, Component.literal(String.format("%s   size %.0f%%",
                            panel.name().toLowerCase(), panel.scale() * 100))
                    .withStyle(ChatFormatting.YELLOW), width / 2, 38, 0xFFFFFF55);
            break;
        }
    }

    private static void frame(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int colour) {
        graphics.fill(left - 1, top - 1, right + 1, top, colour);
        graphics.fill(left - 1, bottom, right + 1, bottom + 1, colour);
        graphics.fill(left - 1, top, left, bottom, colour);
        graphics.fill(right, top, right + 1, bottom, colour);
    }

    /** Regler fuer die Deckkraft des ausgewaehlten Kastens */
    private class OpacitySlider extends AbstractSliderButton {

        OpacitySlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), 0);
            sync();
        }

        void sync() {
            this.value = (selectedPanel.alpha() - 0.1) / 0.9;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("Background of %s: %.0f%%",
                    selectedPanel.name().toLowerCase(), selectedPanel.alpha() * 100)));
        }

        @Override
        protected void applyValue() {
            selectedPanel.setAlpha((float) (0.1 + value * 0.9));
            updateMessage();
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
