package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.CustomMob;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Mobs in der Naehe, zusammengefasst fuer die Anzeige.
 *
 * Dieselbe Einteilung wie im Auswahlfenster: benannte Mobs nach ihrem bereinigten
 * Namen, namenlose nach Typ, Variante und Sichtbarkeit. Gleiche kommen als ein
 * Eintrag mit Anzahl - sonst stuenden zwanzig Zeilen "Zombie" untereinander.
 */
public final class NearbyMobs {

    /** Neu gesucht wird hoechstens zweimal je Sekunde; jeder Frame waere Verschwendung */
    private static final long SCAN_INTERVAL_MILLIS = 500L;

    /**
     * Ein gefundener Mob samt allem, was zum Aufnehmen in die eigene Liste noetig ist.
     *
     * @param pattern    Suchname bei benannten Mobs, sonst leer
     * @param typeId     Entity-Typ bei namenlosen, sonst leer
     * @param count      wie viele davon in Reichweite sind
     * @param distance   Entfernung zum naechsten
     */
    public record Entry(String label, String pattern, String typeId, String variant,
                        boolean invisible, int count, double distance) {

        public boolean isNamed() {
            return !pattern.isEmpty();
        }

        /** Baut daraus den Eintrag fuer die eigene Liste */
        public CustomMob toCustomMob() {
            return isNamed()
                    ? CustomMob.byName(pattern, CustomMob.DEFAULT_COLOR)
                    : CustomMob.byType(typeId, label, invisible, variant, CustomMob.DEFAULT_COLOR);
        }

        /** Steht der schon in der eigenen Liste? */
        public boolean alreadyAdded() {
            for (CustomMob mob : ModConfig.INSTANCE.mobVisuals.customTargets) {
                if (isNamed()) {
                    if (mob.isNameMode() && mob.pattern.equalsIgnoreCase(pattern)) return true;
                } else if (!mob.isNameMode() && typeId.equals(mob.typeId)
                        && variant.equalsIgnoreCase(mob.variant)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<Entry> cached = List.of();
    private static long cachedAt = 0L;

    private NearbyMobs() {
    }

    public static List<Entry> entries() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < SCAN_INTERVAL_MILLIS) return cached;
        cachedAt = now;
        cached = scan();
        return cached;
    }

    /** Nur die benannten oder nur die namenlosen */
    public static List<Entry> entries(boolean named) {
        return entries().stream().filter(e -> e.isNamed() == named).toList();
    }

    private static List<Entry> scan() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return List.of();

        double reach = ModConfig.INSTANCE.mobVisuals.pickRadiusBlocks();
        double reachSqr = reach * reach;
        Map<String, Entry> merged = new LinkedHashMap<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;

            double distanceSqr = entity.distanceToSqr(client.player);
            if (distanceSqr > reachSqr) continue;
            double distance = Math.sqrt(distanceSqr);

            String key;
            Entry fresh;
            if (entity.getCustomName() != null) {
                String pattern = CustomMob.cleanPattern(entity.getCustomName().getString());
                if (pattern.isEmpty()) continue;
                key = "name|" + pattern;
                fresh = new Entry(pattern, pattern, "", "", false, 1, distance);
            } else {
                // Namenlose nach Typ. Spieler und leere Namensschild-Staender sind keine Mobs
                if (entity instanceof Player) continue;
                if (entity instanceof ArmorStand stand && stand.isMarker()) continue;

                String variant = CustomMob.variantOf(entity);
                boolean invisible = entity.isInvisible();
                String typeId = entity.getType().getDescriptionId();

                // ShokiMod kennt manche dieser Mobs beim Namen - dann den nehmen,
                // statt dreimal "Tropical Fish" anzuzeigen
                String known = EntityHighlightManager.describe(entity);
                String label = known != null
                        ? known
                        : entity.getType().getDescription().getString()
                                + (variant.isEmpty() ? "" : " §7(" + variant.toLowerCase() + ")")
                                + (invisible ? " §8[invisible]" : "");

                key = "type|" + typeId + "|" + invisible + "|" + variant;
                fresh = new Entry(label, "", typeId, variant, invisible, 1, distance);
            }

            Entry previous = merged.get(key);
            merged.put(key, previous == null ? fresh
                    : new Entry(previous.label(), previous.pattern(), previous.typeId(),
                            previous.variant(), previous.invisible(), previous.count() + 1,
                            Math.min(previous.distance(), distance)));
        }

        List<Entry> out = new ArrayList<>(merged.values());
        out.sort(Comparator.comparingDouble(Entry::distance));
        return out;
    }
}
