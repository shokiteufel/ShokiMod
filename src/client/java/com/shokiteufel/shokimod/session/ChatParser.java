package com.shokiteufel.shokimod.session;

import com.shokiteufel.shokimod.data.Critters;
import com.shokiteufel.shokimod.data.Critters.Critter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest aus einer Chatzeile heraus, was gerade passiert ist.
 *
 * Muster wortgleich aus crittermods ChatParser. Der Chat ist die einzige Quelle, die
 * auch die Faenge der Gruppe kennt - im Spiel selbst sieht der Client davon nichts.
 */
public final class ChatParser {

    /** Menge vor dem Namen, etwa "3x Rockmite Shard" */
    private static final Pattern SHARD_AMOUNT = Pattern.compile("(\\d[\\d,]*)x\\s+\\S");
    /** Wer den Fang gemacht hat, aus der LOOT SHARE-Zeile */
    private static final Pattern LOOT_SHARE_CATCHER =
            Pattern.compile("from\\s+(\\w{1,16})\\s+(?:catching|finding)\\b");
    private static final Pattern ATTEMPT =
            Pattern.compile("^You threw a Critter Capsule at the (.+)!$");
    private static final Pattern FAILED =
            Pattern.compile("^The (.+?) (?:escaped your Critter Capsule|dodged your critter capsule)\\b");
    private static final Pattern ENTERED =
            Pattern.compile("^(?:\\[[^]]+]\\s*)?(\\w{1,16}) entered Critter Safari!$");
    /**
     * Von Spielern geschriebene Zeilen. Die muessen aussortiert werden, sonst zaehlt
     * jemand, der "CAPTURE! Rockmite" in den Partychat schreibt, als Fang.
     */
    private static final Pattern PLAYER_SAID = Pattern.compile(
            "^(?:Party|Guild|Officer|Co-op|Team|Friend) >.*"
                    + "|^(?:From|To) .*"
                    + "|^(?:\\[(?!NPC]|MOB])[^]]+]\\s*)?\\w{1,16}: .*");

    public enum Type { OWN_CATCH, SHARED_CATCH, ATTEMPT, FAILED, ENTERED_SAFARI }

    /**
     * @param catcher wer gefangen hat; null bedeutet man selbst
     * @param shards  wie viele Shards die Zeile nennt
     */
    public record Event(Type type, Critter critter, String catcher, int shards, boolean sparkling) {
    }

    private ChatParser() {
    }

    public static boolean playerSaid(String line) {
        return PLAYER_SAID.matcher(line).matches();
    }

    /** Farbcodes und eine angehaengte Stueckzahl entfernen */
    public static String clean(String raw) {
        String text = raw.replaceAll("§.", "").trim();
        text = text.replaceAll("\\s*(?:\\(\\s*[x×]?\\s*\\d+\\s*\\)|\\[\\s*[x×]?\\s*\\d+\\s*])$", "");
        return text.trim();
    }

    /**
     * @param selfName eigener Spielername, um den Safari-Eintritt von dem anderer zu trennen
     * @return das Ereignis, oder null wenn die Zeile nichts damit zu tun hat
     */
    public static Event parse(String text, String selfName) {
        if (text.startsWith("CAPTURE!")) {
            Critter critter = Critters.findIn(text);
            if (critter == null) return null;
            return new Event(Type.OWN_CATCH, critter, null,
                    shardAmount(text), text.contains("SPARKLING"));
        }

        if (text.startsWith("LOOT SHARE!")) {
            Critter critter = Critters.findIn(text);
            if (critter == null) return null;
            Matcher catcher = LOOT_SHARE_CATCHER.matcher(text);
            if (!catcher.find()) return null;
            return new Event(Type.SHARED_CATCH, critter, catcher.group(1),
                    shardAmount(text), text.contains("SPARKLING"));
        }

        Matcher attempt = ATTEMPT.matcher(text);
        if (attempt.matches()) {
            Critter critter = Critters.byName(attempt.group(1));
            return critter == null ? null : new Event(Type.ATTEMPT, critter, null, 0, false);
        }

        Matcher failed = FAILED.matcher(text);
        if (failed.find()) {
            Critter critter = Critters.byName(failed.group(1));
            return critter == null ? null : new Event(Type.FAILED, critter, null, 0, false);
        }

        Matcher entered = ENTERED.matcher(text);
        if (entered.matches() && selfName != null && selfName.equals(entered.group(1))) {
            return new Event(Type.ENTERED_SAFARI, null, selfName, 0, false);
        }

        return null;
    }

    private static int shardAmount(String text) {
        Matcher matcher = SHARD_AMOUNT.matcher(text);
        if (!matcher.find()) return 1;
        try {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
