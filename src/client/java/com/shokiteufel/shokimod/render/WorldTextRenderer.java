package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.FloorDropHandler;
import com.shokiteufel.shokimod.scanner.NestTracker;
import com.shokiteufel.shokimod.scanner.SafariExtras;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/** Kästen und Beschriftungen, die in der Welt stehen statt auf dem Bildschirm. */
public class WorldTextRenderer {

    /**
     * Nur der Rahmen, keine Füllung - wie in crittermod.
     * Eine gefüllte Box verdeckt genau das, was man sehen will.
     */
    private static final float MARKER_LINE_WIDTH = 2.0F;

    public static void render(Minecraft client) {
        if (client.player == null) return;
        renderSafariWalls();
        renderMounds();
        renderFloorDrops();
        renderNests();
        renderMineshaftCorpses(client);
        renderGemstoneVeins(client);
    }

    /**
     * Die Edelstein-Adern des Schachts.
     *
     * Jede Ader bekommt ihren Rahmen und ihren Namen in ihrer eigenen Farbe, die
     * naechste dazu die Entfernung und einen Strich vom Spieler dorthin - die Frage
     * beim Ausminen ist nicht, wo ueberall etwas liegt, sondern wohin man als
     * Naechstes laeuft. Was in der Wand steckt, steht dabei, damit man weiss, dass
     * man graben muss.
     */
    private static void renderGemstoneVeins(Minecraft client) {
        ModConfig.MineshaftCategory cfg = ModConfig.INSTANCE.mining.mineshaft;
        if (cfg.veins == ModConfig.VeinFilter.OFF) return;

        java.util.List<com.shokiteufel.shokimod.scanner.OreVeins.Vein> veins =
                com.shokiteufel.shokimod.scanner.OreVeins.veins();
        boolean first = true;

        for (var vein : veins) {
            if (vein.size() < Math.max(1, cfg.veinMinSize)) continue;
            int argb = vein.kind().argb();

            if (cfg.veinBox) {
                GizmoProperties box = Gizmos.cuboid(vein.box(), GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
                box.setAlwaysOnTop();
            }

            StringBuilder text = new StringBuilder();
            if (first) text.append("-> ");
            text.append(vein.kind().label());
            if (vein.size() > 1) text.append(" x").append(vein.size());
            if (vein.hidden()) text.append(" (in wall)");
            if (first) text.append(" - ").append(Math.round(vein.distance())).append('m');
            renderGizmoLabel(text.toString(), vein.anchor(), argb);

            // Der Strich geht von den Fuessen aus, nicht aus dem Gesicht
            if (first && cfg.veinArrow && client.player != null) {
                GizmoProperties arrow = Gizmos.arrow(client.player.position().add(0, 0.5, 0),
                        vein.box().getCenter(), argb, MARKER_LINE_WIDTH);
                arrow.setAlwaysOnTop();
            }
            first = false;
        }
    }

    /**
     * Die moeglichen Leichen-Stellen im Glacite Mineshaft.
     *
     * Gezeigt wird, wo eine stehen kann - nicht, wo eine steht. Das ist der Sinn der
     * Sache: Der Schacht hat ein festes Muster, und wer die fuenf Stellen abgeht,
     * sucht nicht den ganzen Bau ab.
     */
    private static void renderMineshaftCorpses(Minecraft client) {
        if (!com.shokiteufel.shokimod.scanner.MineshaftState.inMineshaft()) return;

        ModConfig.MineshaftCategory cfg = ModConfig.INSTANCE.mining.mineshaft;
        int argb = 0xFF000000 | cfg.corpseColorRGB();
        double range = Math.max(10, cfg.corpseRange);
        double rangeSquared = range * range;
        Vec3 eye = client.player.position();

        // Was wirklich dasteht, zuerst: dort braucht es keine Vermutung mehr
        java.util.List<com.shokiteufel.shokimod.scanner.CorpseFinder.Corpse> real =
                cfg.corpseLive ? com.shokiteufel.shokimod.scanner.CorpseFinder.visible() : java.util.List.of();
        for (var corpse : real) {
            BlockPos pos = corpse.pos();
            if (eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSquared) {
                continue;
            }
            // Jede Sorte in ihrer Farbe: Lapis blau, Umber gold, Tungsten grau, Vanguard
            // weiss - so sieht man schon von weitem, ob der Weg sich lohnt
            int colour = cfg.corpseKindColour ? corpseColour(corpse.type(), argb) : argb;
            if (cfg.corpseBox) {
                GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(colour, MARKER_LINE_WIDTH));
                box.setAlwaysOnTop();
            }
            // Fehlt der Schluessel, steht es dran, statt vor der Leiche aufzufallen
            String text = corpse.type()
                    + (com.shokiteufel.shokimod.scanner.CorpseKeys.missing(corpse.type()) ? " (no key)" : "");
            renderGizmoLabel(text, pos, colour);
        }

        // Was die Party gemeldet hat, in der Farbe seiner Sorte
        for (var entry : com.shokiteufel.shokimod.scanner.CorpseFinder.reported().entrySet()) {
            BlockPos pos = entry.getKey();
            if (eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSquared) {
                continue;
            }
            // Sieht man sie selbst, gilt der eigene Marker
            if (standsThere(real, pos)) continue;

            int colour = cfg.corpseKindColour ? corpseColour(entry.getValue(), argb) : argb;
            if (cfg.corpseBox) {
                GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(colour, MARKER_LINE_WIDTH));
                box.setAlwaysOnTop();
            }
            renderGizmoLabel(entry.getValue() + " (party)", pos, colour);
        }

        // Sind alle bekannt, tragen die restlichen Stellen keine mehr
        if (cfg.corpseHideWhenDone && com.shokiteufel.shokimod.scanner.CorpseFinder.allFound()) return;

        for (BlockPos pos : com.shokiteufel.shokimod.scanner.MineshaftState.corpses()) {
            if (eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSquared) {
                continue;
            }
            // Steht dort schon eine erkannte Leiche, ist das Wort "Corpse" daneben nur Laerm
            if (standsThere(real, pos)) continue;
            // Wo man schon stand, ist die Frage beantwortet
            if (cfg.corpseHideVisited && com.shokiteufel.shokimod.scanner.CorpseFinder.wasVisited(pos)) continue;

            if (cfg.corpseBox) {
                GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
                box.setAlwaysOnTop();
            }
            renderGizmoLabel("Corpse", pos, argb);
        }
    }

    /**
     * Die Farbe einer Leichensorte.
     *
     * Dieselbe Zuordnung, die auch Hypixel im Chat benutzt und die SkyHanni abliest:
     * Lapis blau, Tungsten grau, Umber gold, Vanguard weiss. Eine unbekannte Sorte
     * behaelt die eingestellte Farbe, statt geraten zu werden.
     */
    private static int corpseColour(String type, int fallback) {
        if (type == null) return fallback;
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "lapis" -> 0xFF5555FF;
            case "tungsten" -> 0xFFAAAAAA;
            case "umber" -> 0xFFFFAA00;
            case "vanguard" -> 0xFFFFFFFF;
            default -> fallback;
        };
    }

    /** Liegt auf dieser Stelle eine Leiche, die gerade zu sehen ist? */
    private static boolean standsThere(
            java.util.List<com.shokiteufel.shokimod.scanner.CorpseFinder.Corpse> real, BlockPos pos) {
        for (int i = 0; i < real.size(); i++) {
            if (real.get(i).pos().distSqr(pos) <= 4) return true;
        }
        return false;
    }

    // Bienenstoecke im Forest. Abgeerntete bleiben stehen, die zeigen wir nicht mehr
    private static void renderNests() {
        if (!NestTracker.isActive()) return;

        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.nestColorRGB();
        for (NestTracker.Nest nest : NestTracker.nests()) {
            if (!nest.unpunched()) continue;
            GizmoProperties box = Gizmos.cuboid(nest.pos(), GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Nest", nest.pos(), argb);
        }
    }

    // 地面に落ちている採取物。ブロックは置かれておらず、見た目は ItemDisplay の重なり
    private static void renderFloorDrops() {
        if (!FloorDropHandler.isActive()) return;

        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.floorDropColorRGB();
        for (BlockPos pos : FloorDropHandler.positions()) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Floor Drop", pos, argb);
        }
    }

    // 壊せる壁。まだ立っているものだけを出す
    private static void renderSafariWalls() {
        if (!SafariExtras.wallsActive()) return;

        Minecraft client = Minecraft.getInstance();
        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.wallColorRGB();
        for (BlockPos pos : SafariExtras.intactWalls(client)) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Wall", pos, argb);
        }
    }

    // Rockmite Mound。当たり判定から見つけるので、テクスチャ判定とは独立して効く
    private static void renderMounds() {
        if (!SafariExtras.moundsActive()) return;

        Minecraft client = Minecraft.getInstance();
        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.moundColorRGB();
        for (BlockPos pos : SafariExtras.mounds(client)) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Mound", pos, argb);
        }
    }

    public static Vec3 labelPos(BlockPos renderPos) {
        return new Vec3(renderPos.getX() + 0.5, renderPos.getY() + 1.5, renderPos.getZ() + 0.5);
    }

    public static void renderGizmoLabel(String text, BlockPos renderPos, int argbColor) {
        Vec3 pos = labelPos(renderPos);

        // 距離に比例して拡大し、見かけの大きさを一定に保つ。
        // プレイヤーのtick座標を使うと20回/秒でしかスケールが更新されずカクつくため、
        // フレームごとに補間されるカメラ座標を基準にする
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        float textScale = (float) Math.max(0.02, cameraPos.distanceTo(pos) * 0.0025);

        TextGizmo.Style style = TextGizmo.Style.forColorAndCentered(argbColor)
                .withScale(textScale * 20.0F);
        GizmoProperties properties = Gizmos.billboardText(text, pos, style);
        properties.setAlwaysOnTop();
    }
}
