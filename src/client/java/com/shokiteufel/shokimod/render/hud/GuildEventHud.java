package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.GuildEvents;
import com.shokiteufel.shokimod.handler.GuildEvents.Event;
import com.shokiteufel.shokimod.handler.GuildEvents.Feed;
import com.shokiteufel.shokimod.handler.GuildEvents.Row;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Der Kasten zu den Gilden-Events: je laufendem Event Name, Wertung, Reward, Restzeit,
 * die vordersten Plaetze und der eigene.
 *
 * Nichts wird hier geholt; {@link GuildEvents} liefert den Stand, der Kasten ordnet
 * ihn nur an. Ohne Daten steht drin, woran es liegt - der Tester sieht das Spiel,
 * nicht das Log.
 */
public final class GuildEventHud {

    private static final int TITLE_COLOUR = HudColours.GOLD;
    private static final int NAME_COLOUR = HudColours.YELLOW;
    private static final int LABEL_COLOUR = HudColours.WHITE;
    private static final int VALUE_COLOUR = HudColours.GREEN;
    private static final int TIME_COLOUR = HudColours.AQUA;
    private static final int MUTED = HudColours.GRAY;
    private static final int OWN_COLOUR = HudColours.LIGHT_PURPLE;

    private GuildEventHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        panel.title("Guild Event", TITLE_COLOUR);
        Feed feed = GuildEvents.current();
        if (feed == null) {
            panel.line("Waiting for the bot...", MUTED);
            return panel;
        }

        List<Event> events = feed.events();
        if (events.isEmpty()) {
            panel.line("No event running", MUTED);
            if (feed.next() != null) {
                panel.pair("Next:", feed.next().name(), LABEL_COLOUR, NAME_COLOUR);
                panel.pair("Starts in:", countdown(feed.next().start()), LABEL_COLOUR, TIME_COLOUR);
            }
            return panel;
        }

        int limit = Math.max(1, ModConfig.INSTANCE.guild.events.topRows);
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) panel.blank();
            addEvent(panel, events.get(i), limit);
        }
        return panel;
    }

    private static void addEvent(HudPanel panel, Event event, int limit) {
        panel.line(event.name(), NAME_COLOUR);
        if (!event.label().isEmpty()) panel.line(event.label(), MUTED);
        if (!event.reward().isEmpty()) panel.pair("Reward:", event.reward(), LABEL_COLOUR, NAME_COLOUR);
        panel.pair("Ends in:", countdown(event.end()), LABEL_COLOUR, TIME_COLOUR);

        List<Row> rows = event.standings();
        if (rows.isEmpty()) {
            panel.line("Nobody joined yet", MUTED);
        } else {
            for (int i = 0; i < rows.size() && i < limit; i++) {
                Row row = rows.get(i);
                panel.pair("#" + row.rank() + " " + row.ign(), score(row.score()), LABEL_COLOUR, VALUE_COLOUR);
            }
        }

        Row own = GuildEvents.ownRow(Minecraft.getInstance(), event);
        if (own == null) {
            panel.pair("You:", "not joined", LABEL_COLOUR, MUTED);
        } else {
            panel.pair("You: #" + own.rank(), score(own.score()), OWN_COLOUR, VALUE_COLOUR);
        }
    }

    /** Ganze Zahlen ohne Nachkommastellen, grosse gekuerzt wie Coins */
    private static String score(double value) {
        if (value < 10_000) return String.valueOf((long) value);
        return ItemValue.format(value);
    }

    /** "2d 4h", "3h 12m", "45m", "ended" */
    private static String countdown(long unixSeconds) {
        long left = unixSeconds - System.currentTimeMillis() / 1000L;
        if (left <= 0) return "ended";
        return GuildEvents.span(0, left);
    }
}
