package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.DropBanner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Der Banner-Sandbox: ein Design auswaehlen oder anlegen und jede Eigenschaft drehen.
 *
 * Links die Liste der Designs - die einundzwanzig Vorlagen und alles, was man selbst
 * gebaut hat. Rechts die Eigenschaften des ausgewaehlten Designs: Verankerung,
 * Hintergrund, Rahmen, Akzent, Schrift, Vor- und Nachsatz, Bild, Animation, Dauer,
 * Farbe. "Preview" zeigt es sofort hinter dem Fenster; "Use for Tier" haengt es an
 * eine Stufe. Ort und Groesse zieht man im HUD-Editor, hier gibt es nur den Regler.
 *
 * Kein Abdunkeln des Hintergrunds, damit die Vorschau so aussieht wie im Spiel.
 */
public class BannerDesignScreen extends Screen {

    /**
     * Woran ein geteilter Code zu erkennen ist.
     *
     * Die Eins ist die Fassung des Formats. Kommt spaeter eine zweite, laesst sich
     * am Praefix ablesen, was vorliegt - statt an einem Fehler zu scheitern, den
     * niemand deuten kann.
     */
    private static final String SHARE_PREFIX = "SHOKI1:";
    /**
     * Nur die Felder, die auch gespeichert werden.
     *
     * Ohne diese Einschraenkung wanderten auch die fluechtigen Felder mit, die
     * MoulConfig fuer Knoepfe braucht - und ein Code traege Dinge, die auf einem
     * anderen Rechner nichts bedeuten.
     */
    private static final com.google.gson.Gson SHARE_GSON = new com.google.gson.GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private static final int ROW = 22;
    private static final int LIST_LEFT = 8;
    private static final int LIST_WIDTH = 150;
    private static final int LIST_TOP = 30;
    private static final int COLUMN_WIDTH = 196;
    private static final int COLUMN_GAP = 8;

    private final Screen parent;
    private int selected = 0;
    private int listPage = 0;

    public BannerDesignScreen(Screen parent) {
        super(Component.literal("Banner sandbox"));
        this.parent = parent;
    }

    private static ModConfig.BannerCategory banner() {
        return ModConfig.INSTANCE.chat.banner;
    }

    private List<BannerDesign> designs() {
        return banner().designs;
    }

    private BannerDesign design() {
        List<BannerDesign> all = designs();
        if (all.isEmpty()) all.addAll(BannerDesign.presets());
        selected = Math.clamp(selected, 0, all.size() - 1);
        return all.get(selected);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        BannerDesign d = design();

        // ---- Liste links ----
        int rowsPerPage = Math.max(1, (height - LIST_TOP - 90) / ROW);
        int pages = Math.max(1, (designs().size() + rowsPerPage - 1) / rowsPerPage);
        listPage = Math.clamp(listPage, 0, pages - 1);
        int start = listPage * rowsPerPage;
        for (int i = 0; i < rowsPerPage && start + i < designs().size(); i++) {
            int index = start + i;
            BannerDesign entry = designs().get(index);
            String tiers = HudEditorScreen.tiersUsing(entry.name);
            String rowText = (index == selected ? "▶ " : "") + entry.name + (tiers.isEmpty() ? "" : "  [" + tiers.replace("Tier ", "T") + "]");
            Button row = Button.builder(Component.literal(rowText), button -> {
                selected = index;
                rebuild();
            }).bounds(LIST_LEFT, LIST_TOP + i * ROW, LIST_WIDTH, 20).build();
            addRenderableWidget(row);
        }
        int listBottom = LIST_TOP + rowsPerPage * ROW + 4;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                listPage = Math.floorMod(listPage - 1, pages);
                rebuild();
            }).bounds(LIST_LEFT, listBottom, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                listPage = Math.floorMod(listPage + 1, pages);
                rebuild();
            }).bounds(LIST_LEFT + LIST_WIDTH - 20, listBottom, 20, 20).build());
        }
        int actionTop = listBottom + 24;
        addRenderableWidget(Button.builder(Component.literal("New"), button -> {
            BannerDesign fresh = new BannerDesign();
            fresh.name = uniqueName("New banner");
            designs().add(fresh);
            selected = designs().size() - 1;
            rebuild();
        }).bounds(LIST_LEFT, actionTop, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy"), button -> {
            BannerDesign copy = d.copy();
            copy.name = uniqueName(d.name + " copy");
            designs().add(selected + 1, copy);
            selected = selected + 1;
            rebuild();
        }).bounds(LIST_LEFT + 51, actionTop, 48, 20).build());
        Button delete = Button.builder(Component.literal("Delete").withStyle(ChatFormatting.RED), button -> {
            if (designs().size() <= 1) return;
            designs().remove(selected);
            selected = Math.max(0, selected - 1);
            rebuild();
        }).bounds(LIST_LEFT + 102, actionTop, 48, 20).build();
        delete.active = designs().size() > 1;
        delete.setTooltip(Tooltip.create(Component.literal("Removes this design. Tiers that used it fall back to the first one.")));
        addRenderableWidget(delete);

        // Teilen: Ein Design ist knapp vierhundert Zeichen und passt damit in jede
        // Nachricht. Der Weg fuehrt ueber die Zwischenablage, weil der von jedem
        // Fenster aus funktioniert - Discord, Chat, Notizzettel
        Button teilen = Button.builder(Component.literal("Share")
                        .withStyle(ChatFormatting.AQUA), button -> shareDesign(d))
                .bounds(LIST_LEFT, actionTop + 23, 74, 20).build();
        teilen.setTooltip(Tooltip.create(Component.literal(
                "Copies this design as a code you can paste anywhere.")));
        addRenderableWidget(teilen);

        Button holen = Button.builder(Component.literal("Import")
                        .withStyle(ChatFormatting.GREEN), button -> importDesign())
                .bounds(LIST_LEFT + 77, actionTop + 23, 73, 20).build();
        holen.setTooltip(Tooltip.create(Component.literal(
                "Reads a shared code from your clipboard and adds it as a new design.")));
        addRenderableWidget(holen);

        // ---- Eigenschaften rechts, zwei Spalten ----
        int col1 = LIST_LEFT + LIST_WIDTH + 16;
        int col2 = col1 + COLUMN_WIDTH + COLUMN_GAP;
        int y1 = LIST_TOP;
        int y2 = LIST_TOP;

        EditBox name = box(col1, y1, "Name", d.name, 40, value -> d.name = value.isBlank() ? d.name : value);
        y1 += ROW;
        cycle(col1, y1, "Anchor", BannerDesign.Anchor.values(), () -> d.anchor, v -> d.anchor = v, a -> a.label);
        y1 += ROW;
        cycle(col1, y1, "Background", BannerDesign.Background.values(), () -> d.background, v -> d.background = v, b -> b.label);
        y1 += ROW;
        slider(col1, y1, "Background alpha", 0f, 1f, () -> d.backgroundAlpha, v -> d.backgroundAlpha = v, "%.0f%%", 100f);
        y1 += ROW;
        cycle(col1, y1, "Frame", BannerDesign.Frame.values(), () -> d.frame, v -> d.frame = v, f -> f.label);
        y1 += ROW;
        cycle(col1, y1, "Accent", BannerDesign.Accent.values(), () -> d.accent, v -> d.accent = v, a -> a.label);
        y1 += ROW;
        cycle(col1, y1, "Icon", BannerDesign.Icon.values(), () -> d.icon, v -> d.icon = v, i -> i.label);
        y1 += ROW;
        slider(col1, y1, "Icon size", 0.5f, 4f, () -> d.iconScale, v -> d.iconScale = v, "%.1fx", 1f);
        y1 += ROW;
        cycle(col1, y1, "Animation", BannerDesign.Animation.values(), () -> d.animation, v -> d.animation = v, a -> a.label);
        y1 += ROW;
        // Nur zeigen, wenn eine Kiste aufspringt - sonst stuende hier eine Wahl
        // ohne Wirkung, und man suchte spaeter, warum sie nichts tut
        if (d.animation == BannerDesign.Animation.CHEST) {
            cycle(col1, y1, "Chest", BannerDesign.Chest.values(), () -> d.chest,
                    v -> d.chest = v, c -> c.label);
            y1 += ROW;
        }
        slider(col1, y1, "Duration", 1f, 10f, () -> d.durationMillis / 1000f, v -> d.durationMillis = Math.round(v * 1000f), "%.1fs", 1f);
        y1 += ROW;
        slider(col1, y1, "Overall size", 0.3f, 3f, () -> d.scale, v -> d.scale = v, "%.0f%%", 100f);

        slider(col2, y2, "Headline size", 0.5f, 5f, () -> d.headlineSize, v -> d.headlineSize = v, "%.1fx", 1f);
        y2 += ROW;
        slider(col2, y2, "Value size", 0.5f, 5f, () -> d.valueSize, v -> d.valueSize = v, "%.1fx", 1f);
        y2 += ROW;
        cycle(col2, y2, "Headline colour", BannerDesign.TextColour.values(), () -> d.headlineColour, v -> d.headlineColour = v, c -> c.label);
        y2 += ROW;
        cycle(col2, y2, "Value colour", BannerDesign.TextColour.values(), () -> d.valueColour, v -> d.valueColour = v, c -> c.label);
        y2 += ROW;
        cycle(col2, y2, "Text effect", BannerDesign.TextEffect.values(), () -> d.textEffect, v -> d.textEffect = v, e -> e.label);
        y2 += ROW;
        box(col2, y2, "Prefix", d.prefix, 16, value -> d.prefix = value);
        y2 += ROW;
        box(col2, y2, "Suffix", d.suffix, 16, value -> d.suffix = value);
        y2 += ROW;
        toggle(col2, y2, "Show value", () -> d.showValue, v -> d.showValue = v);
        y2 += ROW;
        // Was um den Wert steht. Leer heisst: nackte Zahl - die Klammern standen
        // frueher fest im Code und liessen sich nicht abwaehlen
        box(col2, y2, "Value left", d.valuePrefix, 4, value -> d.valuePrefix = value);
        y2 += ROW;
        box(col2, y2, "Value right", d.valueSuffix, 4, value -> d.valueSuffix = value);
        y2 += ROW;
        toggle(col2, y2, "Show tier", () -> d.showTier, v -> d.showTier = v);
        y2 += ROW;
        EditBox colour = box(col2, y2, "Colour hex (empty = tier)", d.colour, 7, value -> d.colour = value.trim());
        colour.setTooltip(Tooltip.create(Component.literal("Six hex digits like FFD700. Empty: green, gold or purple by tier.")));
        y2 += ROW;
        Button position = Button.builder(Component.literal("Position: HUD editor"), button -> {
            if (minecraft != null) minecraft.setScreen(new HudEditorScreen(this, true));
        }).bounds(col2, y2, COLUMN_WIDTH, 20).build();
        position.setTooltip(Tooltip.create(Component.literal("Drag this banner into place. Only the Free anchor follows X/Y.")));
        addRenderableWidget(position);

        // ---- Unten ----
        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Preview"), button -> DropBanner.preview(d))
                .bounds(col1, bottom, 80, 20).build());
        for (int tier = 1; tier <= 3; tier++) {
            int number = tier;
            Button use = Button.builder(Component.literal(usesTier(d, tier) ? "✔ Tier " + tier : "Tier " + tier), button -> {
                ModConfig.INSTANCE.chat.rareLoot.setDesign(number, d.name);
                rebuild();
            }).bounds(col1 + 84 + (tier - 1) * 58, bottom, 54, 20).build();
            use.setTooltip(Tooltip.create(Component.literal("Use this design when a drop reaches Tier " + tier)));
            addRenderableWidget(use);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(col2 + COLUMN_WIDTH - 80, bottom, 80, 20).build());

        setInitialFocus(name);
    }

    private static boolean usesTier(BannerDesign d, int tier) {
        return d.name.equalsIgnoreCase(ModConfig.INSTANCE.chat.rareLoot.designFor(tier));
    }

    /**
     * Das Design als Code in die Zwischenablage.
     *
     * Zusammengepackt und in Buchstaben umgeschrieben - roh waeren es fast
     * fuenfhundert Zeichen JSON mit Anfuehrungszeichen, die beim Einfuegen in einen
     * Chat zerbrechen. So sind es rund vierhundert harmlose Zeichen.
     *
     * Die Kennung am Anfang sagt, wie der Rest zu lesen ist. Aendert sich das Format
     * einmal, erkennt eine spaetere Fassung am Praefix, was sie vor sich hat, statt
     * an einem unverstaendlichen Fehler zu scheitern.
     */
    private void shareDesign(BannerDesign d) {
        try {
            String json = SHARE_GSON.toJson(d);
            java.io.ByteArrayOutputStream roh = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream zip = new java.util.zip.GZIPOutputStream(roh)) {
                zip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            String code = SHARE_PREFIX + java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(roh.toByteArray());
            minecraft.keyboardHandler.setClipboard(code);
            note("Copied " + d.name + " (" + code.length() + " characters) to your clipboard",
                    ChatFormatting.GREEN);
        } catch (java.io.IOException | RuntimeException e) {
            note("Could not build the code: " + e, ChatFormatting.RED);
        }
    }

    /**
     * Ein geteiltes Design aus der Zwischenablage holen.
     *
     * Angelegt wird immer ein neues - ein Code soll nie still ueberschreiben, woran
     * jemand gerade gearbeitet hat. Traegt der Code einen Namen, den es schon gibt,
     * bekommt er eine Nummer.
     */
    private void importDesign() {
        String zwischenablage = minecraft.keyboardHandler.getClipboard();
        if (zwischenablage == null || zwischenablage.isBlank()) {
            note("Your clipboard is empty", ChatFormatting.RED);
            return;
        }
        String code = zwischenablage.trim();
        if (!code.startsWith(SHARE_PREFIX)) {
            note("That is not a banner code - it should start with " + SHARE_PREFIX,
                    ChatFormatting.RED);
            return;
        }
        try {
            byte[] gepackt = java.util.Base64.getUrlDecoder()
                    .decode(code.substring(SHARE_PREFIX.length()));
            String json;
            try (java.util.zip.GZIPInputStream zip = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(gepackt))) {
                json = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            BannerDesign neu = SHARE_GSON.fromJson(json, BannerDesign.class);
            if (neu == null || neu.name == null || neu.name.isBlank()) {
                note("The code did not contain a usable design", ChatFormatting.RED);
                return;
            }
            neu.name = uniqueName(neu.name);
            designs().add(neu);
            selected = designs().size() - 1;
            note("Added " + neu.name, ChatFormatting.GREEN);
            rebuild();
        } catch (java.io.IOException | RuntimeException e) {
            // IllegalArgumentException - ein schiefer Base64-Text - ist bereits
            // eine RuntimeException und darf daneben nicht noch einmal stehen
            note("Could not read that code - is it complete?", ChatFormatting.RED);
        }
    }

    /** Eine kurze Rueckmeldung im Chat, damit man weiss, ob es geklappt hat */
    private void note(String text, ChatFormatting colour) {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.player.sendSystemMessage(
                Component.literal("[ShokiMod] " + text).withStyle(colour));
    }

    private String uniqueName(String base) {
        String candidate = base;
        int n = 2;
        while (banner().design(candidate) != null) candidate = base + " " + n++;
        return candidate;
    }

    // ---- Widget-Bauer ----

    private EditBox box(int x, int y, String label, String value, int maxLength, Consumer<String> apply) {
        EditBox field = new EditBox(font, x + 90, y, COLUMN_WIDTH - 90, 20, Component.literal(label));
        field.setMaxLength(maxLength);
        field.setValue(value == null ? "" : value);
        field.setHint(Component.literal(label).withStyle(ChatFormatting.DARK_GRAY));
        field.setResponder(typed -> {
            apply.accept(typed);
            DropBanner.preview(design());
        });
        addRenderableWidget(field);
        labels.add(new Label(x, y + 6, label));
        return field;
    }

    private <T> void cycle(int x, int y, String label, T[] values, Supplier<T> get, Consumer<T> set, Function<T, String> text) {
        Button button = Button.builder(Component.literal(label + ": " + text.apply(get.get())), b -> {
            T current = get.get();
            int index = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == current) index = i;
            int next = (index + 1) % values.length;
            set.accept(values[next]);
            b.setMessage(Component.literal(label + ": " + text.apply(values[next])));
            DropBanner.preview(design());
        }).bounds(x, y, COLUMN_WIDTH, 20).build();
        button.setTooltip(Tooltip.create(Component.literal("Click to step through the options")));
        addRenderableWidget(button);
    }

    private void toggle(int x, int y, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        Button button = Button.builder(toggleText(label, get.get()), b -> {
            boolean next = !get.get();
            set.accept(next);
            b.setMessage(toggleText(label, next));
            DropBanner.preview(design());
        }).bounds(x, y, COLUMN_WIDTH, 20).build();
        addRenderableWidget(button);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": ").append(Component.literal(on ? "ON" : "OFF")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void slider(int x, int y, String label, float min, float max, Supplier<Float> get, Consumer<Float> set,
                        String format, float displayFactor) {
        addRenderableWidget(new ValueSlider(x, y, COLUMN_WIDTH, label, min, max, get, set, format, displayFactor));
    }

    private class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final float min;
        private final float max;
        private final Consumer<Float> set;
        private final String format;
        private final float displayFactor;

        ValueSlider(int x, int y, int width, String label, float min, float max, Supplier<Float> get,
                    Consumer<Float> set, String format, float displayFactor) {
            super(x, y, width, 20, Component.empty(), (Math.clamp(get.get(), min, max) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.set = set;
            this.format = format;
            this.displayFactor = displayFactor;
            updateMessage();
        }

        private float current() {
            return (float) (min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, format, current() * displayFactor)));
        }

        @Override
        protected void applyValue() {
            set.accept(current());
            updateMessage();
            DropBanner.preview(design());
        }
    }

    private record Label(int x, int y, String text) {
    }

    private final List<Label> labels = new java.util.ArrayList<>();

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        labels.clear();
    }

    /** Kein Abdunkeln: die Vorschau soll so aussehen wie im Spiel */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Ein dunkler Grund hinter Liste und Reglern, damit die Schrift lesbar bleibt
        int panelRight = LIST_LEFT + LIST_WIDTH + 16 + COLUMN_WIDTH * 2 + COLUMN_GAP + 8;
        graphics.fill(0, 0, Math.min(width, panelRight), height, 0xB0101010);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, this.title.getString(), LIST_LEFT, 10, 0xFFFFFFFF, true);
        graphics.text(font, "Design: " + design().name, LIST_LEFT + LIST_WIDTH + 16, 10, 0xFFFFAA00, true);
        for (Label label : labels) {
            graphics.text(font, label.text(), label.x(), label.y(), 0xFFCCCCCC, true);
        }
        // Die Vorschau steht, solange das Fenster offen ist, und zuletzt gezeichnet liegt sie
        // ueber dem Fenster - der HUD-Durchgang laesst sie hier aus
        DropBanner.keepPreview(design());
        DropBanner.render(graphics);
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
