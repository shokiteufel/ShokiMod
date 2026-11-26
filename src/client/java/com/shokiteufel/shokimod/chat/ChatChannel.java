package com.shokiteufel.shokimod.chat;

import java.util.Locale;

/**
 * Der Chat, in den man auf Hypixel gerade schreibt: alle, die Party, die Gilde, die Offiziere, ein Gespraech unter vier Augen oder der Coop.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CurrentChatDisplay}). Von dort stammen die sechs Arten, ihre Farben ("All" gelb, "Party" blau, "Guild" dunkelgruen,
 * "Officer" dunkeltuerkis, "CO-OP" tuerkis) und die Namen, mit denen Hypixel sie meldet ("SKYBLOCK CO-OP" fuer den Coop). Die Umsetzung ist eigene Arbeit: Java.
 */
public enum ChatChannel {
    ALL("§e", "All", "ALL"),
    PARTY("§9", "Party", "PARTY"),
    GUILD("§2", "Guild", "GUILD"),
    OFFICER("§3", "Officer", "OFFICER"),
    PRIVATE("", "Private", "PRIVATE"),
    SKYBLOCK_COOP("§b", "CO-OP", "SKYBLOCK CO-OP");

    private final String colour;
    private final String label;
    private final String serverName;

    ChatChannel(String colour, String label, String serverName) {
        this.colour = colour;
        this.label = label;
        this.serverName = serverName;
    }

    /** Der Name, wie er im Kasten steht, mit seiner Farbe ("§eAll") */
    public String display() {
        return colour + label;
    }

    /** Der Chat zu dem Namen, den Hypixel in "You are now in the X channel" nennt - oder null, wenn die Mod ihn nicht kennt (Gross und Klein egal) */
    public static ChatChannel fromServerName(String name) {
        if (name == null) return null;
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        for (ChatChannel channel : values()) {
            if (channel.serverName.equals(wanted)) return channel;
        }
        return null;
    }
}
