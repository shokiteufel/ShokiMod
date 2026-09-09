package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.BannerDesign;
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
import java.util.List;
import java.util.Map;

/**
 * Kaesten oder Fund-Banner an die gewuenschte Stelle ziehen - eines von beiden.
 *
 * Der Editor kennt zwei Ansichten. In der Kasten-Ansicht liegen nur die Kaesten
 * auf dem Bildschirm; in der Banner-Ansicht nur das eine Design, das unten
 * ausgewaehlt ist. So verdeckt nichts das, woran man gerade arbeitet.
 *
 * Ziehen verschiebt, Scrollen ueber einem Element aendert seine Groesse. Beim
 * Banner gilt: nur die Verankerung "Free" folgt dem Ziehen - alle anderen haben
 * ihren festen Platz und nehmen von hier nur Groesse und Farbe.
 */
public class HudEditorScreen extends Screen {

    private static final int BANNER_BASE_WIDTH = 120;
    private static final int BANNER_BASE_HEIGHT = 26;

    private final Screen parent;
    private final Map<SafariHud.Panel, HudPanel> content = new EnumMap<>(SafariHud.Panel.class);

    /** Wahr: Banner-Ansicht. Falsch: Kasten-Ansicht */
    private boolean bannerMode;

    private SafariHud.Panel draggingPanel = null;
    private boolean draggingBanner = false;
    private int grabX = 0;
    private int grabY = 0;

    private SafariHud.Panel selectedPanel = SafariHud.Panel.PROGRESS;
    private int designIndex = 0;

    private OpacitySlider opacity;
    private Button previousDesign;
    private Button designButton;
    private Button nextDesign;
    private ColorSwatchButton swatch;
    private Button colourReset;
    private Button modeButton;
    private Button showsButton;
    private Button areasButton;

    public HudEditorScreen(Screen parent, boolean bannerMode) {
        super(Component.literal("HUD editor"));
        this.parent = parent;
        this.bannerMode = bannerMode;
    }

    private static List<BannerDesign> designs() {
        List<BannerDesign> all = ModConfig.INSTANCE.chat.banner.designs;
        if (all.isEmpty()) all.addAll(BannerDesign.presets());
        return all;
    }

    private BannerDesign design() {
        List<BannerDesign> all = designs();
        designIndex = Math.clamp(designIndex, 0, all.size() - 1);
        return all.get(designIndex);
    }

    @Override
    protected void init() {
        rebuildContent();

        opacity = new OpacitySlider(width / 2 - 105, height - 78, 210);
        addRenderableWidget(opacity);

        previousDesign = Button.builder(Component.literal("◀"), button -> selectDesign(designIndex - 1))
                .bounds(width / 2 - 105, height - 78, 20, 20).build();
        addRenderableWidget(previousDesign);
        designButton = Button.builder(designLabel(), button -> selectDesign(designIndex + 1))
                .bounds(width / 2 - 83, height - 78, 166, 20).build();
        designButton.setTooltip(Tooltip.create(Component.literal("The design being placed. Build new ones in Alerts > Banner > Sandbox.")));
        addRenderableWidget(designButton);
        nextDesign = Button.builder(Component.literal("▶"), button -> selectDesign(designIndex + 1))
                .bounds(width / 2 + 85, height - 78, 20, 20).build();
        addRenderableWidget(nextDesign);

        showsButton = Button.builder(showsLabel(), button -> {
            ModConfig.INSTANCE.hud.editorShowsAll = !showsAll();
            ModConfig.INSTANCE.saveNow();
            button.setMessage(showsLabel());
            rebuildContent();
        }).bounds(width / 2 - 105, height - 54, 103, 20).build();
        showsButton.setTooltip(Tooltip.create(Component.literal(
                "All panels: place them before switching them on.\nActive only: what is really on screen right now.")));
        addRenderableWidget(showsButton);

        areasButton = Button.builder(areasLabel(), button -> openAreas())
                .bounds(width / 2 + 2, height - 54, 103, 20).build();
        addRenderableWidget(areasButton);

        swatch = new ColorSwatchButton(width / 2 - 105, height - 54, 100, 20,
                this::bannerColour, () -> 255, this::openBannerColour);
        swatch.setTooltip(Tooltip.create(Component.literal("Colour of this design")));
        addRenderableWidget(swatch);

        colourReset = Button.builder(Component.literal("Tier colour"), button -> {
            design().colour = "";
            refreshColourReset();
        }).bounds(width / 2 + 5, height - 54, 100, 20).build();
        colourReset.setTooltip(Tooltip.create(Component.literal("No own colour: the banner takes the colour of the tier that fires it")));
        addRenderableWidget(colourReset);

        modeButton = Button.builder(modeLabel(), button -> {
            bannerMode = !bannerMode;
            draggingPanel = null;
            draggingBanner = false;
            applyMode();
        }).bounds(width / 2 - 105, height - 30, 100, 20).build();
        addRenderableWidget(modeButton);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 5, height - 30, 100, 20).build());

        applyMode();
    }

    private void applyMode() {
        opacity.visible = !bannerMode;
        opacity.active = !bannerMode;
        for (Button button : new Button[]{previousDesign, designButton, nextDesign, colourReset}) {
            button.visible = bannerMode;
            button.active = bannerMode;
        }
        swatch.visible = bannerMode;
        swatch.active = bannerMode;
        showsButton.visible = !bannerMode;
        showsButton.active = !bannerMode;
        areasButton.visible = !bannerMode;
        areasButton.active = !bannerMode;
        refreshAreasButton();
        modeButton.setMessage(modeLabel());
        if (bannerMode) refreshColourReset();
    }

    private Component modeLabel() {
        return Component.literal(bannerMode ? "Edit: Banners" : "Edit: Panels");
    }

    private void selectDesign(int index) {
        designIndex = Math.floorMod(index, designs().size());
        designButton.setMessage(designLabel());
        refreshColourReset();
    }

    private Component designLabel() {
        BannerDesign d = design();
        String tiers = tiersUsing(d.name);
        return Component.literal(tiers.isEmpty() ? d.name : d.name + "  -  " + tiers);
    }

    /** "Tier 1, 3" wenn diese Stufen das Design ziehen, sonst leer - damit man nicht das falsche Design faerbt */
    static String tiersUsing(String name) {
        ModConfig.RareLootCategory rare = ModConfig.INSTANCE.chat.rareLoot;
        StringBuilder out = new StringBuilder();
        for (int tier = 1; tier <= 3; tier++) {
            String used = rare.designFor(tier);
            if (used == null || name == null || !used.trim().equalsIgnoreCase(name.trim())) continue;
            out.append(out.length() == 0 ? "Tier " : ", ").append(tier);
        }
        return out.toString();
    }

    /**
     * Welche Kaesten der Editor zeigt.
     *
     * Alle: auch die abgeschalteten, damit man sie vorher an ihren Platz legen kann.
     * Nur aktive: was gerade wirklich auf dem Bildschirm steht.
     */
    private boolean showsAll() {
        return ModConfig.INSTANCE.hud.editorShowsAll;
    }

    /**
     * Wo der gewaehlte Kasten erscheinen darf.
     *
     * Ohne Auswahl gilt die eingebaute Vorgabe - der Knopf sagt dann "default", damit
     * niemand denkt, der Kasten laufe garantiert ueberall.
     */
    private void openAreas() {
        if (minecraft == null || selectedPanel == null) return;
        java.util.List<String> chosen = ModConfig.INSTANCE.hud.areasFor(selectedPanel.name());
        minecraft.setScreen(new AreaPickerScreen(this, "Areas: " + label(selectedPanel), chosen,
                "Nothing selected - the panel follows its default", ModConfig.INSTANCE::saveNow));
    }

    private Component areasLabel() {
        if (selectedPanel == null) return Component.literal("Areas");
        int n = ModConfig.INSTANCE.hud.areasFor(selectedPanel.name()).size();
        return Component.literal(n == 0 ? "Areas: default" : "Areas: " + n);
    }

    private void refreshAreasButton() {
        if (areasButton == null) return;
        areasButton.setMessage(areasLabel());
        areasButton.setTooltip(Tooltip.create(Component.literal(selectedPanel == null
                ? "Pick a panel first"
                : "Where the panel " + label(selectedPanel) + " may appear. "
                  + "Nothing selected: it keeps its built-in default.")));
    }

    private static String label(SafariHud.Panel panel) {
        String raw = panel.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private Component showsLabel() {
        return Component.literal(showsAll() ? "Show: All panels" : "Show: Active only");
    }

    private void rebuildContent() {
        content.clear();
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!showsAll() && !panel.visible()) continue;
            content.put(panel, panel.build());
        }
        // Der ausgewaehlte Kasten darf nicht auf einen liegen, den man gerade nicht sieht
        if (!content.containsKey(selectedPanel)) {
            selectedPanel = content.isEmpty() ? SafariHud.Panel.PROGRESS : content.keySet().iterator().next();
        }
    }

    private void refreshColourReset() {
        if (colourReset == null) return;
        String own = design().colour;
        colourReset.active = bannerMode && own != null && !own.isBlank();
    }

    private int bannerColour() {
        return DropBanner.parseColour(design().colour, ModConfig.RareLootCategory.defaultTierColour(2));
    }

    private void openBannerColour() {
        if (minecraft == null) return;
        BannerDesign target = design();
        minecraft.setScreen(new ColorPickerScreen(this, bannerColour(), 255,
                (rgb, fillAlpha) -> target.colour = String.format("%06X", rgb & 0xFFFFFF)));
    }

    // ---- Lage des Banner-Rahmens ----

    private boolean freeAnchor() {
        return design().anchor == BannerDesign.Anchor.FREE;
    }

    private int bannerWidth() {
        return (int) (BANNER_BASE_WIDTH * design().scale);
    }

    private int bannerHeight() {
        return (int) (BANNER_BASE_HEIGHT * design().scale);
    }

    /** Freie Designs haengen an X/Y; feste bekommen einen Platz, der ihrer Verankerung entspricht */
    private int bannerCentreX() {
        BannerDesign d = design();
        return switch (d.anchor) {
            case RIGHT_EDGE -> width - bannerWidth() / 2 - 4;
            case FREE -> (int) (d.x * width);
            default -> width / 2;
        };
    }

    private int bannerCentreY() {
        BannerDesign d = design();
        return switch (d.anchor) {
            case TOP -> bannerHeight() / 2 + 4;
            case BAND -> height / 4 + bannerHeight() / 2;
            case HOTBAR -> (int) (height * 0.62f);
            case CENTER -> height / 2;
            case RIGHT_EDGE -> height / 3 + bannerHeight() / 2;
            default -> (int) (d.y * height);
        };
    }

    private int bannerLeft() {
        return bannerCentreX() - bannerWidth() / 2;
    }

    private int bannerTop() {
        return bannerCentreY() - bannerHeight() / 2;
    }

    private boolean isOverBanner(double mouseX, double mouseY) {
        if (!bannerMode) return false;
        int left = bannerLeft();
        int top = bannerTop();
        return mouseX >= left && mouseX <= left + bannerWidth()
                && mouseY >= top && mouseY <= top + bannerHeight();
    }

    private boolean isOver(SafariHud.Panel panel, double mouseX, double mouseY) {
        if (bannerMode) return false;
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
            draggingBanner = freeAnchor();
            grabX = (int) event.x() - bannerCentreX();
            grabY = (int) event.y() - bannerCentreY();
            return true;
        }
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!isOver(panel, event.x(), event.y())) continue;
            draggingPanel = panel;
            selectedPanel = panel;
            refreshAreasButton();
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
            BannerDesign d = design();
            d.x = Math.clamp((float) (event.x() - grabX) / width, 0f, 1f);
            d.y = Math.clamp((float) (event.y() - grabY) / height, 0f, 1f);
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
            BannerDesign d = design();
            d.scale = Math.clamp(d.scale + (float) scrollY * 0.1f, 0.3f, 3.0f);
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

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (bannerMode) {
            drawBanner(graphics, mouseX, mouseY);
        } else {
            drawPanels(graphics, mouseX, mouseY);
        }

        graphics.centeredText(font, Component.literal(bannerMode ? "Drop banners" : "Panels"), width / 2, 12, 0xFFFFFFFF);
        String hint = bannerMode
                ? (freeAnchor() ? "Drag to move  ·  scroll to resize  ·  pick the design below"
                : "Anchor " + design().anchor.label + " has a fixed place  ·  scroll to resize  ·  pick the design below")
                : "Drag to move  ·  scroll to resize  ·  slider below for the background";
        graphics.centeredText(font, Component.literal(hint).withStyle(ChatFormatting.GRAY), width / 2, 26, 0xFFAAAAAA);
    }

    private void drawPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            HudPanel built = content.get(panel);
            if (built == null || built.isEmpty()) continue;

            int left = SafariHud.originX(panel);
            int top = SafariHud.originY(panel);
            int right = left + SafariHud.scaledWidth(panel, built, font);
            int bottom = top + SafariHud.scaledHeight(panel, built);
            int colour = panel == selectedPanel ? 0xFFFFAA00 : isOver(panel, mouseX, mouseY) ? 0xC0FFFFFF : 0x50FFFFFF;
            frame(graphics, left, top, right, bottom, colour);
            SafariHud.draw(graphics, font, panel, built);
        }
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!isOver(panel, mouseX, mouseY)) continue;
            graphics.centeredText(font, Component.literal(String.format("%s   size %.0f%%",
                    panel.name().toLowerCase(), panel.scale() * 100)).withStyle(ChatFormatting.YELLOW), width / 2, 38, 0xFFFFFF55);
            break;
        }
    }

    private void drawBanner(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = bannerLeft();
        int top = bannerTop();
        int right = left + bannerWidth();
        int bottom = top + bannerHeight();
        int colour = 0xFF000000 | bannerColour();

        graphics.fill(left, top, right, bottom, 0xA0101010);
        frame(graphics, left, top, right, bottom, isOverBanner(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFFFAA00);
        graphics.fill(left, top, left + 3, bottom, colour);
        graphics.centeredText(font, design().name, (left + right) / 2, (top + bottom) / 2 - 4, 0xFFFFFFFF);

        if (isOverBanner(mouseX, mouseY)) {
            graphics.centeredText(font, Component.literal(String.format("%s   size %.0f%%", design().name, design().scale * 100))
                    .withStyle(ChatFormatting.YELLOW), width / 2, 38, 0xFFFFFF55);
        }
    }

    private static void frame(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int colour) {
        graphics.fill(left - 1, top - 1, right + 1, top, colour);
        graphics.fill(left - 1, bottom, right + 1, bottom + 1, colour);
        graphics.fill(left - 1, top, left, bottom, colour);
        graphics.fill(right, top, right + 1, bottom, colour);
    }

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
