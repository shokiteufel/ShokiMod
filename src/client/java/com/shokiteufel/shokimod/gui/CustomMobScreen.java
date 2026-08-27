package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.CustomMob;
import com.shokiteufel.shokimod.data.MobVisual;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ユーザー定義モブの一覧と、周囲からの取り込み。
 *
 * 取り込みは2通り。名前のあるモブはネームタグから、名前を持たないモブは
 * エンティティ型から拾う。Critter Safari のモブは大半が後者にあたる。
 */
public class CustomMobScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 384;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LIST_TOP = 62;

    // 表示名と検索条件を別々に持たせるため、1行を4つに割る
    private static final int COLUMN_TOGGLE = 0;
    private static final int COLUMN_LABEL = 36;
    private static final int COLUMN_DEFAULT = 156;
    private static final int COLUMN_MATCH = 190;
    private static final int COLUMN_SWATCH = 324;
    private static final int COLUMN_DELETE = 362;
    private static final int LABEL_WIDTH = 116;
    private static final int MATCH_WIDTH = 130;

    private enum Tab {
        LIST("Your list"),
        NAMED("Named mobs"),
        TYPED("Unnamed mobs");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private final Screen parent;
    private Tab tab;
    private int page;
    private boolean hideNpcs = true;
    private List<Candidate> candidates = new ArrayList<>();

    /** 周囲でまとめた候補。名前モードでは pattern を、型モードでは残りを使う */
    private record Candidate(String label, String pattern, String typeId, boolean invisible,
                             String variant, int color, int count, double distance) {
        boolean isTypeMode() {
            return pattern == null;
        }
    }

    public CustomMobScreen(Screen parent, boolean pickMode) {
        super(Component.literal("Customize"));
        this.parent = parent;
        this.tab = pickMode ? Tab.NAMED : Tab.LIST;
    }

    private List<CustomMob> targets() {
        return ModConfig.INSTANCE.customize.customTargets;
    }

    private double radius() {
        return ModConfig.INSTANCE.customize.pickRadiusBlocks();
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 64 - LIST_TOP) / ROW_HEIGHT);
    }

    private int itemCount() {
        return tab == Tab.LIST ? targets().size() : candidates.size();
    }

    private int pageCount() {
        return Math.max(1, (itemCount() + rowsPerPage() - 1) / rowsPerPage());
    }

    @Override
    protected void init() {
        if (tab != Tab.LIST) {
            scanNearby();
        }
        page = Math.min(page, pageCount() - 1);

        int left = width / 2 - ROW_WIDTH / 2;
        int rows = rowsPerPage();
        int start = page * rows;

        for (int i = 0; i < rows && start + i < itemCount(); i++) {
            int y = LIST_TOP + i * ROW_HEIGHT;
            if (tab == Tab.LIST) {
                addManageRow(targets().get(start + i), left, y);
            } else {
                addPickRow(candidates.get(start + i), left, y);
            }
        }

        buildTabs(left);
        buildFooter(left);
    }

    private void buildTabs(int left) {
        int tabWidth = ROW_WIDTH / 3;
        int x = left;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.literal(t.title), button -> {
                tab = t;
                page = 0;
                rebuild();
            }).bounds(x, 26, tabWidth - 2, WIDGET_HEIGHT).build();
            b.active = tab != t;
            addRenderableWidget(b);
            x += tabWidth;
        }
    }

    private void addPickRow(Candidate candidate, int x, int y) {
        boolean already = isKnown(candidate);
        String suffix = (candidate.count() > 1 ? "  §7×" + candidate.count() : "")
                + "  §8" + Math.round(candidate.distance()) + "m"
                + (already ? "  §2✔" : "");

        Button add = Button.builder(Component.literal(candidate.label() + suffix), button -> {
            addCandidate(candidate);
            rebuild();
        }).bounds(x, y, ROW_WIDTH, WIDGET_HEIGHT).build();
        add.active = !already;
        add.setTooltip(Tooltip.create(Component.literal(already
                ? "Already in your list"
                : candidate.isTypeMode()
                        ? "Matches by entity type" + (candidate.variant().isEmpty()
                                ? "" : " and variant " + candidate.variant())
                        : "Matches by name: \"" + candidate.pattern() + "\"")));
        addRenderableWidget(add);
    }

    private void addManageRow(CustomMob mob, int x, int y) {
        addRenderableWidget(Button.builder(onOff(mob.enabled), button -> {
            mob.enabled = !mob.enabled;
            button.setMessage(onOff(mob.enabled));
        }).bounds(x + COLUMN_TOGGLE, y, 36, WIDGET_HEIGHT).build());

        // 表示名。ネームプレートにもこの文字が出る。検索条件とは独立
        EditBox labelBox = new EditBox(font, x + COLUMN_LABEL, y, LABEL_WIDTH, WIDGET_HEIGHT,
                Component.literal("Display name"));
        labelBox.setMaxLength(48);
        labelBox.setValue(mob.label == null ? "" : mob.label);
        labelBox.setHint(Component.literal("shown name"));
        labelBox.setResponder(value -> mob.label = value);
        addRenderableWidget(labelBox);

        // Zuruecksetzen auf den Namen, den ShokiMod beim Uebernehmen geliefert hat
        Button reset = Button.builder(Component.literal("Def"), button -> {
            mob.label = mob.fallbackLabel();
            rebuild();
        }).bounds(x + COLUMN_DEFAULT, y, 30, WIDGET_HEIGHT).build();
        reset.setTooltip(Tooltip.create(Component.literal(
                "Reset the shown name to \"" + mob.fallbackLabel() + "\"")));
        reset.active = !mob.fallbackLabel().equals(mob.label);
        addRenderableWidget(reset);

        if (mob.isNameMode()) {
            // 検索条件。ここを変えても表示名は変わらない
            EditBox matchBox = new EditBox(font, x + COLUMN_MATCH, y, MATCH_WIDTH, WIDGET_HEIGHT,
                    Component.literal("Match"));
            matchBox.setMaxLength(64);
            matchBox.setValue(mob.pattern);
            matchBox.setHint(Component.literal("part of the name"));
            matchBox.setResponder(value -> mob.pattern = value);
            addRenderableWidget(matchBox);
        } else {
            // 型ベースは編集させず、何に当たるかだけ見せる
            Button info = Button.builder(
                    Component.literal("§7" + shortTypeName(mob)), button -> {
                    }).bounds(x + COLUMN_MATCH, y, MATCH_WIDTH, WIDGET_HEIGHT).build();
            info.active = false;
            info.setTooltip(Tooltip.create(Component.literal(
                    "Entity type: " + mob.typeId
                            + (mob.invisibleOnly ? "  |  invisible only" : "")
                            + (mob.variant.isEmpty() ? "" : "  |  variant: " + mob.variant))));
            addRenderableWidget(info);
        }

        addRenderableWidget(new ColorSwatchButton(x + COLUMN_SWATCH, y, 36, WIDGET_HEIGHT,
                () -> mob.color, () -> 255, () -> openColorPicker(mob)));

        addRenderableWidget(Button.builder(
                Component.literal("✕").withStyle(ChatFormatting.RED), button -> {
                    targets().remove(mob);
                    rebuild();
                }).bounds(x + COLUMN_DELETE, y, 20, WIDGET_HEIGHT).build());
    }

    /** "entity.minecraft.tropical_fish" -> "tropical_fish (pink)" */
    private static String shortTypeName(CustomMob mob) {
        String id = mob.typeId == null ? "" : mob.typeId;
        int dot = id.lastIndexOf('.');
        String shortId = dot >= 0 ? id.substring(dot + 1) : id;
        if (!mob.variant.isEmpty()) shortId += " (" + mob.variant.toLowerCase() + ")";
        if (mob.invisibleOnly) shortId += " [inv]";
        return shortId;
    }

    private void buildFooter(int left) {
        int y = height - 30;

        if (tab == Tab.LIST) {
            addRenderableWidget(Button.builder(Component.literal("Add empty"), button -> {
                targets().add(CustomMob.byName("", CustomMob.DEFAULT_COLOR));
                rebuild();
            }).bounds(left, y, 100, WIDGET_HEIGHT).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Rescan"), button -> rebuild())
                    .bounds(left, y, 80, WIDGET_HEIGHT).build());

            if (tab == Tab.NAMED) {
                addRenderableWidget(Button.builder(
                        Component.literal(hideNpcs ? "NPCs: hidden" : "NPCs: shown"), button -> {
                            hideNpcs = !hideNpcs;
                            rebuild();
                        }).bounds(left + 84, y, 110, WIDGET_HEIGHT).build());
            }
        }

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + ROW_WIDTH - 132, y, 20, WIDGET_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + ROW_WIDTH - 108, y, 20, WIDGET_HEIGHT).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + ROW_WIDTH - 80, y, 80, WIDGET_HEIGHT).build());
    }

    private void openColorPicker(CustomMob mob) {
        if (minecraft == null) return;
        minecraft.setScreen(new ColorPickerScreen(this, mob.color, 255,
                (rgb, fillAlpha) -> mob.color = rgb));
    }

    private boolean isKnown(Candidate candidate) {
        if (candidate.isTypeMode()) {
            return targets().stream().anyMatch(m -> !m.isNameMode()
                    && candidate.typeId().equals(m.typeId)
                    && candidate.invisible() == m.invisibleOnly
                    && candidate.variant().equalsIgnoreCase(m.variant));
        }
        return targets().stream().anyMatch(m -> m.isNameMode()
                && m.pattern != null && m.pattern.equalsIgnoreCase(candidate.pattern()));
    }

    private void addCandidate(Candidate candidate) {
        if (isKnown(candidate)) return;
        // 一覧用の見出しには色コードが入っているので、保存する表示名からは落とす
        String plainLabel = CustomMob.normalize(candidate.label());
        targets().add(candidate.isTypeMode()
                ? CustomMob.byType(candidate.typeId(), plainLabel, candidate.invisible(),
                        candidate.variant(), candidate.color())
                : CustomMob.byName(candidate.pattern(), candidate.color()));
    }

    /** 周囲を集める。タブによって、名前のあるものと無いものを振り分ける */
    private void scanNearby() {
        candidates = new ArrayList<>();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return;

        // 大きな半径では AABB 検索が空のセクションまで舐めてしまうので、
        // 読み込み済みのエンティティを距離で絞る。こちらは半径に関係なく一定の手間で済む
        double reach = radius();
        double reachSqr = reach * reach;
        Map<String, Candidate> merged = new LinkedHashMap<>();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == minecraft.player) continue;
            double distanceSqr = entity.distanceToSqr(minecraft.player);
            if (distanceSqr > reachSqr) continue;
            double distance = Math.sqrt(distanceSqr);
            boolean named = entity.getCustomName() != null;

            if (tab == Tab.NAMED) {
                if (!named) continue;
                String cleaned = CustomMob.cleanPattern(entity.getCustomName().getString());
                if (cleaned.isEmpty()) continue;
                if (hideNpcs && looksLikeNpc(entity)) continue;
                merge(merged, cleaned, new Candidate(cleaned, cleaned, null, false, "",
                        CustomMob.DEFAULT_COLOR, 1, distance));
            } else {
                // 名前を持つものは名前タブ側の担当。ここでは残りを型でまとめる
                if (named) continue;
                if (entity instanceof Player) continue;
                if (entity instanceof ArmorStand stand && stand.isMarker()) continue;

                String typeId = entity.getType().getDescriptionId();
                String variant = CustomMob.variantOf(entity);
                boolean invisible = entity.isInvisible();

                // ShokiMod kennt viele dieser Mobs bereits beim Namen. Dann uebernehmen wir
                // dessen Bezeichnung und Farbe als Vorgabe, statt "Tropical Fish" anzuzeigen.
                String known = EntityHighlightManager.describe(entity);
                String display = known != null
                        ? known
                        : entity.getType().getDescription().getString()
                                + (variant.isEmpty() ? "" : " §7(" + variant.toLowerCase() + ")")
                                + (invisible ? " §8[invisible]" : "");
                int color = CustomMob.DEFAULT_COLOR;
                String key = typeId + "|" + invisible + "|" + variant;
                merge(merged, key, new Candidate(display, null, typeId, invisible, variant,
                        color, 1, distance));
            }
        }

        candidates = new ArrayList<>(merged.values());
        candidates.sort((a, b) -> Double.compare(a.distance(), b.distance()));
    }

    private static void merge(Map<String, Candidate> merged, String key, Candidate fresh) {
        Candidate previous = merged.get(key);
        merged.put(key, previous == null ? fresh
                : new Candidate(previous.label(), previous.pattern(), previous.typeId(),
                        previous.invisible(), previous.variant(), previous.color(),
                        previous.count() + 1, Math.min(previous.distance(), fresh.distance())));
    }

    /**
     * NPC と Schilder を grob aussortieren.
     * Hypixel の NPC はプレイヤー実体、案内板は本体を持たないアーマースタンドなので、
     * 近くに普通のモブがいるかどうかで見分ける。
     */
    private boolean looksLikeNpc(Entity nameTag) {
        if (nameTag instanceof Player) return true;
        AABB box = nameTag.getBoundingBox().inflate(4.0);
        List<LivingEntity> mobs = minecraft.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !(e instanceof ArmorStand) && e != minecraft.player && !(e instanceof Player));
        return mobs.isEmpty();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static Component onOff(boolean on) {
        return on ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.DARK_GRAY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        graphics.centeredText(font, this.title, centerX, 10, 0xFFFFFFFF);

        String hint = switch (tab) {
            case LIST -> "Applies to highlight, tracer and nameplate – anywhere, not just one area.";
            case NAMED -> "Named mobs within " + (int) radius() + " blocks. Click to keep one.";
            case TYPED -> "Mobs without a name, grouped by entity type. Click to keep one.";
        };
        graphics.centeredText(font, Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY),
                centerX, LIST_TOP - 24, 0xFF808080);

        // Spaltenueberschriften, damit klar ist welches Feld was tut
        if (tab == Tab.LIST && !targets().isEmpty()) {
            int left = centerX - ROW_WIDTH / 2;
            graphics.text(font, Component.literal("Shown name").withStyle(ChatFormatting.DARK_GRAY),
                    left + COLUMN_LABEL, LIST_TOP - 11, 0xFF808080);
            graphics.text(font, Component.literal("Matches").withStyle(ChatFormatting.DARK_GRAY),
                    left + COLUMN_MATCH, LIST_TOP - 11, 0xFF808080);
            graphics.text(font, Component.literal("Color").withStyle(ChatFormatting.DARK_GRAY),
                    left + COLUMN_SWATCH, LIST_TOP - 11, 0xFF808080);
        }

        if (itemCount() == 0) {
            String message = switch (tab) {
                case LIST -> "No entries yet – use the two tabs above.";
                case NAMED -> "Nothing named nearby.";
                case TYPED -> "Nothing unnamed nearby.";
            };
            graphics.centeredText(font, Component.literal(message).withStyle(ChatFormatting.GRAY),
                    centerX, LIST_TOP + 12, 0xFFAAAAAA);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font,
                    Component.literal((page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY),
                    centerX, height - 46, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        targets().removeIf(m -> (m.pattern == null || m.pattern.isBlank())
                && (m.typeId == null || m.typeId.isEmpty()));
        ModConfig.INSTANCE.saveNow();
    }
}
