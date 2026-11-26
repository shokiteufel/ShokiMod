package com.shokiteufel.shokimod.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > SkyBlock XP in Chat: Wer SkyBlock-XP bekommt, sieht es nur kurz in der Aktionsleiste ("+7 SkyBlock XP (Kill Combo) (45/100)"). Hier steht es zusaetzlich
 * im Chat, einmal je neuem Stand.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code SkyblockXPInChat}, {@code ActionBarStatsData}). Von dort stammt die Regel: Aus der Aktionsleiste wird das Stueck
 * "+N SkyBlock XP (Quelle) (x/y)" gelesen (die Leiste wird in jedem Tick neu geschickt, der Chat bekommt die Zeile nur, wenn sich das Stueck gegenueber dem letzten
 * unterscheidet). Die Umsetzung ist eigene Arbeit: Java, das Muster laeuft gegen den Klartext der Leiste, die Farben des Stuecks kommen aus dem Abschnitt, den das Muster
 * getroffen hat ({@link StyledText}), vorn steht der Name der Mod statt dem von SkyHanni, und der Stand wird beim Weltwechsel vergessen.
 *
 * <p>Der Einsprung ist der Zweig fuer die Aktionsleiste in {@code NetworkHandler}: erst der Schalter ({@code FeatureGate.skyblockXpInChat}), dann ein {@code indexOf} auf
 * das Wort "SkyBlock XP", und erst dann wird die Leiste gelesen.
 */
public final class SkyblockXpInChat {

    private static final Pattern XP = Pattern.compile("\\+\\d+ SkyBlock XP \\([^()]+\\) \\(\\d+/\\d+\\)");

    /** Das zuletzt gesendete Stueck (Klartext) */
    private static String last = "";

    private SkyblockXpInChat() {
    }

    /** Die Aktionsleiste (als Komponente, mit Farben); schreibt das Stueck in den Chat, wenn es neu ist */
    public static void onActionBar(Component bar) {
        String segment = next(StyledText.of(bar));
        if (segment != null) ChatFilters.say(line(segment));
    }

    /** Das Stueck mit Farbcodes, wenn die Leiste eines traegt, das nicht schon gesendet wurde - sonst null. Merkt sich, was es zurueckgibt */
    static String next(StyledText bar) {
        Matcher m = XP.matcher(bar.plain());
        if (!m.find()) return null;
        if (m.group().equals(last)) return null;
        last = m.group();
        return bar.legacy(m.start(), m.end());
    }

    /** Die Zeile im Chat: der Name der Mod, dahinter das Stueck */
    static MutableComponent line(String legacySegment) {
        return Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA).append(StyledText.ofLegacy(legacySegment).toComponent());
    }

    /** Neue Welt: der gemerkte Stand gilt nicht mehr */
    public static void reset() {
        last = "";
    }
}
