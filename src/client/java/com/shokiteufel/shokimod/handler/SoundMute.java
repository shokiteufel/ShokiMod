package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.MuteCategory;
import com.shokiteufel.shokimod.data.ModConstants;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Zwei Toene stumm schalten, die beim Angeln stoeren.
 *
 * <p>Nachgebaut aus Feesh (Apache 2.0, Sleepy-Panda/Feesh, {@code features/sounds/MuteJadeDragonSound} und
 * {@code MuteReindrakeGifts}): Der Mixin in {@code SoundEngine.play} fragt hier fuer jeden Ton, ob er
 * verschluckt werden soll.
 * <ul>
 *   <li><b>Jade Dragon:</b> alle Toene {@code minecraft:entity.ender_dragon.*}, solange man im Moonglade Marsh
 *       in einer der beiden Zonen seiner Hoehle steht, "Dragon's Lair" oder "Murkwater Depths".</li>
 *   <li><b>Reindrake-Geschenke:</b> der laute Ton {@code minecraft:item.totem.use}, wenn man in Jerry's
 *       Workshop ein Geschenk aufhebt. Er wird an der Lautstaerke erkannt: nur der mit genau 0,5 ist das
 *       Geschenk; ein echter Totem der Unsterblichkeit klingt anders und bleibt hoerbar.</li>
 * </ul>
 *
 * <p>Die Zone steht nicht in der Tab-Liste, sondern in der Seitenleiste ("⏣ Murkwater Depths"). Sie wird
 * einmal in der Sekunde gelesen, und nur, wenn der Jade-Dragon-Schalter an ist und man im Moonglade Marsh steht.
 */
public final class SoundMute {

    static final String JADE_DRAGON_PREFIX = "entity.ender_dragon.";
    static final String REINDRAKE_SOUND = "item.totem.use";
    /** Die Lautstaerke, mit der Hypixel den Ton beim Geschenk spielt */
    static final float REINDRAKE_VOLUME = 0.5f;
    static final String DRAGON_LAIR = "Dragon's Lair";
    static final String MURKWATER_DEPTHS = "Murkwater Depths";

    private static final int SCAN_TICKS = 20;
    private static final Pattern CODE = Pattern.compile("§.");
    /** Die Zone steht hinter einem Zeichen ausserhalb von ASCII, und manche haben ein Bild dazwischen */
    private static final Pattern NON_ASCII = Pattern.compile("[^\\u0000-\\u007F]");

    private static boolean inDragonCave = false;
    private static int ticks = 0;
    private static int muted = 0;

    private SoundMute() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SoundMute::tick);
    }

    private static MuteCategory cfg() {
        return ModConfig.INSTANCE.fishing.mute;
    }

    /** Wie viele Toene bisher verschluckt wurden - zum Nachsehen im Log und fuer die Probe */
    public static int mutedCount() {
        return muted;
    }

    // ---- Wo steht man? ----

    private static void tick(Minecraft client) {
        if (!FeatureGate.mute()) {
            inDragonCave = false;
            return;
        }
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;

        inDragonCave = false;
        if (!cfg().jadeDragon || client.level == null || !GameState.Server.isSkyblock()
                || !GameState.Server.isMoongladeMarsh()) {
            return;
        }
        String zone = zoneOf(ScoreboardUtils.getSidebarLines(client));
        inDragonCave = DRAGON_LAIR.equals(zone) || MURKWATER_DEPTHS.equals(zone);
    }

    /**
     * Die Zone aus den Zeilen der Seitenleiste: die Zeile mit dem Ortszeichen, ohne dieses und ohne alles,
     * was nicht ASCII ist. "⏣ Abandoned🐍 Quarry" wird zu "Abandoned Quarry".
     */
    static String zoneOf(List<String> lines) {
        for (String raw : lines) {
            String line = CODE.matcher(raw).replaceAll("");
            if (!line.contains("⏣") && !line.contains("ф")) continue;
            String zone = NON_ASCII.matcher(line).replaceAll("").trim();
            return zone.isEmpty() ? null : zone;
        }
        return null;
    }

    /** Fuer die Probe: so tun, als stuende man in der Hoehle */
    static void setInDragonCaveForTest(boolean in) {
        inDragonCave = in;
    }

    // ---- Der Ton ----

    /**
     * Soll dieser Ton verschluckt werden?
     *
     * @param id     der Ton, mit Namensraum ("minecraft:item.totem.use")
     * @param volume die Lautstaerke, die der Erzeuger eingestellt hat
     */
    public static boolean shouldCancel(Identifier id, float volume) {
        MuteCategory c = cfg();
        if (!c.jadeDragon && !c.reindrakeGifts) return false;
        if (id == null || !"minecraft".equals(id.getNamespace())) return false;

        String path = id.getPath();
        boolean cancel = (c.jadeDragon && inDragonCave && path.startsWith(JADE_DRAGON_PREFIX))
                || (c.reindrakeGifts && volume == REINDRAKE_VOLUME && REINDRAKE_SOUND.equals(path)
                && inJerrysWorkshop());
        if (cancel) muted++;
        return cancel;
    }

    private static boolean inJerrysWorkshop() {
        return GameState.Server.isSkyblock() && ModConstants.MAP_JERRYS_WORKSHOP.equals(GameState.Server.map);
    }
}
