package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party-Einladungen: annehmen mit einer Taste, und einladen mit einem Klick, wenn jemand im Gildenchat ein Wort schreibt.
 *
 * Annehmen: Hypixel schreibt "[MVP++] Name has invited you to join their party!" und darunter "Click here to join!". Die Mod merkt sich die
 * Einladung 60 Sekunden lang - so lange gilt sie -, und die gewaehlte Taste schickt "/party accept Name". Die Taste hat keine Vorgabe
 * (wie beim Hotspot-Teilen: abgefragt, nicht als Tastenbelegung des Spiels eingetragen). Welchen Namen der Befehl bekommt, liest die
 * Mod aus dem Klick der Zeile selbst ("Click to run /party accept Name"), statt zu raten, ob der Einladende oder der Leiter gemeint ist.
 *
 * Einladen: Schreibt jemand im Gildenchat genau das eingestellte Wort (Vorgabe "+"), kommt eine Zeile mit einem Klick, der ihn in die Party
 * einlaedt - wie beim Vanguard-Ruf, nur ohne den Ruf und ohne die 30 Sekunden. Nur aus dem Gildenchat; aus ist die Vorgabe.
 */
public final class PartyInvites {

    /** "You have 60 seconds to accept" - so lange gilt eine Einladung */
    static final long INVITE_MILLIS = 60_000L;
    /** Dasselbe Wort derselben Person bekommt innerhalb dieser Zeit keine zweite Zeile */
    static final long SUGGEST_GAP_MILLIS = 20_000L;
    private static final int SUGGESTED_MAX = 100;
    /** So kurz nach der Einladungszeile darf die Zeile mit dem Klick folgen, wenn Hypixel sie einzeln schickt */
    private static final long CLICK_FOLLOW_MILLIS = 5_000L;

    /**
     * "[MVP++] Name has invited you to join their party!" oder, wenn ein anderer als der Leiter einlaedt,
     * "Name has invited you to join [MVP+] Leader's party!". Die Zeile muss mit dem Namen beginnen: Eine Chatzeile eines Spielers
     * ("[MVP+] Name: ...", "Guild > Name: ...") hat nach dem Namen einen Doppelpunkt und passt nie.
     */
    private static final Pattern INVITE = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?(?<name>[A-Za-z0-9_]{1,16}) has invited you to join "
                    + "(?:their|(?:\\[[^\\]]+\\]\\s*)?(?<leader>[A-Za-z0-9_]{1,16})'s) party!$");
    /** Der Befehl, den Hypixel an den Klick der Zeile haengt */
    private static final Pattern ACCEPT_COMMAND = Pattern.compile(
            "^/?party accept (?<name>[A-Za-z0-9_]{1,16})$", Pattern.CASE_INSENSITIVE);
    /** Wer der Party schon beigetreten ist, braucht die Einladung nicht mehr */
    private static final Pattern JOINED = Pattern.compile(
            "^You have joined (?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]{1,16}'s party!$");
    /** "Guild > [MVP+] Name [Rang]: Text" - Rang und Titel sind optional. Der Offiziers-Chat ("Officer >") gehoert nicht dazu */
    private static final Pattern GUILD_LINE = Pattern.compile(
            "^Guild > (?:\\[[^\\]]+\\]\\s*)?(?<name>[A-Za-z0-9_]{1,16})(?:\\s*\\[[^\\]]+\\])?:\\s*(?<text>.+)$");

    /** Die Einladung, die gerade wartet: wer eingeladen hat, wem der Befehl gilt, bis wann */
    private static String pendingFrom = "";
    private static String pendingTarget = "";
    private static long pendingUntil = 0L;

    private static final Map<String, Long> suggested = new HashMap<>();
    private static boolean keyWasDown = false;

    private PartyInvites() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PartyInvites::tick);
    }

    /** Eine Chatzeile - mit der Komponente, denn der Befehl der Einladung haengt an ihrem Klick */
    public static void onChatMessage(Component message, String plain) {
        if (plain == null || plain.isEmpty()) return;
        // Die Zeilen sind billig zu pruefen; erst wenn eine passt, fallen Arbeit und Ausgabe an
        boolean maybeInvite = plain.contains("invited you to join");
        // Schickt Hypixel die Zeilen einer Einladung einzeln, steht der Klick erst in der zweiten ("... to accept. Click here to join!")
        boolean maybeClick = pendingUntil > 0L && plain.contains("to accept");
        boolean maybeJoined = pendingUntil > 0L && plain.contains("You have joined");
        boolean maybeGuild = plain.startsWith("Guild >") && ModConfig.INSTANCE.guild.party.suggestInvites;
        if (!maybeInvite && !maybeClick && !maybeJoined && !maybeGuild) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        long now = System.currentTimeMillis();

        if (maybeClick && !maybeInvite && now < pendingUntil && now - (pendingUntil - INVITE_MILLIS) <= CLICK_FOLLOW_MILLIS) {
            String clicked = acceptTargetOf(message);
            if (clicked != null && !clicked.equalsIgnoreCase(pendingTarget)) {
                ShokiMod.LOGGER.info("[Party] the click of the invite names {}, not {}", clicked, pendingTarget);
                pendingTarget = clicked;
            }
        }

        for (String raw : plain.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Matcher invite = INVITE.matcher(line);
            if (invite.matches()) {
                noteInvite(client, invite.group("name"), invite.group("leader"), acceptTargetOf(message), now);
                continue;
            }
            if (JOINED.matcher(line).matches()) {
                pendingUntil = 0L;
                continue;
            }
            if (maybeGuild) suggest(client, line, now);
        }
    }

    /** Eine Einladung ist da: merken, und - wenn eine Taste gewaehlt ist - sagen, welche */
    private static void noteInvite(Minecraft client, String from, String leader, String clicked, long now) {
        // Der Klick der Zeile ist die Quelle; fehlt er, gilt der Leiter, und gibt es keinen, der Einladende
        String target = clicked != null ? clicked : (leader != null ? leader : from);
        pendingFrom = from;
        pendingTarget = target;
        pendingUntil = now + INVITE_MILLIS;
        ShokiMod.LOGGER.info("[Party] invite from {}, /party accept {} ({})", from, target, clicked != null ? "from the click" : "guessed");

        int key = acceptKey();
        if (key > 0) {
            chat(client, Component.literal("Press ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(ShortcutHandler.keyName(key)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                    .append(Component.literal(" to accept the party invite of " + from).withStyle(ChatFormatting.WHITE)));
        }
    }

    /** Ein Wort im Gildenchat, das einen Einladen-Knopf bekommt */
    private static void suggest(Minecraft client, String line, long now) {
        ModConfig.PartyInvitesCategory cfg = ModConfig.INSTANCE.guild.party;
        String trigger = cfg.inviteText == null ? "" : cfg.inviteText.trim();
        if (!cfg.suggestInvites || trigger.isEmpty()) return;

        Matcher m = GUILD_LINE.matcher(line);
        if (!m.matches()) return;
        String name = m.group("name");
        String text = m.group("text").trim();
        if (!text.equalsIgnoreCase(trigger)) return;
        if (name.equalsIgnoreCase(client.player.getName().getString())) return;
        // Im Fenster des Vanguard-Rufs gibt es fuer das Plus schon eine Zeile, mit dem passenderen Text
        if (text.equals("+") && VanguardGuildCall.windowOpen()) return;

        String key = name.toLowerCase(Locale.ROOT);
        Long last = suggested.get(key);
        if (last != null && now - last < SUGGEST_GAP_MILLIS) return;
        if (suggested.size() >= SUGGESTED_MAX) suggested.clear();
        suggested.put(key, now);

        String run = "/party invite " + name;
        Component click = Component.literal("[Click to invite " + name + "]")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(run))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to run " + run))));
        chat(client, Component.literal(name + " wrote \"" + text + "\" in the guild chat ").withStyle(ChatFormatting.YELLOW).append(click));
    }

    /**
     * Der Name aus dem Klick der Zeile: Hypixel haengt an "Click here to join!" den Befehl "/party accept Name". Null, wenn die Zeile keinen
     * solchen Klick traegt.
     */
    static String acceptTargetOf(Component component) {
        if (component == null) return null;
        if (component.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) {
            Matcher m = ACCEPT_COMMAND.matcher(run.command().trim());
            if (m.matches()) return m.group("name");
        }
        for (Component sibling : component.getSiblings()) {
            String found = acceptTargetOf(sibling);
            if (found != null) return found;
        }
        return null;
    }

    // ---- Die Taste ----

    /** Die gewaehlte Taste, ein GLFW-Code; 0 heisst: keine (auch eine Maustaste zaehlt hier als keine) */
    private static int acceptKey() {
        ModConfig.PartyInvitesCategory c = ModConfig.INSTANCE.guild.party;
        return c.hasAcceptKey() ? c.acceptKey : 0;
    }

    private static void tick(Minecraft client) {
        int key = acceptKey();
        if (key <= 0) {
            keyWasDown = false;
            return;
        }
        boolean down = ShortcutHandler.isDown(key);
        if (client.player == null) {
            // Auch ausserhalb zaehlt der Stand der Taste weiter, damit eine gehaltene Taste beim Betreten nicht als Druck zaehlt
            keyWasDown = down;
            return;
        }
        // Nur wenn das Spiel die Maus haelt und im Vordergrund ist: Wer im Chat tippt, loest nichts aus
        boolean canFire = client.mouseHandler.isMouseGrabbed() && client.isWindowActive();
        boolean fire = down && !keyWasDown && canFire;
        keyWasDown = down;
        if (fire) accept(client, System.currentTimeMillis());
    }

    /** Die Taste wurde gedrueckt: die wartende Einladung annehmen - oder sagen, dass keine wartet */
    static void accept(Minecraft client, long now) {
        if (client.getConnection() == null || client.player == null) return;
        if (pendingTarget.isEmpty() || now > pendingUntil) {
            chat(client, Component.literal("No party invite waiting.").withStyle(ChatFormatting.GRAY));
            return;
        }
        String command = "party accept " + pendingTarget;
        String from = pendingFrom;
        // Vor dem Senden vergessen: ein zweiter Druck schickt nicht noch einmal
        pendingUntil = 0L;
        pendingTarget = "";
        ShokiMod.LOGGER.info("[Party] accepting: /{}", command);
        client.getConnection().sendCommand(command);
        chat(client, Component.literal("Accepting the party invite of " + from + ".").withStyle(ChatFormatting.WHITE));
    }

    /** Die wartende Einladung, falls sie noch gilt - null sonst. Fuer die Pruefung */
    static String pendingCommand(long now) {
        return pendingTarget.isEmpty() || now > pendingUntil ? null : "party accept " + pendingTarget;
    }

    /** Alles vergessen - fuer die Pruefung und beim Weltwechsel */
    public static void reset() {
        pendingFrom = "";
        pendingTarget = "";
        pendingUntil = 0L;
        suggested.clear();
        keyWasDown = false;
    }

    private static void chat(Minecraft client, Component text) {
        if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA).append(text));
    }
}
