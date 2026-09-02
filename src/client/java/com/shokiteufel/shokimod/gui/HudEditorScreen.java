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
 * Die Kaesten und die drei Fund-Banner an die gewuenschte Stelle ziehen.
 *
 * Ziehen verschiebt, Scrollen ueber einem Element aendert seine Groesse. Die
 * Kaesten werden mit Beispielinhalt gezeichnet, die Banner als Rahmen mit Namen -
 * so laesst sich alles einrichten, ohne auf einen Fund oder die Safari zu warten.
 *
 * Bei den Bannern gilt: Ort und Groesse zaehlen fuer die Stile ab 6. Die ersten
 * fuenf haben ihren festen Platz und nehmen aus dem Editor nur die Farbe.
 */
public class HudEditorScreen extends Screen {

    private static final int BANNER_COUNT = 3;
    private static final int BANNER_BASE_WIDTH = 110;
    private static final int BANNER_BASE_HEIGHT = 24;

    private final Screen parent;
    private final Map<SafariHud.Panel, HudPanel> content = new EnumMap<>(SafariHud.Panel.class);

    private SafariHud.Panel draggingPanel = null;
    /** 0 heisst: kein Banner wird gerade gezogen */
    private int draggingBanner = 0;
    private int grabX = 0;
    private int grabY = 0;

    /** Der Kasten, auf den sich der Regler bezieht. Ein Klick waehlt ihn aus */
    private SafariHud.Panel selectedPanel = SafariHud.Panel.PROGRESS;
    /** Das Banner, auf das sich Farbe und Zuruecksetzen beziehen */
    private int selectedBanner = 1;
    private OpacitySlider opacity;
    private Button colourReset;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Move the panels and the drop banners"));
        this.parent = parent;
    }

    private static ModConfig.RareLootCategory banners() {
        return ModConfig.INSTANCE.chat.rareLoot;
    }

    @Override
    protected void init() {
        rebuildContent();

        opacity = new OpacitySlider(width / 2 - 105, height - 78, 210);
        addRenderableWidget(opacity);

        // Farbe des ausgewaehlten Banners: das Feld zeigt sie, ein Klick oeffnet den Waehler
        ColorSwatchButton swatch = new ColorSwatchButton(width / 2 - 105, height - 54, 100, 20,
                () -> bannerColour(selectedBanner), () -> 255, this::openBannerColour);
        swatch.setTooltip(Tooltip.create(Component.literal("Colour of the selected banner")));
        addRenderableWidget(swatch);

        colourReset = Button.builder(Component.literal("Tier colour"), button -> {
            banners().setBannerColour(selectedBanner, "");
            refreshColourReset();
        }).bounds(width / 2 + 5, height - 54, 100, 20).build();
        colourReset.setTooltip(Tooltip.create(Component.literal(
                "Back to the tier colour: green, gold or purple")));
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
            for (SafariHud.Panel panel : SafariHud.Panel.values()) {
                panel.setAlpha(1.0f);
            }
            for (int tier = 1; tier <= BANNER_COUNT; tier++) {
                banners().setBannerX(tier, 0.5f);
                banners().setBannerY(tier, 0.2f + tier * 0.1f);
                banners().setBannerScale(tier, 1.0f);
                banners().setBannerColour(tier, "");
            }
            opacity.sync();
            refreshColourReset();
        }).bounds(width / 2 - 105, height - 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    /** Der Inhalt aendert sich waehrend des Einrichtens nicht - einmal bauen genuegt */
    private void rebuildContent() {
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            content.put(panel, panel.build());
        }
    }

    private void refreshColourReset() {
        if (colourReset == null) return;
        String own = banners().bannerColour(selectedBanner);
        colourReset.active = own != null && !own.isBlank();
    }

    private int bannerColour(int tier) {
        return DropBanner.parseColour(banners().bannerColour(tier),
                ModConfig.RareLootCategory.defaultTierColour(tier));
    }

    private void openBannerColour() {
        if (minecraft == null) return;
        int tier = selectedBanner;
        minecraft.setScreen(new ColorPickerScreen(this, bannerColour(tier), 255,
                (rgb, fillAlpha) -> banners().setBannerColour(tier, String.format("%06X", rgb & 0xFFFFFF))));
    }

    // ---- Lage der Banner-Rahmen ----

    private int bannerWidth(int tier) {
        return (int) (BANNER_BASE_WIDTH * banners().bannerScale(tier));
    }

    private int bannerHeight(int tier) {
        return (int) (BANNER_BASE_HEIGHT * banners().bannerScale(tier));
    }

    private int bannerLeft(int tier) {
        return (int) (banners().bannerX(tier) * width) - bannerWidth(tier) / 2;
    }

    private int bannerTop(int tier) {
        return (int) (banners().bannerY(tier) * height) - bannerHeight(tier) / 2;
    }

    private boolean isOverBanner(int tier, double mouseX, double mouseY) {
        int left = bannerLeft(tier);
        int top = bannerTop(tier);
        return mouseX >= left && mouseX <= left + bannerWidth(tier)
                && mouseY >= top && mouseY <= top + bannerHeight(tier);
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
        // Banner zuerst: sie liegen ueber den Kaesten, so wie im Spiel auch
        for (int tier = 1; tier <= BANNER_COUNT; tier++) {
            if (!isOverBanner(tier, event.x(), event.y())) continue;
            draggingBanner = tier;
            selectedBanner = tier;
            refreshColourReset();
            grabX = (int) event.x() - (bannerLeft(tier) + bannerWidth(tier) / 2);
            grabY = (int) event.y() - (bannerTop(tier) + bannerHeight(tier) / 2);
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
        if (draggingBanner > 0) {
            // Der Rahmen haengt an seiner Mitte, so wie das Banner spaeter auch
            float x = (float) (event.x() - grabX) / width;
            float y = (float) (event.y() - grabY) / height;
            banners().setBannerX(draggingBanner, Math.clamp(x, 0f, 1f));
            banners().setBannerY(draggingBanner, Math.clamp(y, 0f, 1f));
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
        draggingBanner = 0;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int tier = 1; tier <= BANNER_COUNT; tier++) {
            if (!isOverBanner(tier, mouseX, mouseY)) continue;
            float scale = banners().bannerScale(tier) + (float) scrollY * 0.1f;
            banners().setBannerScale(tier, Math.clamp(scale, 0.5f, 3.0f));
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
     * Kein Abdunkeln, kein Weichzeichner.
     *
     * Beides wuerde hinter die halbdurchsichtigen Kaesten geraten und sie blasser
     * wirken lassen, als sie im Spiel aussehen - man wuerde also etwas einrichten,
     * das man so nie zu sehen bekommt.
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

        for (int tier = 1; tier <= BANNER_COUNT; tier++) {
            int left = bannerLeft(tier);
            int top = bannerTop(tier);
            int right = left + bannerWidth(tier);
            int bottom = top + bannerHeight(tier);
            int colour = 0xFF000000 | bannerColour(tier);

            graphics.fill(left, top, right, bottom, 0xA0101010);
            int edge = tier == selectedBanner ? 0xFFFFAA00 : isOverBanner(tier, mouseX, mouseY) ? 0xFFFFFFFF : colour;
            frame(graphics, left, top, right, bottom, edge);
            // Der Farbbalken links zeigt die Farbe auch dann, wenn der Rahmen gerade gelb ist
            graphics.fill(left, top, left + 3, bottom, colour);

            String label = "Tier " + tier + " banner";
            graphics.centeredText(font, label, (left + right) / 2, (top + bottom) / 2 - 4, 0xFFFFFFFF);
        }

        graphics.centeredText(font, this.title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(
                        "Drag to move  ·  scroll to resize  ·  banners: styles 6 and up follow the frame")
                .withStyle(ChatFormatting.GRAY), width / 2, 26, 0xFFAAAAAA);

        for (int tier = 1; tier <= BANNER_COUNT; tier++) {
            if (!isOverBanner(tier, mouseX, mouseY)) continue;
            graphics.centeredText(font, Component.literal(String.format("tier %d banner   size %.0f%%   style %s",
                            tier, banners().bannerScale(tier) * 100, banners().tier(tier).style()))
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

    /**
     * Regler fuer die Deckkraft des ausgewaehlten Kastens.
     *
     * Ein Regler zeigt seinen Wert von selbst an und laesst sich mit der Maus finden.
     */
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
