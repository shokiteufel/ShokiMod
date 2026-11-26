package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.CompactDoubleHookPosition;
import com.shokiteufel.shokimod.data.ModConfig.FishingCategory;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;

import java.util.regex.Pattern;

/**
 * Fishing > Shorten Fishing Message und Compact Double Hook: die zwei Chatzeilen, mit denen Hypixel einen Fang meldet.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code SeaCreatureManager}). Von dort stammt die Regel: Bei einem Double Hook kommt die Zeile "It's a Double Hook! Woot woot!"
 * VOR der Zeile des Meeresbewohners. Mit "Compact Double Hook" wird sie ausgeblendet und der Zeitpunkt gemerkt; die naechste Fang-Zeile eines Meeresbewohners
 * innerhalb von 100 Millisekunden bekommt "DOUBLE HOOK! " in Fettgelb davor (Left) oder " DOUBLE HOOK!" dahinter (Right). "Shorten Fishing Message" ersetzt die
 * Fang-Zeile durch "You caught a/an <Name>!" (blau, der Name in der Farbe des Meeresbewohners; a oder an nach dem Anfangsbuchstaben). Der gemerkte Zeitpunkt wird nach
 * einer Fang-Zeile vergessen, nicht schon bei einer Zeile, die keine ist (dazwischen kommen Zeilen wie die der Auto-Pet-Regel oder einer Donnerflasche).
 *
 * Die Umsetzung ist eigene Arbeit: Java, die Zeilen der Meeresbewohner stehen schon in {@link SeaCreatures} (der Satz zu jedem Fang, aus Feesh; SkyHannis Tabelle liegt
 * in seinem Repo und gibt es hier nicht), die Farbe des Namens ist die Seltenheitsfarbe dieser Tabelle, alle Muster laufen gegen den Klartext der Zeile, die neue Zeile
 * wird Stueck fuer Stueck ({@link StyledText}) gebaut, die Uhr ist die des Chat-Wegs. Zwei Abweichungen von SkyHanni: Ein Name mit "The" davor ("The Loch Emperor") bekommt
 * keinen Artikel ("You caught The Loch Emperor!" statt "a The Loch Emperor"), und die Double-Hook-Zeile wird nur gemerkt, wenn Compact Double Hook an ist.
 */
final class SeaCreatureLines {

    /** So lange nach der Double-Hook-Zeile gehoert eine Fang-Zeile noch zum Double Hook */
    static final long DOUBLE_HOOK_WINDOW_MS = 100L;

    private static final Pattern DOUBLE_HOOK = Pattern.compile("It's a Double Hook!(?: Woot woot!)?");
    private static final String BEFORE = "§e§lDOUBLE HOOK! ";
    private static final String AFTER = " §e§lDOUBLE HOOK!";
    /** Kein Double Hook gemerkt (liegt weiter zurueck als jedes Zeitfenster) */
    private static final long NONE = Long.MIN_VALUE / 2;

    private long doubleHookAt = NONE;

    /** Neue Welt: ein gemerkter Double Hook gilt nicht mehr */
    void reset() {
        doubleHookAt = NONE;
    }

    /** Ist ein Double Hook gemerkt, der noch zur Zeile gehoert, die zur Zeit {@code now} kommt? */
    boolean doubleHookRecent(long now) {
        long since = now - doubleHookAt;
        return since >= 0L && since <= DOUBLE_HOOK_WINDOW_MS;
    }

    /**
     * Erste Stufe: die Zeile "It's a Double Hook!" - bei "Compact Double Hook" ausblenden und den Zeitpunkt merken. Null heisst, die Zeile ist keine, oder der
     * Schalter ist aus (dann bleibt sie stehen, und es wird nichts gemerkt).
     */
    ChatVerdict doubleHookLine(Incoming in, FishingCategory f, long now) {
        if (!f.compactDoubleHook) return null;
        if (!DOUBLE_HOOK.matcher(in.plain.strip()).matches()) return null;
        doubleHookAt = now;
        return ChatVerdict.HIDE;
    }

    /**
     * Zweite Stufe: die Fang-Zeile eines Meeresbewohners, gekuerzt und/oder mit dem Double-Hook-Zusatz. Null heisst, die Zeile bleibt, wie sie ist (keine Fang-Zeile
     * oder nichts zu aendern).
     */
    StyledText catchLine(StyledText cur, FishingCategory f, long now) {
        SeaCreatures.Creature creature = SeaCreatures.caughtIn(cur.plain().strip());
        if (creature == null) return null;
        boolean doubleHook = f.compactDoubleHook && doubleHookRecent(now);
        // Nach einer Fang-Zeile ist der Zeitpunkt vergessen; bei einer Zeile, die keine ist (return oben), bleibt er stehen
        doubleHookAt = NONE;

        StyledText edited = cur;
        if (f.shortenFishingMessage) edited = StyledText.ofLegacy(shortLine(creature));
        if (doubleHook) {
            edited = f.compactDoubleHookPosition == CompactDoubleHookPosition.RIGHT
                    ? edited.append(StyledText.ofLegacy(AFTER))
                    : StyledText.ofLegacy(BEFORE).append(edited);
        }
        return edited == cur ? null : edited;
    }

    /** "You caught a Squid!" mit Farbcodes: blau, der Name in der Farbe des Meeresbewohners */
    static String shortLine(SeaCreatures.Creature creature) {
        String name = creature.name();
        String code = legacyCodeOf(creature.colour());
        return "§9You caught " + articleOf(name) + (code == null ? "§f" : code) + name + "§9!";
    }

    /** "a ", "an " oder, bei einem Namen mit "The" davor, nichts - jeweils mit dem Leerzeichen dahinter */
    static String articleOf(String name) {
        if (name == null || name.isEmpty() || name.regionMatches(true, 0, "The ", 0, 4)) return "";
        return "aeiouAEIOU".indexOf(name.charAt(0)) >= 0 ? "an " : "a ";
    }

    /** Der Farbcode ({@code "§6"}) zu einer der sechzehn Farben (RGB), oder null, wenn es keine davon ist */
    static String legacyCodeOf(int rgb) {
        for (ChatFormatting f : ChatFormatting.values()) {
            TextColor legacy = TextColor.fromLegacyFormat(f);
            if (legacy != null && legacy.getValue() == (rgb & 0xFFFFFF)) return f.toString();
        }
        return null;
    }
}
