package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Misc > Shorten /warp und Replace /warp is: Befehle, die man tippt, werden vor dem Senden durch die richtigen ersetzt.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Shorten /warp" und "Replace /warp is"). Von dort stammt die Regel: Steht ein Befehl ohne alles dahinter
 * in der Liste der Warps ("/wizard"), geht stattdessen "/warp wizard" an den Server; "/warp is" wird zu "/is". Beides nur in SkyBlock, und auf der
 * privaten Insel bleibt "/jerry", was es dort ist. Die Umsetzung ist eigene Arbeit: Java, ein Einsprung in das Senden von Befehlen der Fabric-API
 * ({@code ALLOW_COMMAND}), der den getippten Befehl verwirft und den richtigen schickt.
 *
 * <p>Die Liste der Warps ist die, die SkyHanni fuehrt (96 Namen, von "arachne" bis "workshop"); sie steht fest hier, es wird nichts nachgeladen.
 * Was Hypixel neu dazubringt, fehlt, bis es hier eingetragen ist. SkyHannis Ausnahme fuer "/barn" im Garten entfaellt: Dort gibt es hier keine eigenen
 * Warp-Befehle. Das Vervollstaendigen mit der Tab-Taste, das SkyHanni dazu anbietet, ist Chat > Tab Complete > Warps (siehe {@link #warps()}).
 */
public final class WarpCommands {

    static final Set<String> WARPS = Set.of(
            "arachne", "atoll", "backwater", "barn", "base", "basecamp", "bayou", "camp", "canyon", "carnival", "castle", "ch", "ci", "cn",
            "crimson", "crypt", "crypts", "crystals", "da", "deep", "deeper", "desert", "dh", "dhub", "dmines", "drag", "dragon", "dragons",
            "dungeon_hub", "dungeons", "dwarves", "elizabeth", "end", "farming", "foraging", "forge", "galatea", "garden", "glacite", "glowing",
            "gold", "gt", "hollows", "home", "howl", "howling_cave", "hub", "island", "isle", "jerry", "jungle", "kuudra", "loch", "lotus",
            "mines", "moby", "moonglade", "mound", "murk", "murkwater", "museum", "nest", "nether", "nuc", "nucleus", "park", "rift", "safari",
            "sepulture", "skull", "smold", "smoldering", "smoldering_tomb", "spider", "spiders", "spring", "springs", "stonks", "taylor",
            "the_rift", "top", "torrhus", "torrhus_canyon", "tower", "trap", "trapper", "trees", "tunnel", "tunnels", "village", "void",
            "winter", "wiz", "wizard", "wizard_tower", "workshop");

    private WarpCommands() {
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(WarpCommands::allow);
    }

    /**
     * Was an die Stelle des getippten Befehls gehen soll, ohne Schraegstrich - oder null, wenn der Befehl bleibt, wie er ist.
     *
     * @param command       der getippte Befehl ohne Schraegstrich ("wizard", "warp is")
     * @param privateIsland ob man auf der privaten Insel ist (dort bleibt "jerry" ein eigener Befehl)
     */
    static String replacement(String command, boolean shorten, boolean replaceIs, boolean privateIsland) {
        if (command == null) return null;
        String c = command.trim().toLowerCase(Locale.ROOT);
        if (replaceIs && c.equals("warp is")) return "is";
        if (shorten && WARPS.contains(c)) {
            if (c.equals("jerry") && privateIsland) return null;
            return "warp " + c;
        }
        return null;
    }

    /**
     * Was fuer den getippten Befehl gesendet werden soll, nach den Schaltern und dem Ort - oder null, wenn der Befehl bleibt, wie er ist. Auch der Weg von
     * Chat > Tab Complete fragt hier, damit {@code /warp is} dort nicht an "Replace /warp is" vorbeigeht.
     *
     * @param command der Befehl ohne Schraegstrich
     */
    public static String rewrite(String command) {
        if (!FeatureGate.warpCommands() || !GameState.Server.isSkyblock()) return null;
        ModConfig.MiscCategory c = ModConfig.INSTANCE.misc;
        return replacement(command, c.shortenWarp, c.replaceWarpIs, "Private Island".equals(GameState.Server.map));
    }

    /** Die Namen der Warps, alphabetisch - die Vorschlaege hinter {@code /warp} (Chat > Tab Complete > Warps) */
    public static List<String> warps() {
        return WARPS.stream().sorted().toList();
    }

    private static boolean allow(String command) {
        String ersatz = rewrite(command);
        if (ersatz == null) return true;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return true;
        // Der Ersatz geht durch dieselbe Tuer wie jeder Befehl; er passt auf keine der beiden Regeln mehr, die Schleife schliesst sich also nicht
        connection.sendCommand(ersatz);
        return false;
    }
}
