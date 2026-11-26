package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Compact Potion Messages: "BUFF! You were splashed by Name with Speed IV! Press TAB or type /effects to view your active effects!" wird zu
 * "Potion Effect! Speed IV by Name" - eine kurze Zeile, auf Wunsch mit einem Klick, der /effects oeffnet.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CompactSplashPotionMessage}). Von dort stammt die Regel: Die sechs Meldungen (von jemandem angespritzt,
 * selbst bekommen, selbst gespritzt - je mit dem Hinweis auf TAB und /effects, und die drei Zeilen des Poisoned Candy ohne ihn) werden gelesen und durch
 * "Potion Effect! <Wirkung>" ersetzt, bei einem fremden Spritzer mit "by <Name>"; der Klick fuehrt zu /effects. Die Umsetzung ist eigene Arbeit: Java, Muster
 * fuer die Zeile ohne Farbcodes; die Farben von Wirkung und Name kommen aus dem Abschnitt, den das Muster getroffen hat, nicht aus einem Muster mit Codes.
 */
final class CompactPotionMessages {

    private static final String TAIL = "! Press TAB or type /effects to view your active effects!";

    private record Variant(Pattern pattern, boolean byPlayer) {
    }

    private static final Variant[] VARIANTS = {
            new Variant(Pattern.compile("BUFF! You were splashed by (?<player>.*) with (?<effect>.*)" + TAIL), true),
            new Variant(Pattern.compile("BUFF! You have gained (?<effect>.*)" + TAIL), false),
            new Variant(Pattern.compile("BUFF! You splashed yourself with (?<effect>.*)" + TAIL), false),
            // Hypixel schreibt beim Poisoned Candy eine andere Meldung ohne den Hinweis; das Muster der Wirkung ist hier fest, damit es nichts anderes trifft
            new Variant(Pattern.compile("BUFF! You have gained (?<effect>Poisoned Candy I)!"), false),
            new Variant(Pattern.compile("BUFF! You splashed yourself with (?<effect>Poisoned Candy I)!"), false),
            new Variant(Pattern.compile("BUFF! You were splashed by (?<player>.*) with (?<effect>Poisoned Candy I)!"), true),
    };

    private CompactPotionMessages() {
    }

    /** Die kurze Zeile, oder null, wenn die Zeile keine dieser Meldungen ist */
    static ChatVerdict line(Incoming in, ChatTabCategory t) {
        // Alle sechs fangen so an: der billige Blick vor den Mustern
        if (!in.plain.startsWith("BUFF! ")) return null;
        for (Variant v : VARIANTS) {
            Matcher m = v.pattern().matcher(in.plain);
            if (!m.matches()) continue;
            StyledText styled = in.styled();
            String effect = styled.legacy(m.start("effect"), m.end("effect"));
            String by = v.byPlayer() ? " §aby " + styled.legacy(m.start("player"), m.end("player")) : "";
            String text = "§a§lPotion Effect! §r" + effect + by;
            boolean click = t.compactPotion != null && t.compactPotion.clickableChatMessage;
            return click
                    ? ChatVerdict.replace(new ChatVerdict.Line(text, "/effects", "§eClick to view your potion effects."))
                    : ChatVerdict.replace(text);
        }
        return null;
    }
}
