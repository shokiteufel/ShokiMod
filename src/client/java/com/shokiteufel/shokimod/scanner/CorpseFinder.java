package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Leichen, die wirklich dastehen - und die Stellen, die niemand aufgeschrieben hat.
 *
 * Eine gefrorene Leiche ist ein Armorstand mit einem bestimmten Helm: Lapis traegt
 * LAPIS_ARMOR_HELMET, Umber ARMOR_OF_YOG_HELMET, Tungsten MINERAL_HELMET, Vanguard
 * VANGUARD_HELMET. Das ist eine Tatsache ueber Hypixel - nachgesehen an SkyHanni, das
 * dieselben vier Helme liest, aber keine Zeile von dort uebernommen.
 *
 * Zwei Dinge kommen dabei heraus. Erstens ist das, was in Sichtweite steht, keine
 * Vermutung mehr, sondern eine Leiche mit Sorte und Ort. Zweitens wird ihre Stelle
 * behalten: Die Liste aus dem Repo kennt fuenf Bauplaene nur in der ersten Ausfuehrung -
 * Amethyst 2 steht dort leer -, und dort zeigte die Mod bisher nichts und sagte auch
 * nicht, warum. Was man selbst findet, ist beim naechsten Besuch eine bekannte Stelle.
 */
public final class CorpseFinder {

    /** Welcher Helm welche Sorte ist. Andere Armorstaende tragen keinen davon */
    private static final Map<String, String> HELMETS = Map.of(
            "LAPIS_ARMOR_HELMET", "Lapis",
            "ARMOR_OF_YOG_HELMET", "Umber",
            "MINERAL_HELMET", "Tungsten",
            "VANGUARD_HELMET", "Vanguard");

    /** Alle halbe Sekunde reicht: Leichen laufen nicht weg */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Wie weit zwei Funde auseinanderliegen muessen, um zwei Stellen zu sein */
    private static final int SAME_SPOT_DISTANCE = 2;
    /** Mehr Stellen als das hat kein Bauplan. Die Grenze schuetzt die Config vor Muell */
    private static final int MAX_LEARNED = 40;

    /** Eine Leiche, die gerade zu sehen ist */
    public record Corpse(String type, BlockPos pos) {
    }

    private static List<Corpse> visible = List.of();
    private static int ticks = 0;

    private CorpseFinder() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CorpseFinder::tick);
    }

    /** Die Leichen in Sichtweite, mit Sorte und Ort */
    public static List<Corpse> visible() {
        return visible;
    }

    /**
     * Die selbst gefundenen Stellen dieses Schachts.
     *
     * Gemerkt wird je Bauplan und Ausfuehrung, denn genau das bestimmt den Bau -
     * "AMET_2" ist ein anderer Schacht als "AMET_1".
     */
    public static List<BlockPos> learned(String type, String variant) {
        if (type == null || variant == null) return List.of();
        List<String> saved = ModConfig.INSTANCE.mining.mineshaft.learnedCorpses.get(key(type, variant));
        if (saved == null || saved.isEmpty()) return List.of();

        List<BlockPos> out = new ArrayList<>(saved.size());
        for (String point : saved) {
            BlockPos pos = parse(point);
            if (pos != null) out.add(pos);
        }
        return out;
    }

    /** Wie viele Stellen fuer diesen Schacht selbst zusammengekommen sind */
    public static int learnedCount(String type, String variant) {
        List<String> saved = ModConfig.INSTANCE.mining.mineshaft.learnedCorpses.get(key(type, variant));
        return saved == null ? 0 : saved.size();
    }

    static String key(String type, String variant) {
        return type.toUpperCase(Locale.ROOT) + "_" + variant.toUpperCase(Locale.ROOT);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.mineshaftCorpses() || !MineshaftState.inMineshaft()) {
            visible = List.of();
            return;
        }
        if (client.level == null || client.player == null) {
            visible = List.of();
            return;
        }
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        List<Corpse> found = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;

            ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
            String id = SkyBlockItems.idOf(helmet);
            if (id == null) continue;

            String type = HELMETS.get(id.toUpperCase(Locale.ROOT));
            if (type != null) found.add(new Corpse(type, stand.blockPosition()));
        }
        visible = List.copyOf(found);

        if (ModConfig.INSTANCE.mining.mineshaft.corpseLearn) remember(found);
    }

    /**
     * Neue Stellen in die Config schreiben.
     *
     * Zwei Funde im Abstand von einem Block sind dieselbe Stelle: Ein Armorstand steht
     * nicht auf den Zentimeter dort, wo der naechste stand, und die Leiche wird spaeter
     * angeklickt, nicht ausgemessen.
     */
    private static void remember(List<Corpse> found) {
        if (found.isEmpty()) return;

        String key = key(MineshaftState.type(), MineshaftState.variant());
        Map<String, List<String>> all = ModConfig.INSTANCE.mining.mineshaft.learnedCorpses;
        List<String> saved = all.computeIfAbsent(key, k -> new ArrayList<>());

        List<BlockPos> known = new ArrayList<>();
        for (String point : saved) {
            BlockPos pos = parse(point);
            if (pos != null) known.add(pos);
        }
        // Die Liste aus dem Repo zaehlt mit: Was dort steht, muss nicht doppelt hier stehen
        known.addAll(com.shokiteufel.shokimod.util.MineshaftCorpses.forShaft(
                MineshaftState.type(), MineshaftState.variant()));

        int added = 0;
        for (Corpse corpse : found) {
            if (saved.size() >= MAX_LEARNED) break;
            if (near(known, corpse.pos())) continue;

            saved.add(corpse.pos().getX() + "," + corpse.pos().getY() + "," + corpse.pos().getZ());
            known.add(corpse.pos());
            added++;
        }
        if (added > 0) {
            ShokiMod.LOGGER.info("[Mineshaft] {}: {} new corpse spot(s) remembered, {} in total",
                    key, added, saved.size());
            ModConfig.INSTANCE.saveNow();
        }
    }

    private static boolean near(List<BlockPos> known, BlockPos pos) {
        for (int i = 0; i < known.size(); i++) {
            if (known.get(i).distSqr(pos) <= SAME_SPOT_DISTANCE * SAME_SPOT_DISTANCE) return true;
        }
        return false;
    }

    /** "x,y,z" - dieselbe Schreibweise wie im Repo, damit man beides vergleichen kann */
    static BlockPos parse(String point) {
        if (point == null) return null;
        String[] parts = point.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Alle Stellen dieses Schachts: die aus dem Repo und die selbst gefundenen.
     *
     * Reihenfolge mit Absicht - das Repo zuerst, damit die gewohnten Marker stehen, wo
     * sie immer standen, und die eigenen Funde hinten dazukommen.
     */
    public static List<BlockPos> allSpots(String type, String variant) {
        List<BlockPos> merged = new ArrayList<>(
                com.shokiteufel.shokimod.util.MineshaftCorpses.forShaft(type, variant));
        for (BlockPos pos : learned(type, variant)) {
            if (!near(merged, pos)) merged.add(pos);
        }
        return merged;
    }
}
