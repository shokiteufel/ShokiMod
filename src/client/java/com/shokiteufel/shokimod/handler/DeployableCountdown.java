package com.shokiteufel.shokimod.handler;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.DeployableReminder.Kind;

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
        seen.clear();
        heads.clear();
        known.clear();
        mine.clear();
        scanned.clear();
    }

    static int watching() {
        return seen.size();
    }

    // ==========================================
    // Tick: die Staender der Umgebung durchgehen
    // ==========================================

    static void tick(Minecraft client, long now) {
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;
        if (client.level == null || client.player == null) return;
        if (!FeatureGate.deployables() || !GameState.Server.isSkyblock()
                || (cfgMain() <= 0 && cfgUmberella() <= 0)) {
            if (!seen.isEmpty()) seen.clear();
            return;
        }

        Vec3 me = client.player.position();
        String myName = client.player.getName().getString();
        Set<Integer> present = new HashSet<>();
        scanned.clear();
        List<Part> remaining = new ArrayList<>();
        List<Part> totems = new ArrayList<>();
        List<Part> owners = new ArrayList<>();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;

            Component customName = stand.getCustomName();
            if (customName != null) {
                String text = COLOUR_CODE.matcher(customName.getString()).replaceAll("").trim();
                Reading reading = parse(text);
                if (reading != null) {
                    watch(stand, reading, me, present, now);
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
            }

            Reading flare = flare(stand);
            if (flare != null) watch(stand, flare, me, present, now);
        }

        // Das Totem: "Remaining" gehoert zu dem Totem-Staender, der fast darunter oder darueber steht
        for (Part time : remaining) {
            if (next(time, totems) == null) continue;
            Part owner = next(time, owners);
            if (owner != null && !OWNER.matcher(owner.text).replaceFirst("$1").trim().equalsIgnoreCase(myName)) continue;
            watch(time.stand, new Reading(Kind.TOTEM, TOTEM_NAME, remainingSeconds(time.text), TOTEM_RANGE), me, present, now);
        }

        seen.keySet().retainAll(present);
        // Wer nicht mehr zu sehen ist, wird vergessen - ausser meinen: Sie kommen wieder, wenn man zurueckgeht
        known.retainAll(scanned);
        mine.values().removeIf(lastSeen -> now - lastSeen > MINE_KEEP_MILLIS);
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

    private static void watch(ArmorStand stand, Reading reading, Vec3 me, Set<Integer> present, long now) {
        if (!isMine(stand, reading, me, now)) return;
        if (stand.distanceToSqr(me) > reading.range() * reading.range()) return;

        present.add(stand.getId());
        int warn = warnSeconds(reading.kind());
        if (observe(stand.getId(), reading.kind(), reading.name(), reading.seconds(), warn)) {
            DeployableReminder.early(reading.kind(), reading.name(), reading.seconds(), now);
        }
    }

    /**
     * Gehoert der Staender mir? Entschieden wird einmal, wenn er zum ersten Mal auftaucht.
     *
     * Aus ("Only my own" aus) und beim Totem (das den Besitzer nennt und anderswo geprueft wird) zaehlt jeder.
     */
    private static boolean isMine(ArmorStand stand, Reading reading, Vec3 me, long now) {
        if (!ModConfig.INSTANCE.deployables.ownOnly || reading.kind() == Kind.TOTEM) return true;
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
        boolean placed = since >= 0 && since <= PLACE_WINDOW_MILLIS
                && ((deployableItem && distance <= PLACE_RANGE)
                || (since <= PLACE_STRICT_WINDOW_MILLIS && distance <= PLACE_STRICT_RANGE));
        // Nur was nah genug zum Aufstellen war, ist ein Eintrag wert - fremde Deployables in der Ferne sind Rauschen
        if (distance <= 12.0 && decisionsLogged < DECISION_LOG_LIMIT) {
            decisionsLogged++;
            ShokiMod.LOGGER.info("[Deployables] {} ({}): {} - right-click {} ms ago with {}, {} blocks away",
                    reading.name(), reading.kind(), placed ? "MINE" : "not mine", since, item,
                    String.format(Locale.ROOT, "%.1f", distance));
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
        }
    }
}
