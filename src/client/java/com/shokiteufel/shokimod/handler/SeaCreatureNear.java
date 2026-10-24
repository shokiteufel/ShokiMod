package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.NearbyRareCategory;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seltene Meeresbewohner in der Naehe - gelesen aus den Mobs um einen herum, nicht aus dem Chat.
 *
 * <p>Die Erkennung ist die der HP-Anzeige ({@link SeaCreatureHp}, nachgebaut aus Feesh): Hypixel stellt ueber jeden Bewohner ein
 * Namensschild "[Lv600] Name 69M/100M", und der Mob steht darunter (meist mit der Kennung des Schildes minus eins, siehe {@link #mobUnder}). Daraus ergibt sich, wer in der
 * Naehe ist - auch wenn ein anderer ihn hochgezogen hat und nichts in den Gruppenchat schreibt, und auch ohne dass irgendein Satz im Chat
 * vorkommt. Zwei Dinge gibt es dafuer: eine Ansage, einmal je Mob, und einen Kasten um den Mob ({@code SeaCreatureBoxRenderer}), der auch
 * durch Waende zu sehen ist, aber nur in einem kleinen Umkreis.
 *
 * <p>Ansage: Der Mob muss erst eine Sekunde zu sehen sein. In dieser Zeit kommt der Satz im Chat, falls er kommt, und der Alarm im Reiter
 * "Rare catch" sagt ihn an - dann sagt dieser hier es nicht noch einmal (der Kasten bleibt). Ein Mob, der aus dem Umkreis geht und
 * wiederkommt, wird nicht noch einmal angesagt: Er ist derselbe.
 */
public final class SeaCreatureNear {

    /** Alle fuenf Ticks nachsehen, wie bei der HP-Anzeige */
    private static final int SCAN_TICKS = 5;
    /** So lange muss ein Mob zu sehen sein, bevor er angesagt wird - damit der Chat-Alarm vorher dran ist */
    static final long SETTLE_MILLIS = 1000L;
    /** Hat der Alarm im Chat den Bewohner in dieser Zeit schon gesagt, schweigt dieser hier */
    static final long CHAT_ALERT_WINDOW_MILLIS = 10_000L;
    /** So lange bleibt ein angesagter Mob im Gedaechtnis */
    private static final long REMEMBER_MILLIS = 6 * 60 * 1000L;
    /** Ein Mob, dessen Schild so lange nicht mehr gelesen wurde, ist weg - und bekommt keinen Kasten mehr */
    private static final long GONE_MILLIS = 3000L;
    /** Wie weit die Kennung davor vom Schild entfernt stehen darf, damit sie gilt: grosszuegig, ein schneller Mob laeuft dem Schild um einen Tick voraus */
    private static final double GUESS_RADIUS = 2.5;
    /** Wie nah ein Mob unter dem Schild stehen muss, wenn die Kennung nichts ergab: knapp, damit es nicht der Nachbar ist */
    private static final double SEARCH_RADIUS = 1.0;

    /** Wer gerade in der Naehe steht */
    public static final class Sighting {
        /** Die Kennung des Namensschildes: Es bleibt, solange der Bewohner da ist, und ist darum der Schluessel */
        final int standId;
        /** Die Kennung des Mobs unter dem Schild - die der Kasten umschliesst. Wird bei jedem Durchgang neu bestimmt */
        public int mobId;
        public final SeaCreatures.Creature creature;
        final long firstSeen;
        long lastSeen;
        double distance;
        boolean decided;

        Sighting(int standId, int mobId, SeaCreatures.Creature creature, long now) {
            this.standId = standId;
            this.mobId = mobId;
            this.creature = creature;
            this.firstSeen = now;
            this.lastSeen = now;
        }
    }

    /** Kennung des Namensschildes -> was dort steht. Nur im Haupt-Thread angefasst */
    private static final Map<Integer, Sighting> current = new HashMap<>();
    /** Wann ein Bewohner angesagt wurde (oder haette werden sollen), nach der Kennung seines Schildes - damit er nicht noch einmal drankommt */
    private static final Map<Integer, Long> decidedAt = new HashMap<>();
    /** Was die Ansage getan hat, fuer Pruefungen: "Yeti: shown" oder "Yeti: suppressed" */
    private static final List<String> decisions = new ArrayList<>();
    private static int ticks = 0;

    private SeaCreatureNear() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SeaCreatureNear::tick);
    }

    private static NearbyRareCategory cfg() {
        return ModConfig.INSTANCE.fishing.nearby;
    }

    // ---- Die Auswahl ----

    /** Zaehlt dieser Bewohner? Ohne eigene Wahl gilt: die seltenen ja, die uebrigen nein - wie im Alarm "Rare catch" */
    public static boolean wants(SeaCreatures.Creature creature) {
        if (creature == null || creature.hpOnly()) return false;
        return cfg().picks.getOrDefault(creature.name(), creature.rare());
    }

    public static void toggle(SeaCreatures.Creature creature) {
        if (creature == null || creature.hpOnly()) return;
        cfg().picks.put(creature.name(), !wants(creature));
        ModConfig.INSTANCE.saveNow();
    }

    // ---- Was gerade in der Naehe steht (fuer den Kasten) ----

    /** Steht gerade jemand in der Naehe? Billig: der Zeichner fragt es in jedem Bild, bevor er etwas anfasst */
    public static boolean hasSightings() {
        return !current.isEmpty();
    }

    /** Die Sichtungen, die jetzt einen Kasten bekommen koennen. Eine Kopie */
    public static List<Sighting> sightings() {
        return new ArrayList<>(current.values());
    }

    /** Fuer Pruefungen: was die Ansagen entschieden haben */
    static List<String> decisions() {
        return decisions;
    }

    /** Fuer Pruefungen: alles vergessen */
    static void reset() {
        current.clear();
        decidedAt.clear();
        decisions.clear();
        ticks = 0;
    }

    // ---- Tick ----

    private static void tick(Minecraft client) {
        if (!FeatureGate.nearbyRare()) {
            if (!current.isEmpty()) current.clear();
            return;
        }
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;

        if (client.level == null || client.player == null || !GameState.Server.isSkyblock()) {
            if (!current.isEmpty()) current.clear();
            return;
        }
        try {
            scan(client, System.currentTimeMillis());
        } catch (RuntimeException e) {
            // Ein Namensschild, das wir nicht kennen, soll den Alarm nicht lahmlegen
            ShokiMod.LOGGER.warn("[NearRare] scan failed: {}", e.toString());
        }
    }

    /** Ein Durchgang; die Uhr als Parameter, damit sich die Zeiten ohne Warten pruefen lassen */
    static void scan(Minecraft client, long now) {
        NearbyRareCategory c = cfg();
        double range = Math.max(c.alertRange, c.hitbox ? c.hitboxRange : 0);
        String map = GameState.Server.map;

        Set<Integer> seenNow = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
            // Billig zuerst: ein Namensschild eines Bewohners traegt "Lv"
            String plain = stand.getCustomName().getString();
            if (!plain.contains("Lv")) continue;
            double distance = client.player.distanceTo(stand);
            if (distance > range) continue;

            SeaCreatureHp.Parsed parsed = SeaCreatureHp.parse(stand.getId(), SeaCreatureHp.legacy(stand.getCustomName()), plain);
            if (parsed == null) continue;
            SeaCreatures.Creature creature = SeaCreatures.byName(parsed.name());
            if (creature == null || !wants(creature) || !SeaCreatures.availableIn(creature.name(), map)) continue;

            int standId = stand.getId();
            seenNow.add(standId);
            Sighting s = current.computeIfAbsent(standId, id -> new Sighting(id, -1, creature, now));
            s.mobId = mobUnder(client, stand);
            s.lastSeen = now;
            s.distance = distance;
        }

        // Wer nicht mehr gelesen wird, ist weg
        current.values().removeIf(s -> !seenNow.contains(s.standId) && now - s.lastSeen > GONE_MILLIS);
        decidedAt.values().removeIf(at -> now - at > REMEMBER_MILLIS);

        for (Sighting s : current.values()) {
            if (s.decided || now - s.firstSeen < SETTLE_MILLIS) continue;
            // Weit weg steht er im Kasten-Umkreis, ist aber noch keine Ansage wert
            if (s.distance > c.alertRange) continue;
            s.decided = true;
            if (decidedAt.containsKey(s.standId)) continue;
            decidedAt.put(s.standId, now);
            if (RareCatchAlert.announcedRecently(s.creature.name(), CHAT_ALERT_WINDOW_MILLIS)) {
                decisions.add(s.creature.name() + ": suppressed");
                ShokiMod.LOGGER.info("[NearRare] {} at {} blocks - already announced from the chat", s.creature.name(), Math.round(s.distance));
                continue;
            }
            announce(s.creature, s.distance);
        }
    }

    // ---- Der Mob unter dem Schild ----

    /**
     * Welcher Mob zu diesem Namensschild gehoert. Hypixel spawnt erst den Mob und gleich danach das Schild, also hat der Mob die Kennung des
     * Schildes minus eins (so liest es auch Feesh). Das gilt aber nur, solange nichts anderes dazwischen entsteht - ein Tier, das irgendwo
     * in der Naehe aufgeht, schiebt sich in die Zaehlung und der Mob liegt dann zwei oder drei Kennungen davor. Darum zaehlt die Kennung nur,
     * wenn dort wirklich einer unter dem Schild steht; sonst ist es der naechste, der unter dem Schild steht.
     */
    static int mobUnder(Minecraft client, ArmorStand stand) {
        int guessId = stand.getId() - 1;
        Entity guess = client.level.getEntity(guessId);
        if (guess != null && isUnder(guess, stand, GUESS_RADIUS)) return guessId;

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : client.level.entitiesForRendering()) {
            if (!isUnder(e, stand, SEARCH_RADIUS)) continue;
            double dx = e.getX() - stand.getX();
            double dz = e.getZ() - stand.getZ();
            double score = dx * dx + dz * dz;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        // Nichts gefunden: bei der Kennung davor bleiben, wie bisher
        return best != null ? best.getId() : guessId;
    }

    /** Steht dieses Wesen unter dem Schild? Gleiche Stelle von oben gesehen, und das Schild darueber */
    private static boolean isUnder(Entity e, ArmorStand stand, double radius) {
        if (e == stand || e instanceof ArmorStand || e.isRemoved()) return false;
        double dx = e.getX() - stand.getX();
        double dz = e.getZ() - stand.getZ();
        double above = stand.getY() - e.getY();
        return dx * dx + dz * dz <= radius * radius && above >= -1.0 && above <= 8.0;
    }

    // ---- Die Ansage ----

    /**
     * Banner, Chatzeile und Ton - wie bei einem Fang, nur mit "Nearby" in der Zeile, in der sonst der Fangende steht.
     * Das Banner nimmt das Design von "Rare catch", damit nicht zwei Sprachen auf dem Bildschirm stehen.
     */
    static void announce(SeaCreatures.Creature creature, double distance) {
        NearbyRareCategory c = cfg();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        long blocks = Math.round(distance);
        decisions.add(creature.name() + ": shown");

        if (c.banner) {
            BannerDesign look = RareCatchAlert.design();
            String byline = "Nearby - " + blocks + " blocks";
            if (look.background == BannerDesign.Background.TITLE) {
                DropBanner.show(look, byline, RareCatchAlert.feeshTitle(creature, false), "", "",
                        creature.colour(), ItemStack.EMPTY, 0d);
            } else {
                DropBanner.show(look, byline, creature.name(), "", creature.rarity(), creature.colour(), ItemStack.EMPTY, 0d);
            }
        }
        if (c.chatLine) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                    .withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(creature.name()).withStyle(style -> style.withColor(creature.colour()).withBold(true)))
                    .append(Component.literal(" nearby - " + blocks + " blocks").withStyle(ChatFormatting.GRAY)));
        }
        if (c.sound) {
            // Der eigene Klang des Bewohners geht vor; ohne ihn der allgemeine aus "Rare catch"
            String ton = RareCatchAlert.soundOf(creature);
            if (ton.isBlank()) ton = ModConfig.INSTANCE.fishing.rareCatch.sound;
            if (ton != null && !ton.isBlank()) CustomSoundPlayer.play(ton, AlertVolume.factor(), SeaCreatureNear.class);
        }
        ShokiMod.LOGGER.info("[NearRare] {} at {} blocks", creature.name(), blocks);
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren, mit einem Yeti in zwoelf Bloecken */
    public static void test() {
        SeaCreatures.Creature probe = SeaCreatures.byName("Yeti");
        if (probe == null) {
            List<SeaCreatures.Creature> selten = SeaCreatures.rare();
            if (selten.isEmpty()) return;
            probe = selten.get(0);
        }
        announce(probe, 12.0);
    }
}
