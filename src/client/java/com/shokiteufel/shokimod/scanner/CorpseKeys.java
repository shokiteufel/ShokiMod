package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Welche Leichen-Schluessel man dabei hat - und fuer welche Leiche im Schacht keiner da ist.
 *
 * Eine Lapis-Leiche braucht keinen Schluessel, eine Tungsten einen TUNGSTEN_KEY, eine
 * Umber einen UMBER_KEY und eine Vanguard einen SKELETON_KEY. Das ist eine Tatsache
 * ueber Hypixel, nachgesehen in SkyHannis CorpseType (AGPL, kein Code uebernommen) -
 * und eine, die man leicht falsch raet: Der Skeleton Key gehoert nicht zu Lapis.
 *
 * Der Sinn: Vor einer Umber-Leiche zu stehen und den Schluessel nicht dabei zu haben,
 * merkt man sonst erst dort. Im Schacht steht die Zahl neben der Leiche.
 */
public final class CorpseKeys {

    /** Welche Leichensorte welchen Schluessel braucht. Lapis steht nicht dabei - sie braucht keinen */
    private static final Map<String, String> KEYS = Map.of(
            "tungsten", "TUNGSTEN_KEY",
            "umber", "UMBER_KEY",
            "vanguard", "SKELETON_KEY");

    /** Der Name, wie er im Kasten stehen soll */
    private static final Map<String, String> LABELS = Map.of(
            "TUNGSTEN_KEY", "Tungsten",
            "UMBER_KEY", "Umber",
            "SKELETON_KEY", "Skeleton");

    /** Zweimal je Sekunde reicht: Das Inventar durchzuzaehlen ist billig, aber nicht umsonst */
    private static final long SCAN_INTERVAL_MILLIS = 500L;

    private static Map<String, Integer> counts = Map.of();
    private static long lastScanMillis = 0L;

    private CorpseKeys() {
    }

    /**
     * Wie viele Schluessel dieser Art man dabei hat.
     *
     * @param keyId TUNGSTEN_KEY, UMBER_KEY oder SKELETON_KEY
     */
    public static int count(String keyId) {
        Integer found = counts().get(keyId);
        return found == null ? 0 : found;
    }

    /** Der Schluessel, den diese Leichensorte braucht - oder null, wenn keiner noetig ist */
    public static String keyFor(String corpseType) {
        return corpseType == null ? null : KEYS.get(corpseType.toLowerCase(Locale.ROOT));
    }

    /** Fehlt fuer diese Leichensorte ein Schluessel? */
    public static boolean missing(String corpseType) {
        String key = keyFor(corpseType);
        return key != null && count(key) <= 0;
    }

    /** Was man dabei hat, als Zeile: "1 Umber, 2 Skeleton" - oder leer */
    public static String summary() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            int amount = count(entry.getKey());
            if (amount <= 0) continue;
            if (!out.isEmpty()) out.append(", ");
            out.append(amount).append(' ').append(entry.getValue());
        }
        return out.toString();
    }

    /**
     * Die Schluessel im Inventar, hoechstens zweimal je Sekunde neu gezaehlt.
     *
     * Gezaehlt wird nur das Inventar, nicht die Ender-Kiste: Was dort liegt, hilft vor
     * einer Leiche nicht weiter.
     */
    private static Map<String, Integer> counts() {
        long now = System.currentTimeMillis();
        if (now - lastScanMillis < SCAN_INTERVAL_MILLIS) return counts;
        lastScanMillis = now;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            counts = Map.of();
            return counts;
        }

        Map<String, Integer> found = new LinkedHashMap<>();
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            String id = SkyBlockItems.idOf(stack);
            if (id == null || !LABELS.containsKey(id)) continue;
            found.merge(id, stack.getCount(), Integer::sum);
        }
        counts = Map.copyOf(found);
        return counts;
    }

    /**
     * Die Leichen des Schachts, fuer die ein Schluessel fehlt.
     *
     * Gefragt wird nur nach den noch offenen: Eine gepluenderte Leiche braucht keinen
     * Schluessel mehr.
     */
    public static List<String> missingFor(List<MiningState.Corpse> corpses) {
        if (corpses == null || corpses.isEmpty() || !ModConfig.INSTANCE.mining.mineshaft.corpseKeys) {
            return List.of();
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < corpses.size(); i++) {
            MiningState.Corpse corpse = corpses.get(i);
            if (corpse.open() <= 0) continue;
            if (missing(corpse.type()) && !out.contains(corpse.type())) out.add(corpse.type());
        }
        return out;
    }
}
