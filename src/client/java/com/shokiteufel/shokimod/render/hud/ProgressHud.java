package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.Critters;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.SafariBiome;
import com.shokiteufel.shokimod.session.SafariSession;
import com.shokiteufel.shokimod.session.SessionManager;
import net.minecraft.client.Minecraft;

import java.util.Map;

/**
 * Der Fortschritt eines Laufs: Uhr, Critterdex von Gruppe und einem selbst,
 * ein Balken je Biom, darunter wer wie viel beigetragen hat.
 *
 * Nachbau von crittermods CritterHud.
 */
public final class ProgressHud {

    private static final int TITLE_COLOUR = 0xFF6EFF6E;
    private static final int LABEL_COLOUR = 0xFFE6E6E6;
    private static final int DONE_COLOUR = 0xFF7CFF7C;
    private static final int OWN_COLOUR = 0xFF6EF0FF;

    private ProgressHud() {
    }

    public static HudPanel build() {
        HudPanel panel = new HudPanel();
        SafariSession session = SessionManager.currentOrLast();
        boolean firstCatchIsEnough = ModConfig.INSTANCE.safari.firstCatchIsEnough;

        // Ohne Lauf steht der Kasten trotzdem da, damit man ihn platzieren kann
        boolean noRun = session == null;
        if (noRun) {
            session = new SafariSession(Minecraft.getInstance().getUser() == null
                    ? null : Minecraft.getInstance().getUser().getName(), System.currentTimeMillis());
        }

        String title;
        if (noRun) {
            title = "Critter Safari (ready)";
        } else if (session.isRunning()) {
            title = "Critter Safari  " + formatDuration(session.elapsedMillis(System.currentTimeMillis()));
        } else {
            title = "Critter Safari (last run)";
        }
        panel.title(title, TITLE_COLOUR);

        int total = Critters.total();
        panel.bar("Party", session.partyUnique(), total, LABEL_COLOUR,
                session.dexComplete(firstCatchIsEnough) ? DONE_COLOUR : 0xFFD0D0D0);
        panel.bar("You", session.ownUnique(), total, LABEL_COLOUR, OWN_COLOUR);

        panel.blank();
        for (SafariBiome biome : SafariBiome.values()) {
            boolean complete = session.biomeComplete(biome, firstCatchIsEnough);
            String label = complete ? biome.displayName() + " *" : biome.displayName();
            panel.bar(label, session.partyUnique(biome), Critters.totalIn(biome),
                    biomeText(biome), complete ? DONE_COLOUR : biomeText(biome));
        }

        if (ModConfig.INSTANCE.safari.showPerPlayer) {
            Map<String, Integer> perPlayer = session.uniquePerPlayer();
            // Allein braucht es die Aufschluesselung nicht - sie steht schon oben als "You"
            if (perPlayer.size() > 1) {
                panel.blank();
                perPlayer.forEach((player, count) ->
                        panel.pair(player, String.valueOf(count), LABEL_COLOUR, 0xFFFFFFFF));
            }
        }

        return panel;
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Die Biomfarben liegen als reines RGB vor; fuer Text muss das Alpha-Byte dazu */
    private static int biomeText(SafariBiome biome) {
        return 0xFF000000 | biome.textColour();
    }
}
