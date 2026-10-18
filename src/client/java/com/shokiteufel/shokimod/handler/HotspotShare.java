package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Bietet an, einen gefundenen Hotspot mit der Gruppe oder allen zu teilen - mit einem Klick.
 *
 * <p>Nachgebaut aus Feesh (Apache 2.0, Sleepy-Panda/Feesh, {@code features/chat/HotspotFoundMessage.kt} und
 * {@code utils/HotspotUtils.kt}): Wer mit einer Angel in der Hotbar in einer Welt mit Hotspots steht, bekommt, sobald
 * er einem Hotspot auf zehn Bloecke nahe kommt, eine Zeile "You found +15% Fishing Speed Hotspot." und darunter zwei
 * anklickbare Stellen, "[Share to PARTY chat]" und "[Share to ALL chat]". Ein Klick schickt eine Zeile wie
 * "x: 123, y: 64, z: -45 | +15% Fishing Speed Hotspot at Torrhus Springs" an die Gruppe oder - mit einer Kennung
 * dahinter, damit Hypixel sie nicht als doppelt verwirft - an alle.
 *
 * <p>Jeder Hotspot wird einmal angeboten. Gemerkt werden die letzten zwei, damit es beim Hin- und Herlaufen zwischen
 * zwei nahen Hotspots nicht immer wieder kommt.
 *
 * <p>Gefunden wird er wie bei Feesh: Ein Ruestungsstaender mit dem Namen "HOTSPOT", und der Bonus steht auf dem
 * Staender knapp darunter (gleiches X und Z, bis zu einem Block tiefer, gleicher Neigungswinkel). Das ist ein anderer
 * Weg als der der Hotspot-Kreise ({@link HotspotTracker}), der den Bonus-Staender sucht - der Vorschlag laeuft
 * deshalb auch, wenn die Kreise aus sind.
 */
public final class HotspotShare {

    /** So nah muss man sein - Feeshs Wert */
    static final double RANGE = 10.0;
    private static final int SCAN_TICKS = 10;
    /** Die Welten mit Hotspots - Feeshs Liste */
    private static final Set<String> WORLDS = Set.of("Backwater Bayou", "Spider's Den", "Hub", "Jerry's Workshop",
            "Lotus Atoll", "The Park", "Crimson Isle", "Torrhus Canyon");
    private static final String ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern CODE = Pattern.compile("§.");

    /** Ein gefundener Hotspot; der Bonus ist null, wenn kein Staender darunter steht */
    record Found(UUID id, double x, double y, double z, String perk) {
    }

    private static final List<UUID> foundIds = new ArrayList<>();
    private static UUID lastClosest = null;
    private static Object lastLevel = null;
    private static int ticks = 0;

    private HotspotShare() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HotspotShare::tick);
    }

    /** Gibt es in dieser Welt Hotspots? */
    static boolean applies(String map) {
        return map != null && WORLDS.contains(map);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.hotspotShare()) return;
        // Eine neue Welt vergisst, was schon angeboten wurde
        if (client.level != lastLevel) {
            lastLevel = client.level;
            foundIds.clear();
            lastClosest = null;
        }
        if (client.player == null || !GameState.Server.isSkyblock() || !applies(GameState.Server.map)) return;
        if (!hasRod(client.player)) return;

        if (++ticks < SCAN_TICKS) return;
        ticks = 0;
        try {
            Found found = findClosest(client);
            if (found != null && due(found.id())) announce(client, found, true);
        } catch (RuntimeException e) {
            ShokiMod.LOGGER.warn("[HotspotShare] scan failed: {}", e.toString());
        }
    }

    /** Eine Angel in der Hotbar - nur dann ist man zum Angeln da und nicht nur auf der Durchreise */
    private static boolean hasRod(Player player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.FISHING_ROD)) return true;
        }
        return false;
    }

    /**
     * Soll dieser Hotspot jetzt angeboten werden? Merkt ihn sich dabei.
     *
     * Wie bei Feesh: Steht er unter den letzten zwei, nein. Sonst ja - es sei denn, es ist der, der zuletzt der
     * naechste war.
     */
    static boolean due(UUID id) {
        if (foundIds.contains(id)) return false;
        boolean offer = lastClosest == null || !lastClosest.equals(id);
        if (offer) {
            foundIds.add(0, id);
            if (foundIds.size() > 2) foundIds.remove(foundIds.size() - 1);
        }
        lastClosest = id;
        return offer;
    }

    /** Fuer die Probe: das Gedaechtnis loeschen */
    static void resetForTest() {
        foundIds.clear();
        lastClosest = null;
        lastLevel = null;
        ticks = 0;
    }

    // ---- Den naechsten Hotspot finden ----

    /** Ein benannter Ruestungsstaender, so wie die Suche ihn braucht; der Bonustext wird nur geholt, wenn er gebraucht wird */
    record Stand(UUID id, double x, double y, double z, float xRot, String name, Supplier<String> formatted) {
    }

    /** Der naechste Hotspot in zehn Bloecken, samt Bonus - oder null */
    private static Found findClosest(Minecraft client) {
        double limit = RANGE * RANGE;
        List<Stand> near = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand && stand.getCustomName() != null
                    && stand.distanceToSqr(client.player) <= limit) {
                Component label = stand.getCustomName();
                near.add(new Stand(stand.getUUID(), stand.getX(), stand.getY(), stand.getZ(), stand.getXRot(),
                        label.getString(), () -> SeaCreatureHp.legacy(label)));
            }
        }
        return pick(near, client.player.getX(), client.player.getY(), client.player.getZ());
    }

    /**
     * Aus den Staendern in der Naehe den naechsten Hotspot und seinen Bonus heraussuchen.
     *
     * Der Hotspot ist der Staender, der "HOTSPOT" heisst. Sein Bonus steht auf einem Staender knapp darunter:
     * gleiches X und Z, hoechstens einen Block tiefer, gleicher Neigungswinkel - so wie bei Feesh.
     */
    static Found pick(List<Stand> stands, double px, double py, double pz) {
        double limit = RANGE * RANGE;
        Stand hotspot = null;
        double best = Double.MAX_VALUE;
        for (Stand stand : stands) {
            if (!"HOTSPOT".equals(clean(stand.name()))) continue;
            double distance = square(stand.x() - px) + square(stand.y() - py) + square(stand.z() - pz);
            if (distance <= limit && distance < best) {
                best = distance;
                hotspot = stand;
            }
        }
        if (hotspot == null) return null;

        String perk = null;
        for (Stand stand : stands) {
            if (stand == hotspot) continue;
            if (stand.x() == hotspot.x() && stand.z() == hotspot.z() && stand.y() < hotspot.y()
                    && hotspot.y() - stand.y() <= 1.0 && stand.xRot() == hotspot.xRot()) {
                perk = stand.formatted().get();
                break;
            }
        }
        return new Found(hotspot.id(), hotspot.x(), hotspot.y(), hotspot.z(), perk);
    }

    private static double square(double value) {
        return value * value;
    }

    private static String clean(String text) {
        return text == null ? "" : CODE.matcher(text).replaceAll("").trim();
    }

    // ---- Der Vorschlag ----

    /**
     * Die Zeile, die geteilt wird: "x: 123, y: 64, z: -45 | +15% Fishing Speed Hotspot at Torrhus Springs".
     *
     * @param perk   der Bonus, mit oder ohne Farbcodes; null, wenn keiner da ist
     * @param zone   die Zone aus der Seitenleiste; null laesst das "at ..." weg
     * @param withId ob eine Kennung dahinter steht - bei der Nachricht an alle, damit Hypixel sie nicht als
     *               doppelt verwirft
     */
    static String shareMessage(double x, double y, double z, String perk, String zone, boolean withId) {
        String location = "x: " + Math.round(x) + ", y: " + Math.round(y) + ", z: " + Math.round(z);
        String perkText = perk == null || clean(perk).isEmpty() ? "" : clean(perk) + " ";
        String zoneText = zone == null || zone.isBlank() ? "" : " at " + zone.trim();
        return location + " | " + perkText + "Hotspot" + zoneText + (withId ? " | " + messageId() : "");
    }

    /** "@" und zehn zufaellige Zeichen - Hypixel laesst dieselbe Zeile nicht zweimal durch */
    static String messageId() {
        StringBuilder id = new StringBuilder("@");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) id.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
        return id.toString();
    }

    /**
     * Die beiden Zeilen im Chat.
     *
     * @param live ob die Stellen anklickbar sind. Der Testknopf zeigt sie nur: ein Klick auf eine Beispielzeile
     *             soll nichts an die Gruppe schicken.
     */
    static List<Component> offer(String perk, String partyMessage, String allMessage, boolean live) {
        MutableComponent found = Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("You found ").withStyle(ChatFormatting.WHITE));
        if (perk != null && !clean(perk).isEmpty()) {
            found.append(Component.literal(perk + " ").withStyle(ChatFormatting.WHITE));
        }
        found.append(Component.literal("Hotspot").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(".").withStyle(ChatFormatting.WHITE));

        MutableComponent party = share("PARTY", ChatFormatting.BLUE, "/pchat " + partyMessage, live);
        MutableComponent all = share("ALL", ChatFormatting.YELLOW, "/achat " + allMessage, live);
        MutableComponent choice = Component.empty().append(party)
                .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                .append(all);
        return List.of(found, choice);
    }

    /** "[Share to PARTY chat]" - weiss und fett, das Wort in seiner Farbe; ein Klick fuehrt den Befehl aus */
    private static MutableComponent share(String where, ChatFormatting colour, String command, boolean live) {
        MutableComponent text = Component.empty()
                .append(Component.literal("[Share to ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(where + " ").withStyle(colour, ChatFormatting.BOLD))
                .append(Component.literal("chat]").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        String hover = live ? "Click to share to " + where + " chat" : "Test - nothing is sent";
        return text.withStyle(style -> live
                ? style.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
                : style.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static void announce(Minecraft client, Found found, boolean live) {
        if (client.player == null) return;
        String zone = "Backwater Bayou".equals(GameState.Server.map)
                ? null // Der Bayou hat nur eine Zone, die muss nicht dastehen
                : SoundMute.zoneOf(ScoreboardUtils.getSidebarLines(client));
        String party = shareMessage(found.x(), found.y(), found.z(), found.perk(), zone, false);
        String all = shareMessage(found.x(), found.y(), found.z(), found.perk(), zone, true);
        for (Component line : offer(found.perk(), party, all, live)) client.player.sendSystemMessage(line);
        if (ModConfig.INSTANCE.fishing.hotspot.shareSound) {
            client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, AlertVolume.factor(), 1.0f);
        }
        ShokiMod.LOGGER.info("[HotspotShare] offered {}", party);
    }

    /** Der Knopf in den Einstellungen: ein Beispiel an der eigenen Stelle - nicht anklickbar */
    public static void test() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        Found sample = new Found(UUID.randomUUID(), client.player.getX(), client.player.getY(), client.player.getZ(),
                "§b+15% Fishing Speed");
        announce(client, sample, false);
    }
}
