package com.shokiteufel.shokimod.handler;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DeployableHudCategory;
import com.shokiteufel.shokimod.handler.DeployableReminder.Kind;
import com.shokiteufel.shokimod.util.DeployableLog;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die Restzeit der Deployables in der Naehe, damit die Warnung vor dem Ablauf kommen kann - und
 * nicht erst danach.
 *
 * <p>Die Chatzeile "Your ... despawned." kommt, wenn das Deployable schon weg ist. Eine Warnung davor
 * braucht die Restzeit, und die steht nur in der Welt. Wie die anderen Mods sie lesen (SkyHanni,
 * Riccio's Fishing Utils, SkyCubed - Texte und Rechenwege dort nachgesehen):
 * <ul>
 * <li>Power Orb, Umberella, Laternen, Black Hole: der Ruestungsstaender traegt den Namen mit der
 * Restzeit, "Plasmaflux 28s", "Umberella 298s".</li>
 * <li>Totem of Corruption: drei Staender uebereinander - "Totem of Corruption", "Remaining: 2m 30s" und
 * "Owner: Name".</li>
 * <li>Flare: ein Staender mit einem Kopf (die Textur sagt, welcher), ohne Zeit im Namen. Die Zeit
 * rechnet SkyHanni aus dem Alter des Staenders: drei Minuten abzueglich der Ticks seit dem Erscheinen.</li>
 * </ul>
 *
 * <p><b>Welches zaehlt.</b> Der Staender sagt nicht, wem das Deployable gehoert - nur das Totem nennt
 * seinen Besitzer. Bis 1.17.18 wurde deshalb vor jedem Deployable gewarnt, in dessen Reichweite man steht;
 * wer mit fuenf Leuten zusammen angelt, bekam die Warnung fuenffach. Jetzt zaehlt (Einstellung "Only my own",
 * Standard an) nur, was man selbst aufgestellt hat. Das erkennt man am Aufstellen: Ein Staender, der kurz
 * nach dem eigenen Rechtsklick mit einem Deployable in der Hand in der Naehe zum ersten Mal auftaucht, ist
 * der eigene (siehe {@link #placedByMe}). Was man nicht hat aufstellen sehen, gehoert nicht dazu. Beim
 * Totem zaehlt wie bisher nur das eigene, wenn der Besitzer dabeisteht.
 *
 * <p><b>Nicht beim Vorbeilaufen.</b> Gewarnt wird nur, wenn man den Staender herunterzaehlen sah: Wer an
 * einem Deployable mit fuenf Sekunden Rest vorbeikommt, bekommt keine Warnung. Beim Flare gilt das Alter
 * seit dem Erscheinen im eigenen Client - wer spaeter hinzukommt, sieht zu viel Restzeit (das hat
 * SkyHanni genauso), und die Warnung kommt dann entsprechend spaet.
 */
public final class DeployableCountdown {

    /** "Plasmaflux 28s", "Umberella 298s" - der Name, ein Leerzeichen, die Sekunden */
    private static final Pattern STAND = Pattern.compile(
            "^(Radiant|Mana Flux|Overflux|Plasmaflux|Dwarven Lantern|Mithril Lantern|Titanium Lantern"
                    + "|Glacite Lantern|Will-o'-wisp|Black Hole|Umberella) (\\d+)s$");
    /** "Remaining: 2m 30s" oder "Remaining: 5s" */
    private static final Pattern REMAINING = Pattern.compile("^Remaining: (?:(\\d+)m )?(\\d+)s$");
    private static final Pattern OWNER = Pattern.compile("^Owner: (.+)$");
    private static final String TOTEM_NAME = "Totem of Corruption";
    private static final Pattern COLOUR_CODE = Pattern.compile("§[0-9a-fk-or]");
    /** Woran man einen Staendertext erkennt, der zu einem Deployable gehoert, aber nicht gelesen wird */
    private static final Pattern LOOKS_LIKE = Pattern.compile(
            "(?i)power orb|flux|radiant|umberella|umbrella|lantern|totem|black hole|flare");

    /** So lange nach einem Rechtsklick gilt ein neu auftauchender Staender als von mir aufgestellt */
    private static final long PLACE_WINDOW_MILLIS = 3_500L;
    /** Wie nah am Spieler ein Staender stehen muss, damit er beim Aufstellen als der eigene gilt */
    private static final double PLACE_RANGE = 8.0;
    /**
     * Ein Flare wird in den Himmel geschossen: sein Staender taucht gut eine Sekunde nach dem Rechtsklick auf und steht dann hoch
     * ueber dem Spieler (gemessen: 10,3 Bloecke Abstand, zuerst gesehen 1,65 s nach dem Rechtsklick). Fuer ihn zaehlt deshalb der
     * Abstand auf dem Boden (wie bei den anderen), und nach oben darf er so weit weg sein.
     */
    private static final double FLARE_SHOT_HEIGHT = 40.0;
    /**
     * Ohne erkennbares Deployable in der Hand zaehlt nur, was fast an der eigenen Stelle und fast sofort erscheint.
     * Das faengt Deployables ab, deren Item-Kennung die Liste unten nicht kennt - und ist eng genug, dass der
     * Rechtsklick mit der Angel (mehrmals je Minute) nicht jeden Staender neben einem zum eigenen macht.
     */
    private static final long PLACE_STRICT_WINDOW_MILLIS = 1_500L;
    private static final double PLACE_STRICT_RANGE = 2.5;
    /** So lange bleibt ein eigener Staender gemerkt, nachdem man ihn zuletzt sah (laenger als jedes Deployable haelt) */
    private static final long MINE_KEEP_MILLIS = 15L * 60_000L;
    /** Woran man ein Deployable-Item erkennt (SkyBlock-Kennung des Items in der Hand) */
    private static final Pattern DEPLOYABLE_ITEM = Pattern.compile(
            "POWER_ORB|FLARE|UMBERELLA|UMBRELLA|LANTERN|WISP|BLACK_HOLE|TOTEM|RADIANT|FLUX");
    private static final int DECISION_LOG_LIMIT = 80;

    private static final int SCAN_TICKS = 10;
    private static final int FLARE_SECONDS = 180;
    private static final double FLARE_RANGE = 40.0;
    private static final double TOTEM_RANGE = 16.0;
    /** Zusammengehoerige Staender des Totems stehen fast uebereinander */
    private static final double TOTEM_PART_REACH = 1.5;
    private static final double TOTEM_PART_HEIGHT = 3.5;
    private static final int LOG_LIMIT = 30;
    private static final int CACHE_LIMIT = 4000;

    /** Die Koepfe der drei Flares (der Hash in der Textur-URL), wie sie SkyblockAddons und SkyHanni fuehren */
    private static final String WARNING_FLARE = "22e2bf6c1ec330247927ba63479e5872ac66b06903c86c82b52dac9f1c971458";
    private static final String ALERT_FLARE = "9d2bf9864720d87fd06b84efa80b795c48ed539b16523c3b1f1990b40c003f6b";
    private static final String SOS_FLARE = "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b";

    /** Was ein Staender gerade sagt: welche Sorte, unter welchem Namen, wie viele Sekunden, wie weit reicht es */
    record Reading(Kind kind, String name, int seconds, double range) {
    }

    private static final class Tracked {
        /** Man hat es herunterzaehlen sehen, bevor die Warnschwelle erreicht war */
        final boolean fresh;
        boolean warned;

        Tracked(boolean fresh) {
            this.fresh = fresh;
        }
    }

    private static final Map<Integer, Tracked> seen = new HashMap<>();
    /** Staender-Nummer -> Name des Flares, oder "" fuer "kein Flare" - der Kopf wird nur einmal entschluesselt */
    private static final Map<Integer, String> heads = new HashMap<>();
    private static final Set<String> notUnderstood = new HashSet<>();
    private static int ticks = 0;
    /** Staender, ueber die schon entschieden ist, ob sie meine sind (entschieden wird beim ersten Sehen) */
    private static final Set<Integer> known = new HashSet<>();
    /** Meine Staender, mit dem Zeitpunkt, zu dem ich sie zuletzt sah */
    private static final Map<Integer, Long> mine = new HashMap<>();
    /** Alle Deployable-Staender dieses Durchgangs - damit "known" nicht waechst */
    private static final Set<Integer> scanned = new HashSet<>();
    private static int decisionsLogged = 0;

    private DeployableCountdown() {
    }

    // ==========================================
    // Staendertexte lesen
    // ==========================================

    /** Ein Staender mit Namen und Restzeit - oder null */
    static Reading parse(String text) {
        Matcher m = STAND.matcher(text);
        if (!m.matches()) return null;

        String shortName = m.group(1);
        Kind kind = DeployableReminder.kindOf(shortName);
        if (kind == null) return null;
        int seconds;
        try {
            seconds = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
        return new Reading(kind, label(shortName, kind), seconds, rangeOf(shortName, kind));
    }

    /** Der Name, wie ihn Hypixel im Inventar fuehrt: "Plasmaflux" ist ein Plasmaflux Power Orb */
    static String label(String shortName, Kind kind) {
        return kind == Kind.POWER_ORB ? shortName + " Power Orb" : shortName;
    }

    /** Wie weit ein Deployable wirkt - die Werte, die SkyHanni fuehrt */
    static double rangeOf(String shortName, Kind kind) {
        return switch (kind) {
            case POWER_ORB -> shortName.equals("Plasmaflux") ? 20.0 : 18.0;
            case LANTERN, UMBERELLA -> 30.0;
            case BLACK_HOLE -> 20.0;
            case FLARE -> FLARE_RANGE;
            case TOTEM -> TOTEM_RANGE;
        };
    }

    /** "Remaining: 2m 30s" in Sekunden, sonst -1 */
    static int remainingSeconds(String text) {
        Matcher m = REMAINING.matcher(text);
        if (!m.matches()) return -1;
        try {
            int minutes = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
            return minutes * 60 + Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Welcher Flare steckt in dieser Textur? Der Wert ist das Base64-JSON der Skin-Eigenschaft */
    static String flareFromTexture(String base64) {
        if (base64 == null || base64.isEmpty()) return "";
        String json;
        try {
            json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
        if (json.contains(WARNING_FLARE)) return "Warning Flare";
        if (json.contains(ALERT_FLARE)) return "Alert Flare";
        if (json.contains(SOS_FLARE)) return "SOS Flare";
        return "";
    }

    // ==========================================
    // Wann gewarnt wird
    // ==========================================

    /**
     * Ein Messwert eines Staenders. Liefert true, wenn jetzt zu warnen ist - einmal je Staender.
     *
     * Gewarnt wird nur, wenn man den Staender zum ersten Mal sah, solange noch mehr als die Schwelle
     * uebrig war: Wer an einem Deployable mit fuenf Sekunden Rest vorbeilaeuft, soll nichts hoeren.
     */
    static boolean observe(int id, Kind kind, String name, int remaining, int warnSeconds) {
        if (warnSeconds <= 0) return false;

        Tracked t = seen.get(id);
        if (t == null) {
            t = new Tracked(remaining > warnSeconds);
            seen.put(id, t);
            ShokiMod.LOGGER.info("[Deployables] watching {} ({}) - {} s left{}", name, kind, remaining,
                    t.fresh ? "" : " (already inside the warning time, no warning)");
        }
        if (t.warned || !t.fresh || remaining > warnSeconds) return false;
        t.warned = true;
        return true;
    }

    /** Wie viele Sekunden vorher fuer diese Sorte gewarnt wird, 0 = gar nicht */
    static int warnSeconds(Kind kind) {
        return kind == Kind.UMBERELLA ? cfgUmberella() : cfgMain();
    }

    private static int cfgMain() {
        return ModConfig.INSTANCE.deployables.warnSeconds;
    }

    private static int cfgUmberella() {
        return ModConfig.INSTANCE.deployables.umberellaAlert.warnSeconds;
    }

    static void reset() {
        DeployableTimers.reset();
        seen.clear();
        heads.clear();
        known.clear();
        mine.clear();
        scanned.clear();
        textsLogged.clear();
    }

    static int watching() {
        return seen.size();
    }

    // ==========================================
    // Tick: die Staender der Umgebung durchgehen
    // ==========================================

    static void tick(Minecraft client, long now) {
        // Jeden Tick: neue Zahlen der Staender, die der Zeitkasten kennt - er soll gleichmaessig herunterlaufen
        DeployableTimers.tick(client, now);
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;
        if (client.level == null || client.player == null) return;
        boolean warnings = FeatureGate.deployables() && (cfgMain() > 0 || cfgUmberella() > 0);
        boolean panel = FeatureGate.deployableHud();
        boolean log = DeployableLog.enabled();
        if (!GameState.Server.isSkyblock() || (!warnings && !panel)) {
            if (!seen.isEmpty()) seen.clear();
            if (log) logStatus("skipped: skyblock=" + GameState.Server.isSkyblock() + " warnings=" + warnings + " panel=" + panel, "", now);
            return;
        }

        Vec3 me = client.player.position();
        String myName = client.player.getName().getString();
        Set<Integer> present = new HashSet<>();
        scanned.clear();
        List<Part> remaining = new ArrayList<>();
        List<Part> totems = new ArrayList<>();
        List<Part> owners = new ArrayList<>();
        int stands = 0;
        int named = 0;
        int understood = 0;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            stands++;

            Component customName = stand.getCustomName();
            if (customName != null) {
                named++;
                String text = COLOUR_CODE.matcher(customName.getString()).replaceAll("").trim();
                Reading reading = parse(text);
                if (reading != null) {
                    understood++;
                    watch(stand, reading, me, present, now, warnings, panel);
                    continue;
                }
                if (remainingSeconds(text) >= 0) {
                    remaining.add(new Part(stand, text));
                    continue;
                }
                if (text.equals(TOTEM_NAME)) {
                    totems.add(new Part(stand, text));
                    continue;
                }
                if (OWNER.matcher(text).matches()) {
                    owners.add(new Part(stand, text));
                    continue;
                }
                logNotUnderstood(text);
                if (log) logTextNearUse(stand, text, me, now);
            }

            Reading flare = flare(stand);
            if (flare != null) {
                understood++;
                watch(stand, flare, me, present, now, warnings, panel);
            }
        }

        // Das Totem: "Remaining" gehoert zu dem Totem-Staender, der fast darunter oder darueber steht
        for (Part time : remaining) {
            if (next(time, totems) == null) continue;
            Part owner = next(time, owners);
            Reading totem = new Reading(Kind.TOTEM, TOTEM_NAME, remainingSeconds(time.text), TOTEM_RANGE);
            if (owner != null && !OWNER.matcher(owner.text).replaceFirst("$1").trim().equalsIgnoreCase(myName)) {
                // Das Totem eines anderen: keine Warnung, aber der Zeitkasten im Modus "alle in der Naehe" zeigt es
                if (panel) DeployableTimers.seen(time.stand, totem, false, Math.sqrt(time.stand.distanceToSqr(me)), now);
                continue;
            }
            watch(time.stand, totem, me, present, now, warnings, panel);
        }

        seen.keySet().retainAll(present);
        // Wer nicht mehr zu sehen ist, wird vergessen - ausser meinen: Sie kommen wieder, wenn man zurueckgeht
        known.retainAll(scanned);
        mine.values().removeIf(lastSeen -> now - lastSeen > MINE_KEEP_MILLIS);

        if (log) {
            DeployableHudCategory hud = ModConfig.INSTANCE.deployables.hud;
            long sinceUse = now - ParticleTracer.lastUseAt();
            logStatus(String.format(Locale.ROOT, "panel=%s mode=%s warnings=%s stands=%d named=%d read=%d totem parts=%d/%d/%d timers=%d rows=%d",
                            panel, hud.mode, warnings, stands, named, understood, totems.size(), remaining.size(), owners.size(),
                            DeployableTimers.count(), DeployableTimers.rows(now).size()),
                    String.format(Locale.ROOT, " | last right-click: %s, %s | %s", ParticleTracer.lastUseItem(),
                            ParticleTracer.lastUseAt() == 0L ? "never" : (sinceUse / 1000) + " s ago", DeployableTimers.describe(now)), now);
        }
    }

    // ==========================================
    // Protokoll (Einstellung "Deployable log")
    // ==========================================

    private static String lastStatus = "";
    private static long lastStatusAt = 0L;
    /** Texte in der Naehe eines Rechtsklicks, die schon im Protokoll stehen */
    private static final Set<String> textsLogged = new HashSet<>();
    private static final long STATUS_REPEAT_MILLIS = 15_000L;
    /** So lange nach einem Rechtsklick mit einem Deployable wird jeder Staendertext nahe bei mir ins Protokoll geschrieben */
    private static final long TEXT_WINDOW_MILLIS = 6_000L;
    private static final double TEXT_RANGE = 5.0;
    private static final int TEXT_LIMIT = 200;

    /** Eine Zeile mit dem Stand der Dinge: nur wenn sich die Zahlen geaendert haben, sonst alle fuenfzehn Sekunden */
    private static void logStatus(String signature, String extra, long now) {
        if (signature.equals(lastStatus) && now - lastStatusAt < STATUS_REPEAT_MILLIS) return;
        lastStatus = signature;
        lastStatusAt = now;
        DeployableLog.line("STATUS", signature + extra);
    }

    /**
     * Staendertexte dicht bei mir, kurz nachdem ich ein Deployable benutzt habe, auch wenn sie nicht gelesen werden: Ein Power Orb
     * besteht aus mehreren Staendern, und gerade der, dessen Text die Mod nicht kennt, soll im Protokoll stehen.
     */
    private static void logTextNearUse(ArmorStand stand, String text, Vec3 me, long now) {
        if (text.isEmpty() || textsLogged.size() >= TEXT_LIMIT) return;
        long since = now - ParticleTracer.lastUseAt();
        if (since < 0 || since > TEXT_WINDOW_MILLIS || !DEPLOYABLE_ITEM.matcher(String.valueOf(ParticleTracer.lastUseItem())).find()) return;
        double distance = Math.sqrt(stand.distanceToSqr(me));
        if (distance > TEXT_RANGE || !textsLogged.add(text)) return;
        DeployableLog.line("TEXT", String.format(Locale.ROOT, "near stand, not read: \"%s\" (%.1f blocks away, %d ms after the right-click)", text, distance, since));
    }

    /** Ein Rechtsklick mit diesem Item (SkyBlock-Kennung) in der Haupthand - nur Deployables kommen ins Protokoll */
    static void noteUse(String item) {
        if (!DeployableLog.enabled() || item == null || !DEPLOYABLE_ITEM.matcher(item).find()) return;
        DeployableLog.line("USE", "right-click with " + item + " (panel=" + FeatureGate.deployableHud() + ", skyblock=" + GameState.Server.isSkyblock() + ")");
    }

    private record Part(ArmorStand stand, String text) {
    }

    /** Der naechste Staender aus der Liste, der zum selben Gebilde gehoert - oder null */
    private static Part next(Part of, List<Part> candidates) {
        Part best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Part c : candidates) {
            double dx = c.stand.getX() - of.stand.getX();
            double dz = c.stand.getZ() - of.stand.getZ();
            double dy = Math.abs(c.stand.getY() - of.stand.getY());
            double flat = dx * dx + dz * dz;
            if (flat > TOTEM_PART_REACH * TOTEM_PART_REACH || dy > TOTEM_PART_HEIGHT) continue;
            if (flat < bestDistance) {
                bestDistance = flat;
                best = c;
            }
        }
        return best;
    }

    private static void watch(ArmorStand stand, Reading reading, Vec3 me, Set<Integer> present, long now,
                              boolean warnings, boolean panel) {
        // Wem es gehoert, wird einmal beim Auftauchen entschieden - fuer die Warnung ("Only my own") wie fuer den Zeitkasten
        boolean mine = owned(stand, reading, me, now);
        if (panel && DeployableTimers.seen(stand, reading, mine, Math.sqrt(stand.distanceToSqr(me)), now) && !mine) {
            // Der Ersatz fuer einen meiner Staender, den Hypixel neu gesetzt hat: er ist auch meiner, die Warnung soll ihm weiter folgen
            DeployableCountdown.mine.put(stand.getId(), now);
            mine = true;
        }
        if (!warnings) return;
        if (ModConfig.INSTANCE.deployables.ownOnly && !mine) return;
        if (stand.distanceToSqr(me) > reading.range() * reading.range()) return;

        present.add(stand.getId());
        int warn = warnSeconds(reading.kind());
        if (observe(stand.getId(), reading.kind(), reading.name(), reading.seconds(), warn)) {
            DeployableReminder.early(reading.kind(), reading.name(), reading.seconds(), now);
        }
    }

    /**
     * Gehoert der Staender mir? Entschieden wird einmal, wenn er zum ersten Mal auftaucht - unabhaengig davon, ob die Warnung nur die
     * eigenen will ("Only my own"): Auch der Zeitkasten im Modus "nur meine" fragt danach.
     *
     * Beim Totem, das den Besitzer nennt und anderswo geprueft wird, zaehlt jeder, der hier ankommt.
     */
    private static boolean owned(ArmorStand stand, Reading reading, Vec3 me, long now) {
        if (reading.kind() == Kind.TOTEM) return true;
        int id = stand.getId();
        scanned.add(id);
        if (mine.containsKey(id)) {
            mine.put(id, now);
            return true;
        }
        if (!known.add(id)) return false;
        boolean placed = placedByMe(stand, reading, me, now);
        if (placed) mine.put(id, now);
        return placed;
    }

    /**
     * Ist dieser neu aufgetauchte Staender von mir aufgestellt?
     *
     * Ja, wenn mein letzter Rechtsklick (Haupthand) hoechstens dreieinhalb Sekunden her ist und der Staender nah
     * steht - mit einem Deployable-Item in der Hand bis acht Bloecke weit, sonst nur, wenn er fast sofort und fast
     * an meiner Stelle erscheint. Jede Entscheidung steht, mit den Zahlen, im Log: Daran sieht man, wenn die
     * Item-Liste ein Deployable nicht kennt.
     */
    static boolean placedByMe(ArmorStand stand, Reading reading, Vec3 me, long now) {
        long since = now - ParticleTracer.lastUseAt();
        String item = ParticleTracer.lastUseItem();
        double distance = Math.sqrt(stand.distanceToSqr(me));
        boolean deployableItem = item != null && DEPLOYABLE_ITEM.matcher(item).find();
        boolean flareShot = deployableItem && reading.kind() == Kind.FLARE && item.contains("FLARE");
        double flat = Math.hypot(stand.getX() - me.x, stand.getZ() - me.z);
        double up = stand.getY() - me.y;
        boolean near = flareShot ? flat <= PLACE_RANGE && Math.abs(up) <= FLARE_SHOT_HEIGHT : distance <= PLACE_RANGE;
        boolean placed = since >= 0 && since <= PLACE_WINDOW_MILLIS
                && ((deployableItem && near)
                || (since <= PLACE_STRICT_WINDOW_MILLIS && distance <= PLACE_STRICT_RANGE));
        // Nur was nah genug zum Aufstellen war, ist ein Eintrag wert - fremde Deployables in der Ferne sind Rauschen
        if ((distance <= 12.0 || flareShot) && decisionsLogged < DECISION_LOG_LIMIT) {
            decisionsLogged++;
            ShokiMod.LOGGER.info("[Deployables] {} ({}): {} - right-click {} ms ago with {}, {} blocks away",
                    reading.name(), reading.kind(), placed ? "MINE" : "not mine", since, item,
                    String.format(Locale.ROOT, "%.1f", distance));
        }
        if (DeployableLog.enabled()) {
            DeployableLog.line("OWNER", String.format(Locale.ROOT, "id=%d %s (%s): %s - right-click %s with %s, %.1f blocks away (%.1f on the ground, %+.1f up; deployable item: %s; window %d ms, range %.0f%s - or %d ms / %.1f when the item is not known)",
                    stand.getId(), reading.name(), reading.kind(), placed ? "MINE" : "not mine",
                    ParticleTracer.lastUseAt() == 0L ? "never" : since + " ms ago", item, distance, flat, up, deployableItem,
                    PLACE_WINDOW_MILLIS, PLACE_RANGE, flareShot ? " on the ground, up to " + (int) FLARE_SHOT_HEIGHT + " up (flare)" : "",
                    PLACE_STRICT_WINDOW_MILLIS, PLACE_STRICT_RANGE));
        }
        return placed;
    }

    /** Ein Flare: Kopf mit der Textur eines der drei, Zeit aus dem Alter des Staenders */
    private static Reading flare(ArmorStand stand) {
        ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty() || !head.is(Items.PLAYER_HEAD)) return null;

        String name = heads.get(stand.getId());
        if (name == null) {
            name = flareFromTexture(texture(head));
            if (heads.size() < CACHE_LIMIT) heads.put(stand.getId(), name);
        }
        if (name.isEmpty()) return null;

        int left = Math.max(0, FLARE_SECONDS - stand.tickCount / 20);
        return new Reading(Kind.FLARE, name, left, FLARE_RANGE);
    }

    private static String texture(ItemStack head) {
        ResolvableProfile profile = head.get(DataComponents.PROFILE);
        if (profile == null) return "";
        GameProfile gp = profile.partialProfile();
        if (gp == null) return "";
        for (Property property : gp.properties().get("textures")) {
            return property.value();
        }
        return "";
    }

    /** Ein Staendertext, der nach Deployable klingt und nicht gelesen wird - einmal im Log, damit man es merkt */
    private static void logNotUnderstood(String text) {
        if (text.isEmpty() || !LOOKS_LIKE.matcher(text).find()) return;
        if (notUnderstood.size() < LOG_LIMIT && notUnderstood.add(text.toLowerCase(Locale.ROOT))) {
            ShokiMod.LOGGER.info("[Deployables] stand text not understood: \"{}\"", text);
            DeployableLog.line("TEXT", "looks like a deployable but is not read: \"" + text + "\"");
        }
    }
}
