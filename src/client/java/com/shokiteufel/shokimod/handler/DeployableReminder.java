package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DeployableEndingsCategory;
import com.shokiteufel.shokimod.data.ModConfig.DeployablesCategory;
import com.shokiteufel.shokimod.data.ModConfig.UmberellaCategory;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Meldet, wenn eines deiner Deployables verschwindet: Power Orb, Flare, Umberella, Laterne, Totem of
 * Corruption oder Black Hole.
 *
 * <p>Gelesen werden die Zeilen, die Hypixel selbst in den Chat schreibt - "Your Plasmaflux Power Orb
 * despawned." oder "Your Umberella despawned." (abgelaufen), "Your previous X was removed!" (durch ein
 * neues ersetzt) und "Your flare disappeared because you were too far away!" (zu weit weg). Sie kommen
 * nur fuer das eigene Deployable. Ein fremder Orb in der Naehe, der ablaeuft, steht dort nicht, und den
 * muss man auch nicht ersetzen.
 *
 * <p><b>Wie andere Mods es machen.</b> SkyHanni (Deployable.kt), Riccio's Fishing Utils und SkyCubed
 * lesen den Namen des Ruestungsstaenders ueber dem Deployable ("Umberella 298s", "Plasmaflux 28s") und
 * rechnen die Restzeit selbst. Das taugt fuer eine Anzeige der Restzeit, aber fuer "ist abgelaufen" ist es
 * der Umweg: Der Staender ist nur da, solange er gerendert wird - wer weitergelaufen ist, verliert ihn,
 * und ein fremdes Deployable in Reichweite sieht genauso aus wie das eigene (SkyHanni fragt deshalb nach
 * der Entfernung). Die Chatzeile nennt den Zeitpunkt, an dem das eigene Deployable wirklich verschwindet,
 * und sonst nichts. Die beiden Zeilen "previous ... removed" und "too far away" kennt auch Feesh
 * (DeployablesTimer.kt, Apache 2.0); beides sind Texte von Hypixel.
 *
 * <p><b>Die Warnung vorher.</b> Die Chatzeile kommt erst, wenn das Deployable weg ist. Die Warnung davor
 * (standardmaessig zehn Sekunden) braucht die Restzeit, und die liest {@link DeployableCountdown} von den
 * Staendern in der Welt. Hat sie gewarnt, bleibt die Zeile "despawned" ohne eigene Meldung; sie springt nur
 * ein, wenn es keine Warnung gab - das Deployable war nicht in Reichweite, oder die Warnung ist aus (0).
 *
 * <p><b>Kein Alarm beim Kartenwechsel.</b> Wer die Insel wechselt, laesst alles zurueck, was er
 * aufgestellt hat - und Hypixel schreibt dazu seine Zeilen. Das ist kein Ablauf. Ob die Zeile vor oder
 * nach dem Wechsel kommt, weiss man nicht, deshalb gilt beides: Kommt sie kurz nach einem Wechsel, wird
 * sie nicht gemeldet; und jede Meldung wartet ein paar Sekunden und verfaellt, wenn in dieser Zeit die
 * Welt wechselt. Ein Wechsel ist, wenn sich das Welt-Objekt des Clients aendert - jede Insel, jede
 * Lobby, jeder Serverwechsel erzeugt ein neues.
 *
 * <p><b>Die Sorten.</b> Welche man hoeren will, steht in den Einstellungen. Die Umberella ist nur fuer
 * Trophy Fishing und hat deshalb einen eigenen Reiter mit eigenem Schalter, Ton und Banner. Ein Name,
 * den keine Sorte kennt, wird nicht gemeldet, aber einmal im Log genannt - so faellt auf, wenn Hypixel
 * ein neues Deployable bringt.
 *
 * <p><b>Ausblenden.</b> Hypixels eigene Zeilen lassen sich verstecken (je Reiter einzeln, aus). Das
 * geht auch bei ausgeschaltetem Alarm, und die Meldung sieht die Zeile vorher.
 */
public final class DeployableReminder {

    /** "Your Plasmaflux Power Orb despawned." - ohne Farbcodes */
    private static final Pattern DESPAWNED = Pattern.compile("^Your (.+) despawned\\.$");
    private static final Pattern REPLACED = Pattern.compile("^Your previous (.+) was removed!$");
    private static final Pattern FLARE_TOO_FAR = Pattern.compile(
            "^Your flare disappeared because you were too far away!$");
    private static final int UNKNOWN_LIMIT = 50;
    /** So lange nach einem Wechsel der Welt kommt keine Meldung */
    static final long QUIET_MILLIS = 10_000L;
    /** So lange wartet eine Meldung, ob gleich die Welt wechselt */
    static final long DELAY_MILLIS = 3_000L;

    /** So lange nach einer Warnung vorher bleibt die Zeile "despawned" der Sorte stumm */
    static final long DEDUPE_MILLIS = 75_000L;
    /** So lange kann dieselbe Sorte nicht zweimal vorher warnen (zwei Orbs, die zugleich auslaufen) */
    static final long EARLY_COOLDOWN_MILLIS = 15_000L;

    private static final Set<String> unknownLogged = new HashSet<>();
    private static final Map<Kind, Long> warnedAt = new EnumMap<>(Kind.class);
    private static final List<Pending> pending = new ArrayList<>();

    /** Das Welt-Objekt vom letzten Tick - ein anderes heisst: Kartenwechsel */
    private static Object lastLevel;
    private static long changedAt = 0L;

    /** Fuer die Zeile, die zweimal durchlaeuft: einmal fuers Melden, einmal fuers Ausblenden */
    private static String lastText;
    private static Hit lastHit;

    /** Die Sorten, aus denen man waehlt. Die Farbe ist die des Banners */
    public enum Kind {
        POWER_ORB(0xFF55FF),
        FLARE(0xFF5555),
        UMBERELLA(0x5555FF),
        LANTERN(0xFFAA00),
        TOTEM(0xAA00AA),
        BLACK_HOLE(0xAAAAAA);

        final int rgb;

        Kind(int rgb) {
            this.rgb = rgb;
        }
    }

    /** Wie es verschwunden ist */
    enum Ending {
        /** Die Zeit laeuft gleich ab - die Warnung vorher, aus dem Staender gelesen */
        SOON,
        /** Die Zeit ist um */
        RAN_OUT,
        /** Ein neues wurde aufgestellt */
        REPLACED,
        /** Der Flare blieb zurueck */
        TOO_FAR
    }

    /**
     * Was gemeldet wird: welche Sorte, unter welchem Namen, wie es verschwindet oder verschwand, und -
     * bei der Warnung vorher - wie viele Sekunden noch bleiben.
     */
    record Hit(Kind kind, String name, Ending ending, int seconds) {
    }

    /** Wie gemeldet wird: die Schalter des Reiters, der zur Meldung gehoert */
    record Style(boolean banner, boolean chatLine, boolean sound, String soundFile, String design) {
    }

    private record Pending(Hit hit, Style style, long fireAt) {
    }

    private DeployableReminder() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DeployableReminder::tick);
    }

    private static DeployablesCategory cfg() {
        return ModConfig.INSTANCE.deployables;
    }

    // ==========================================
    // Zeilen lesen
    // ==========================================

    /**
     * Was die Zeile meldet - oder null, wenn sie mit Deployables nichts zu tun hat oder den Namen
     * keine Sorte kennt.
     */
    static Hit read(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) return null;

        if (FLARE_TOO_FAR.matcher(text).matches()) return new Hit(Kind.FLARE, "Flare", Ending.TOO_FAR, 0);

        Ending ending = Ending.RAN_OUT;
        Matcher m = DESPAWNED.matcher(text);
        if (!m.matches()) {
            m = REPLACED.matcher(text);
            if (!m.matches()) return null;
            ending = Ending.REPLACED;
        }

        String name = m.group(1).trim();
        Kind kind = kindOf(name);
        if (kind == null) {
            if (unknownLogged.size() < UNKNOWN_LIMIT && unknownLogged.add(name)) {
                ShokiMod.LOGGER.info("[Deployables] \"{}\" ({}) - no kind knows that name, so it is not reported", name, ending);
            }
            return null;
        }
        return new Hit(kind, name, ending, 0);
    }

    private static Hit readCached(String message) {
        if (message == null) return null;
        if (!message.equals(lastText)) {
            lastText = message;
            lastHit = read(message);
        }
        return lastHit;
    }

    /** Die Sorte zu einem Namen wie "Plasmaflux Power Orb", "Glacite Lantern" oder "Umberella" */
    static Kind kindOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("power orb") || n.contains("radiant") || n.contains("flux")) return Kind.POWER_ORB;
        if (n.contains("flare")) return Kind.FLARE;
        if (n.contains("umberella") || n.contains("umbrella")) return Kind.UMBERELLA;
        if (n.contains("lantern") || n.contains("will-o")) return Kind.LANTERN;
        if (n.contains("totem")) return Kind.TOTEM;
        if (n.contains("black hole")) return Kind.BLACK_HOLE;
        return null;
    }

    // ==========================================
    // Wer gemeldet wird, und wie
    // ==========================================

    /**
     * Will man von dieser Sorte hoeren? Die Umberella entscheidet ihr eigener Reiter, alle anderen der
     * Hauptschalter und der Haken der Sorte.
     */
    static boolean kindOn(Kind kind) {
        DeployablesCategory c = cfg();
        return switch (kind) {
            case UMBERELLA -> c.umberellaAlert.alert;
            case POWER_ORB -> c.enabled && c.powerOrbs;
            case FLARE -> c.enabled && c.flares;
            case LANTERN -> c.enabled && c.lanterns;
            case TOTEM -> c.enabled && c.totem;
            case BLACK_HOLE -> c.enabled && c.blackHole;
        };
    }

    static Style mainStyle() {
        DeployablesCategory c = cfg();
        return new Style(c.banner, c.chatLine, c.sound, c.soundFile, c.bannerDesign);
    }

    static Style umberellaStyle() {
        UmberellaCategory u = cfg().umberellaAlert;
        return new Style(u.banner, u.chatLine, u.sound, u.soundFile, u.bannerDesign);
    }

    static Style endingsStyle() {
        DeployableEndingsCategory e = cfg().endings;
        return new Style(e.banner, e.chatLine, e.sound, e.soundFile, e.bannerDesign);
    }

    /** Wie diese Zeile gemeldet wird - oder null, wenn sie keine Meldung bekommt */
    static Style styleFor(Hit hit) {
        if (!kindOn(hit.kind())) return null;

        DeployableEndingsCategory e = cfg().endings;
        return switch (hit.ending()) {
            case SOON, RAN_OUT -> hit.kind() == Kind.UMBERELLA ? umberellaStyle() : mainStyle();
            case REPLACED -> e.replacedAlert ? endingsStyle() : null;
            case TOO_FAR -> e.farAwayAlert ? endingsStyle() : null;
        };
    }

    // ==========================================
    // Chat, Kartenwechsel, Tick
    // ==========================================

    /** Eine Chatzeile, ohne Farbcodes */
    public static void onChatMessage(String message) {
        onChatMessage(message, System.currentTimeMillis());
    }

    static void onChatMessage(String message, long now) {
        if (!GameState.Server.isSkyblock()) return;

        // Der Zeitkasten folgt Hypixels Zeilen, auch wenn kein Alarm an ist: Ein ersetztes oder zurueckgelassenes Deployable steht nicht mehr da
        if (FeatureGate.deployableHud()) {
            Hit ended = readCached(message);
            if (ended != null) DeployableTimers.ended(ended.kind(), ended.ending(), now);
        }
        if (!FeatureGate.deployables()) return;

        Hit hit = readCached(message);
        if (hit == null) return;
        Style style = styleFor(hit);
        if (style == null) return;

        if (hit.ending() == Ending.RAN_OUT && warnedRecently(hit.kind(), now)) {
            ShokiMod.LOGGER.info("[Deployables] {} ran out - already warned, no second alert", hit.name());
            return;
        }
        if (now - changedAt < QUIET_MILLIS) {
            ShokiMod.LOGGER.info("[Deployables] {} ({}) right after a map change - no alert", hit.name(), hit.ending());
            return;
        }
        pending.add(new Pending(hit, style, now + DELAY_MILLIS));
    }

    /**
     * Soll Hypixels eigene Zeile verschwinden? Je Reiter ein Schalter, alle aus. Das gilt auch bei
     * ausgeschaltetem Alarm - wer nur die rote Zeile los sein will, braucht ihn nicht.
     */
    public static boolean hides(String message) {
        DeployablesCategory c = cfg();
        if (!c.hideLine && !c.umberellaAlert.hideLine && !c.endings.hideLines) return false;
        if (!GameState.Server.isSkyblock()) return false;

        Hit hit = readCached(message);
        if (hit == null) return false;
        return switch (hit.ending()) {
            case SOON, RAN_OUT -> hit.kind() == Kind.UMBERELLA ? c.umberellaAlert.hideLine : c.hideLine;
            case REPLACED, TOO_FAR -> c.endings.hideLines;
        };
    }

    /** Merkt sich den Wechsel der Welt und verwirft, was noch wartet */
    static void track(Object level, long now) {
        if (level == lastLevel) return;
        lastLevel = level;
        changedAt = now;
        warnedAt.clear();
        DeployableCountdown.reset();
        if (!pending.isEmpty()) {
            ShokiMod.LOGGER.info("[Deployables] map change - {} waiting alert(s) dropped", pending.size());
            pending.clear();
        }
    }

    /** Bringt die Meldungen, deren Wartezeit um ist */
    static void due(long now) {
        if (pending.isEmpty()) return;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now < p.fireAt()) continue;
            it.remove();
            fire(p.hit(), p.style());
        }
    }

    private static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        track(client.level, now);
        due(now);
        DeployableCountdown.tick(client, now);
    }

    /** Die Warnung vorher: gelesen aus dem Staender, kommt sofort und braucht keine Wartezeit */
    static boolean early(Kind kind, String name, int seconds, long now) {
        Long last = warnedAt.get(kind);
        if (last != null && now - last < EARLY_COOLDOWN_MILLIS) return false;

        Hit hit = new Hit(kind, name, Ending.SOON, seconds);
        Style style = styleFor(hit);
        if (style == null) return false;

        warnedAt.put(kind, now);
        fire(hit, style);
        return true;
    }

    private static boolean warnedRecently(Kind kind, long now) {
        Long last = warnedAt.get(kind);
        return last != null && now - last < DEDUPE_MILLIS;
    }

    /** Fuer die Probe: wie viele Meldungen warten gerade */
    static int waiting() {
        return pending.size();
    }

    // ==========================================
    // Meldung
    // ==========================================

    private static void fire(Hit hit, Style style) {
        String headline;
        String where;
        String chatText;
        switch (hit.ending()) {
            case SOON -> {
                headline = "Deployable ending";
                where = hit.seconds() + " s left";
                chatText = " ends in " + hit.seconds() + " s.";
            }
            case REPLACED -> {
                headline = "Deployable replaced";
                where = "a new one took its place";
                chatText = " was replaced by a new one.";
            }
            case TOO_FAR -> {
                headline = "Flare vanished";
                where = "you were too far away";
                chatText = " vanished - you were too far away.";
            }
            default -> {
                headline = "Deployable ran out";
                where = "place a new one";
                chatText = " ran out.";
            }
        }
        if (style.banner()) {
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(style.design()),
                    headline, hit.name(), where, hit.kind().rgb, ItemStack.EMPTY);
        }

        // Chat und Ton brauchen eine Welt
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        if (style.chatLine()) {
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                    .withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(hit.name()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(chatText).withStyle(ChatFormatting.YELLOW)));
        }
        sound(client, style);
        ShokiMod.LOGGER.info("[Deployables] {} - {}", hit.name(), hit.ending());
    }

    /**
     * Der Ton: die gewaehlte Datei, sonst das eingebaute Pling.
     *
     * Fehlt die gewaehlte Datei (umbenannt, geloescht), faellt es aufs Pling zurueck - eine Meldung,
     * die stumm bleibt, ist schlimmer als eine mit dem falschen Ton.
     */
    private static void sound(Minecraft client, Style style) {
        if (!style.sound()) return;

        String file = style.soundFile();
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), DeployableReminder.class);
            return;
        }
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), AlertVolume.factor(), 0.8f);
    }

    /** Knopf in den Einstellungen: einmal vorfuehren, mit einem Plasmaflux Power Orb */
    public static void test() {
        fire(new Hit(Kind.POWER_ORB, "Plasmaflux Power Orb", Ending.SOON, Math.max(1, cfg().warnSeconds)), mainStyle());
    }

    /** Knopf im Reiter Umberella */
    public static void testUmberella() {
        fire(new Hit(Kind.UMBERELLA, "Umberella", Ending.SOON, Math.max(1, cfg().umberellaAlert.warnSeconds)), umberellaStyle());
    }

    /** Knopf im Reiter Other endings: ein Flare, der zu weit weg war */
    public static void testEndings() {
        fire(new Hit(Kind.FLARE, "Flare", Ending.TOO_FAR, 0), endingsStyle());
    }
}
