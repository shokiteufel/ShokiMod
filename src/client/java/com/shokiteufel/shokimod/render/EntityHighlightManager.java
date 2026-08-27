package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.CustomMob;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.MobVisual;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.SparklingTarget;
import com.shokiteufel.shokimod.util.CustomMobDebug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sucht jeden Tick die Mobs heraus, die markiert werden sollen, und legt das Ergebnis
 * für die drei Anzeigen ab: Glow (über die Mixins), Tracer und Namensschild.
 *
 * Gesucht wird zweierlei: selbst hinzugefügte Mobs - nach Name oder nach Entity-Typ -
 * und seltene Critter am Präfix "Sparkling".
 */
public class EntityHighlightManager {

    /** Grenze zwischen den vier Safari-Biomen. Sie liegen kreuzförmig um diesen Punkt */
    private static final double SAFARI_CENTER_X = -50.0;
    private static final double SAFARI_CENTER_Z = 0.0;

    // Display(アイテム/ブロック表示)の表示位置。
    // Display は原点がそのまま見た目の中心になるため、足元からの補正は要らない
    private static final double DISPLAY_ANCHOR = 0.0;
    // ネームタグから本体を探す半径(ブロック)
    private static final double NAMETAG_SEARCH_RADIUS = 4.0;
    // skull を被せたアーマースタンドは、足元ではなく頭の位置に見た目がある。
    // 表示をそこへ合わせるための、足元からの高さ(ブロック)
    private static final double HEAD_STAND_ANCHOR = 1.7;
    private static final double SMALL_HEAD_STAND_ANCHOR = 0.85;

    /** Alles, was gerade leuchten soll. Die Mixins fragen genau diese Menge ab */
    public static final Set<Entity> highlightedEntities = new HashSet<>();

    /** Leuchtfarbe je Mob. Gleiche Art kann je Eintrag eine andere Farbe haben */
    public static final Map<Entity, Integer> customGlowColors = new HashMap<>();

    /** Namensschild-Text je Mob */
    public static final Map<Entity, String> nameplateEntities = new LinkedHashMap<>();

    /** Tracer-Ziele mit ihrer Linienfarbe */
    public static final Map<Entity, Integer> tracerEntities = new LinkedHashMap<>();

    /** Aufhängehöhe, wenn die Trefferbox nicht zum Aussehen passt */
    public static final Map<Entity, Double> renderAnchors = new HashMap<>();

    /**
     * Armor Stands, deren Umriss auf den Kopf begrenzt werden soll.
     * Ohne das umreißt der Glow auch Arme, Rumpf und Beine, die gar nicht zu sehen sind.
     */
    public static final Set<Entity> headOnlyGlowEntities = new HashSet<>();

    // Je Art nur die nächste Linie, sofern nicht "All" eingestellt ist
    private static final Map<Object, Entity> tracerNearest = new HashMap<>();

    /**
     * Sichtbare Mobs werden jeden Tick neu bestimmt.
     * Armor Stands und Displays lassen sich nicht am Typ erkennen, deshalb diese Merkliste.
     */
    private static final Set<Entity> rebuiltVisuals = new HashSet<>();

    /** Damit ein Mob nicht zusätzlich über seinen Typ ein zweites Mal erfasst wird */
    private static final Set<Entity> nametagClaimedEntities = new HashSet<>();

    private static int debugNamedCount = 0;
    private static int debugMatchCount = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EntityHighlightManager::updateHighlights);
    }

    private static void updateHighlights(Minecraft client) {
        customGlowColors.clear();
        nameplateEntities.clear();
        // 型で消せない見た目エンティティは毎 tick 作り直す
        highlightedEntities.removeAll(rebuiltVisuals);
        rebuiltVisuals.clear();
        nametagClaimedEntities.clear();
        renderAnchors.clear();
        headOnlyGlowEntities.clear();
        tracerEntities.clear();
        tracerNearest.clear();

        if (client.level == null || client.player == null) {
            highlightedEntities.clear();
            return;
        }

        if (CustomMobDebug.enabled()) {
            List<CustomMob> customs = ModConfig.INSTANCE.mobVisuals.customTargets;
            CustomMobDebug.summary(customs.size(), debugNamedCount, debugMatchCount,
                    highlightedEntities.size(), ModConfig.INSTANCE.mobVisuals.enableHighlight);
            for (CustomMob c : customs) {
                CustomMobDebug.rule(c.pattern, c.enabled, c.anyEnabled(), c.highlight(),
                        customs.contains(c));
            }
        }
        debugNamedCount = 0;
        debugMatchCount = 0;

        boolean scanCustom = ModConfig.INSTANCE.mobVisuals.customTargets.stream()
                .anyMatch(m -> m.isUsable() && m.anyEnabled());
        // 型ベースの規則は名前を持たないモブが対象なので、名前フィルタの外で回す必要がある
        boolean scanCustomType = ModConfig.INSTANCE.mobVisuals.customTargets.stream()
                .anyMatch(m -> !m.isNameMode() && m.isUsable() && m.anyEnabled());
        // レア個体は接頭辞つきの名前を持つので、名前ループの中で拾える
        boolean scanSparkling = SparklingTarget.INSTANCE.anyEnabled();

        if (!scanCustom && !scanSparkling) return;

        if (scanCustomType) {
            for (Entity entity : client.level.entitiesForRendering()) {
                for (CustomMob custom : ModConfig.INSTANCE.mobVisuals.customTargets) {
                    if (custom.isNameMode() || !custom.isUsable() || !custom.anyEnabled()) continue;
                    if (!custom.matchesType(entity)) continue;

                    // 透明な個体は当たり判定。見た目を担うスタンドや Display に付け替える
                    Entity visual = entity;
                    if (entity.isInvisible()) {
                        Entity companion = nametagVisual(client, entity);
                        if (companion != null) visual = companion;
                    }
                    CustomMobDebug.typeMatched(custom.label(), entity, visual);

                    // 透明なものを光らせても何も見えないので、その場合だけ Glow を見送る
                    if (custom.highlight() && !visual.isInvisible()) {
                        registerHighlight(visual, custom);
                        CustomMobDebug.registered(custom.label(), visual, custom.glowColorRGB());
                    }
                    registerTracer(visual, custom);
                    if (custom.nameplate()) {
                        nameplateEntities.put(visual, nameplateLabel(custom, custom.plainLabel()));
                    }
                    break;
                }
            }
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            Component customName = entity.getCustomName();
            if (customName == null) continue;
            String nameStr = customName.getString();
            debugNamedCount++;

            // Sparkling(レア個体)。接頭辞と、その後ろの種名の両方で判定する
            if (scanSparkling && SparklingTarget.isSparkling(nameStr)) {
                SparklingTarget target = SparklingTarget.INSTANCE;
                Entity visualTarget = customVisual(client, entity);
                Entity visual = visualTarget != null ? visualTarget : entity;
                if (visualTarget != null) {
                    nametagClaimedEntities.add(visualTarget);
                    if (target.highlight()) registerHighlight(visualTarget, target);
                }
                registerTracer(visual, target);
                if (target.nameplate()) {
                    nameplateEntities.put(visual,
                            nameplateLabel(target, SparklingTarget.displayName(nameStr)));
                }
                // Einmalige Einblendung. Die Entprellung steckt in ShinyAlert
                ShinyAlert.onSighting(entity, SparklingTarget.displayName(nameStr), visualTarget);
            }

            // ユーザーが Customize で追加したモブ。エリアを限定しないので毎回見る。
            // 体力表示が変わり続けるため、判定は保存時に整えた名前の部分一致で行う
            for (CustomMob custom : ModConfig.INSTANCE.mobVisuals.customTargets) {
                if (!custom.isUsable()) continue;
                if (!custom.anyEnabled()) continue;
                if (!custom.isNameMode()) continue;
                if (!custom.matches(nameStr)) continue;
                debugMatchCount++;
                CustomMobDebug.matched(CustomMob.normalize(custom.pattern), CustomMob.normalize(nameStr), entity);

                // Hypixel はネームタグを別のアーマースタンドで持つので、その下の本体を探す
                Entity visualTarget = customVisual(client, entity);
                CustomMobDebug.resolved(custom.pattern, entity, visualTarget);
                // 本体が見つからない場合はネームタグ自体を対象にして、
                // 座標さえあれば描ける Tracer とネームプレートは出す
                Entity visual = visualTarget != null ? visualTarget : entity;
                if (visualTarget != null) {
                    nametagClaimedEntities.add(visualTarget);
                    if (custom.highlight()) {
                        registerHighlight(visualTarget, custom);
                        CustomMobDebug.registered(custom.pattern, visualTarget, custom.glowColorRGB());
                    }
                }
                registerTracer(visual, custom);
                if (custom.nameplate()) {
                    nameplateEntities.put(visual, nameplateLabel(custom, custom.plainLabel()));
                }
                break;
            }
        }
    }

    private static String nameplateLabel(MobVisual target, String text) {
        String label = BossNameplateRenderer.colorCode(target.tracerColorARGB()) + "§l" + text;
        return BossNameplateRenderer.buildLabel(label, null);
    }

    private static void registerHighlight(Entity visual, MobVisual target) {
        // marker でないアーマースタンドは、そのままだと腕・胴・脚の輪郭まで出てしまう。
        // 描画時だけ marker として扱わせ、ヘッドだけの輪郭にする
        if (visual instanceof ArmorStand stand && !stand.isMarker()) headOnlyGlowEntities.add(visual);

        highlightedEntities.add(visual);
        // アーマースタンドや Display は型による一括削除ができないので、毎 tick 作り直す集合へ入れる
        rebuiltVisuals.add(visual);
        // 同じ型でも呼び名ごとに色が変わるため、mixin から引けるよう控えておく
        customGlowColors.put(visual, target.glowColorRGB());
    }

    private static void registerTracer(Entity entity, MobVisual target) {
        if (target.tracer()) addTracer(entity, target, target.tracerColorARGB());
    }

    // Tracer の対象を登録する。既定では線が乱立しないよう、同じモブ(key)の中では
    // 自分に最も近い1体だけに絞る。走査順は検出方法ごとにばらばらなので、
    // より近い個体が来たら差し替える形で絞り込む。
    // 設定が All のときは絞り込まず、見つかったモブすべてに線を引く
    private static void addTracer(Entity entity, Object key, int colorARGB) {
        if (entity == null) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (ModConfig.INSTANCE.mobVisuals.tracerMode == ModConfig.MobVisualsCategory.TracerMode.ALL) {
            tracerEntities.put(entity, colorARGB);
            return;
        }

        Entity current = tracerNearest.get(key);
        if (current != null) {
            if (current.distanceToSqr(player) <= entity.distanceToSqr(player)) return;
            // 差し替えるので、前の個体の線は消す
            tracerEntities.remove(current);
        }
        tracerNearest.put(key, entity);
        tracerEntities.put(entity, colorARGB);
    }

    /**
     * ネームプレートと Tracer を合わせる高さ(足元からのブロック数)。
     *
     * 通常は当たり判定の中心でよいが、Hypixel が「skull を被せたアーマースタンド」で
     * 見た目を作っているモブは、当たり判定の中心と見た目が一致しない。
     */
    public static double renderAnchorHeight(Entity entity) {
        Double measured = renderAnchors.get(entity);
        if (measured != null) return measured;

        // Display は当たり判定を持たないので、原点をそのまま表示位置にする
        if (entity instanceof Display) return DISPLAY_ANCHOR;

        if (entity instanceof ArmorStand stand && !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            // marker のアーマースタンドは当たり判定が 0 になるため、当たり判定からは大きさを測れない。
            // 見た目の大きさは scale 属性で決まるので、そちらを基準にする
            double base = stand.isSmall() ? SMALL_HEAD_STAND_ANCHOR : HEAD_STAND_ANCHOR;
            return base * stand.getScale();
        }
        return entity.getBbHeight() / 2.0;
    }

    /**
     * Der Mob, der zu einem Namensschild gehört, sofern Hypixel ihn aus einem eigenen
     * Modell gebaut hat - einem Armor Stand mit Ausrüstung oder einem Display.
     */
    private static Entity nametagVisual(Minecraft client, Entity nameTag) {
        AABB box = nameTag.getBoundingBox().inflate(NAMETAG_SEARCH_RADIUS);

        // どのスロットで見た目を作っているかはモブによって違うので、スロットは限定しない。
        // 当たり判定のモブを光らせると見えないエンティティが光ってしまうため、そちらへは切り替えない
        Entity stand = headStandUnderNameTag(client.level.getEntitiesOfClass(ArmorStand.class, box,
                EntityHighlightManager::hasAnyEquipment), nameTag);
        if (stand != null) return stand;

        Entity display = headStandUnderNameTag(client.level.getEntitiesOfClass(Display.class, box, e -> true), nameTag);
        if (display != null) return display;

        // ネームタグ自身が見た目を兼ねている場合もある
        return nameTag instanceof ArmorStand named && hasAnyEquipment(named) ? nameTag : null;
    }

    /**
     * ユーザー定義モブの本体を探す。
     *
     * nametagVisual は Hypixel が独自モデルで作ったモブ(装備付きアーマースタンド / Display)
     * だけを返す。Graveyard Zombie のように通常のモブでそのまま作られている相手は
     * null になるため、その場合は近くの実体を拾い直す。
     * 当たり判定用の見えない個体を光らせても意味がないので、不可視の個体は除く。
     */
    private static Entity customVisual(Minecraft client, Entity nameTag) {
        Entity modelled = nametagVisual(client, nameTag);
        if (modelled != null) return modelled;

        AABB box = nameTag.getBoundingBox().inflate(NAMETAG_SEARCH_RADIUS);
        List<LivingEntity> mobs = client.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !(e instanceof ArmorStand) && e != client.player && !e.isInvisible());
        return getClosestEntity(mobs, nameTag);
    }

    // アーマースタンドが何かを装備しているか。装備が無いものはネームタグ用の透明なスタンド
    private static boolean hasAnyEquipment(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!stand.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private static Entity headStandUnderNameTag(List<? extends Entity> candidates, Entity nameTag) {
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double dx = candidate.getX() - nameTag.getX();
            double dz = candidate.getZ() - nameTag.getZ();
            double horizontalSqr = dx * dx + dz * dz;
            double dy = Math.abs(nameTag.getY() - candidate.getY());

            // 水平のずれを強く優先しつつ、高さの差も少しだけ見る。
            // 条件で弾かずに一番それらしいものを選ぶことで、
            // ヘッドが少し上にある場合などでも取りこぼさない
            double score = horizontalSqr * 100.0 + dy;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static Entity getClosestEntity(List<? extends Entity> entities, Entity center) {
        Entity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Entity e : entities) {
            double dist = e.distanceToSqr(center);
            if (dist < minDistance) {
                minDistance = dist;
                closest = e;
            }
        }
        return closest;
    }

    /**
     * Hypixels Name für einen Mob, den man sonst nur an der Farbe erkennt.
     *
     * Tropenfische sehen im Auswahlfenster alle gleich aus - Hypixel baut Timil, Ember und
     * Solar aus demselben Fisch und unterscheidet sie nur über die Farbe. Ohne das hier
     * stünde dort dreimal "Tropical Fish".
     */
    public static String describe(Entity entity) {
        if (!(entity instanceof TropicalFish fish)) return null;

        // Safari の Tepid は白一色。体にオレンジが入るものは別のモブなので先に弾く
        if (GameState.Server.isSafari()
                && (fish.getBaseColor() == DyeColor.ORANGE || fish.getPatternColor() == DyeColor.ORANGE)) {
            return null;
        }
        String byBase = fishName(fish.getBaseColor());
        return byBase != null ? byBase : fishName(fish.getPatternColor());
    }

    // 近い色はまとめて扱い、Hypixel 側がどの色を使っていても拾えるようにする。
    // 同じ熱帯魚でもエリアごとに呼び名が違うため、エリアで分岐する
    private static String fishName(DyeColor color) {
        if (color == null) return null;
        if (GameState.Server.isMoongladeMarsh()) {
            return switch (color) {
                case LIGHT_BLUE, CYAN, BLUE -> "Azure";
                case GREEN, LIME -> "Verdant";
                default -> null;
            };
        }
        if (GameState.Server.isSafari()) {
            return switch (color) {
                case BROWN, GRAY, LIGHT_GRAY -> "Cavernfish";
                case WHITE -> "Tepid";
                default -> null;
            };
        }
        return switch (color) {
            case ORANGE -> "Ember";
            case YELLOW -> "Solar";
            case PINK, MAGENTA -> "Timil";
            default -> null;
        };
    }

    /** Liegen Punkt und Standort im selben der vier Biome? */
    public static boolean inSameSafariBiome(BlockPos pos, double originX, double originZ) {
        return (pos.getX() > SAFARI_CENTER_X) == (originX > SAFARI_CENTER_X)
                && (pos.getZ() > SAFARI_CENTER_Z) == (originZ > SAFARI_CENTER_Z);
    }

    // Die vier Safari-Biome liegen kreuzförmig um den Mittelpunkt
    public static boolean inSafariCavern(BlockPos pos) {
        return pos.getX() < SAFARI_CENTER_X && pos.getZ() > SAFARI_CENTER_Z;
    }

    public static boolean inSafariIcy(BlockPos pos) {
        return pos.getX() < SAFARI_CENTER_X && pos.getZ() < SAFARI_CENTER_Z;
    }

    public static boolean inSafariForest(BlockPos pos) {
        return pos.getX() > SAFARI_CENTER_X && pos.getZ() > SAFARI_CENTER_Z;
    }
}
