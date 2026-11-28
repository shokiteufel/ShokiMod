package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.ShokiMod;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import java.util.Locale;
import java.util.Set;

/**
 * Beim Senden von {@code /gfs}: Unterstriche in einem Sack-Namen werden zu Leerzeichen ("/gfs ENCHANTED_COAL 64" geht als "/gfs ENCHANTED COAL 64" hinaus).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code GetFromSacksTabComplete.handleUnderlineReplace}). Die Vorschlaege hinter {@code /gfs} ({@link SackItems}) nennen die
 * Namen mit Unterstrichen, weil die Chat-Eingabe wortweise vorschlaegt; Hypixel will den Namen mit Leerzeichen. SkyHanni setzt sie beim Senden zurueck - aber nur,
 * wenn es ein Name aus der Sack-Liste ist, sonst bleibt der Befehl, wie er getippt wurde. Die Umsetzung ist eigene Arbeit: Java.
 *
 * <p><b>Nur getippte Befehle:</b> Die Mod schickt selbst {@code gfs OIL_BARREL 5} und {@code gfs UMBER_KEY 2} (Tanken, Schluessel; {@code CommandSequence}). Beide
 * stehen in der Sack-Liste und wuerden sonst umgeschrieben - dabei sind sie in genau dieser Form bewaehrt. Darum markiert {@link #asOwn(Runnable)} sie, und die Mod
 * fasst sie nicht an (bei SkyHanni {@code senderIsSkyhanni}).
 *
 * <p>Nur mit dem Schalter "/gfs Sack" an, in SkyBlock und mit dem 26.3-Schalter an; sonst geht der Befehl, wie er ist.
 */
public final class GfsUnderscores {

    /** Gerade schickt die Mod einen eigenen Befehl: Der laeuft im selben Aufruf durch {@code MODIFY_COMMAND}, darum reicht ein Merker */
    private static boolean own = false;

    private GfsUnderscores() {
    }

    public static void register() {
        ClientSendMessageEvents.MODIFY_COMMAND.register(GfsUnderscores::modify);
    }

    /** Fuehrt das Senden aus und gilt dabei als "eigener Befehl der Mod": der wird nie umgeschrieben */
    public static void asOwn(Runnable send) {
        boolean before = own;
        own = true;
        try {
            send.run();
        } finally {
            own = before;
        }
    }

    /** Was {@code MODIFY_COMMAND} aufruft: der Befehl ohne Schraegstrich, so wie er hinausgeht */
    static String modify(String command) {
        if (own || command == null) return command;
        try {
            if (!TabCompleteCommands.active("gfs")) return command;
            String sent = rewrite(command, SackItems.realNames());
            if (!sent.equals(command)) ShokiMod.LOGGER.info("[Chat] /{} -> /{}", command, sent);
            return sent;
        } catch (RuntimeException e) {
            // Ein Fehler hier darf nie einen Befehl kosten: er geht, wie getippt
            return command;
        }
    }

    /**
     * Der Befehl, wie er hinausgeht.
     *
     * @param command   ohne Schraegstrich ("gfs ENCHANTED_COAL 64")
     * @param realNames die echten Namen der Sack-Gegenstaende, Grossbuchstaben, mit Leerzeichen
     */
    static String rewrite(String command, Set<String> realNames) {
        String[] words = command.split(" ", -1);
        // "gfs <Name> <Menge>": mindestens drei Woerter, sonst ist da kein Name zu ersetzen
        if (words.length < 3) return command;
        String name = words[0].toLowerCase(Locale.ROOT);
        if (!name.equals("gfs") && !name.equals("getfromsacks")) return command;

        StringBuilder middle = new StringBuilder();
        for (int i = 1; i < words.length - 1; i++) {
            if (i > 1) middle.append(' ');
            middle.append(words[i]);
        }
        String raw = middle.toString();
        String real = raw.replace('_', ' ');
        if (real.equals(raw)) return command;
        if (!realNames.contains(real.toUpperCase(Locale.ROOT))) return command;
        return words[0] + " " + real + " " + words[words.length - 1];
    }
}
