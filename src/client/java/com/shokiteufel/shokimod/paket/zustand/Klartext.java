package com.shokiteufel.shokimod.paket.zustand;

import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Text ohne Farbcodes. Hypixel schreibt Farbcodes teils als Stil der Komponente, teils als buchstaeblichen Text
 * ("§aYou have ..."); {@link Component#getString()} entfernt nur die ersten, deshalb werden die zweiten hier entfernt.
 */
public final class Klartext {
    private static final Pattern FARBCODE = Pattern.compile("§.");

    private Klartext() {
    }

    public static String von(Component komponente) {
        return komponente == null ? "" : entfernen(komponente.getString());
    }

    public static String entfernen(String text) {
        return text == null ? "" : text.indexOf('§') < 0 ? text : FARBCODE.matcher(text).replaceAll("");
    }
}
