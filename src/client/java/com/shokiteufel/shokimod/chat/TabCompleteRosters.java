package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.TabCompleteCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;

/**
 * Haelt die Listen der Party, der Gilde und der Freunde aus den Chatzeilen auf dem Laufenden - die Quellen fuer die Namen hinter {@code /p}, {@code /g} und {@code
 * /f} (Chat > Tab Complete) und fuer die Zeile "Party (3 members)" im Kasten "Current Chat Display".
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PartyApi}, {@code GuildApi}, {@code FriendApi}); die Regeln stehen in {@link PartyRoster}, {@link GuildRoster} und
 * {@link FriendRoster}. Hier steht nur das Anschliessen: die Zeile kommt aus dem Chat-Weg, die Eintraege der Freundesliste werden aus den Klicks der Zeile gelesen
 * (jeder Eintrag von {@code /friend list} traegt den Klick {@code /viewprofile <Kennung>} und im Hover "Click here to view Name's profile"), die Listen der Freunde
 * und der Gilde liegen in der Einstellungsdatei.
 */
public final class TabCompleteRosters {

    private static boolean loaded = false;

    private TabCompleteRosters() {
    }

    /** Die gespeicherten Listen der Freunde und der Gilde einsetzen (einmal; ein zweiter Aufruf ersetzt sie nicht noch einmal) */
    public static void load() {
        if (loaded) return;
        loaded = true;
        TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        if (c == null) return;
        FriendRoster.INSTANCE.load(c.knownFriends, c.knownBestFriends);
        GuildRoster.INSTANCE.load(c.knownGuildMembers);
    }

    /** Eine Chatzeile mit ihrer Komponente: aendert sie etwas an den Listen? */
    public static void onChatMessage(Component message, String plain) {
        if (plain == null || plain.isEmpty()) return;
        TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        if (c == null) return;
        load();

        if (PartyRoster.mightConcern(plain)) PartyRoster.INSTANCE.onLine(plain, selfName());

        if (GuildRoster.INSTANCE.mightConcern(plain) && GuildRoster.INSTANCE.onLine(plain)) {
            c.knownGuildMembers = new ArrayList<>(GuildRoster.INSTANCE.members());
            ModConfig.INSTANCE.saveNow();
        }

        if (FriendRoster.mightConcern(plain)) {
            boolean changed = FriendRoster.INSTANCE.onLine(plain);
            if (message != null && FriendRoster.isFriendListBlock(plain)) changed |= readFriendList(message, false);
            if (changed) {
                c.knownFriends = FriendRoster.INSTANCE.friends();
                c.knownBestFriends = FriendRoster.INSTANCE.bestFriends();
                ModConfig.INSTANCE.saveNow();
            }
        }
    }

    /** Die Eintraege der Freundesliste einer Seite von {@code /friend list}: Name aus dem Hover, "bester Freund" wenn der Eintrag fett geschrieben ist */
    static boolean readFriendList(Component component, boolean parentBold) {
        boolean changed = false;
        Style style = component.getStyle();
        boolean bold = parentBold || style.isBold();
        if (style.getClickEvent() instanceof ClickEvent.RunCommand run && run.command().startsWith("/viewprofile")) {
            String hover = style.getHoverEvent() instanceof HoverEvent.ShowText show ? show.value().getString() : null;
            String name = FriendRoster.nameFromHover(hover);
            if (name != null) changed |= FriendRoster.INSTANCE.addFriend(name, bold);
        }
        for (Component sibling : component.getSiblings()) changed |= readFriendList(sibling, bold);
        return changed;
    }

    private static String selfName() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.player == null ? null : client.player.getName().getString();
    }
}
