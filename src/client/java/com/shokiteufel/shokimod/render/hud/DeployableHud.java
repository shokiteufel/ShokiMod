package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DeployableHudCategory;
import com.shokiteufel.shokimod.handler.DeployableTimers;
import com.shokiteufel.shokimod.handler.DeployableTimers.Row;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Der Zeitkasten der Deployables: wie lange die eigenen - oder alle in der Naehe - noch halten, das naechste Ende zuerst.
 *
 * Die Zeit rechnet {@link DeployableTimers} aus den Messwerten der Staender und laeuft gleichmaessig herunter; der Kasten wird mit jedem
 * Tick neu gebaut (siehe SafariHud), damit die Zehntel nicht im Viertelsekundentakt springen. Gelb ab 30, rot ab 10 Sekunden.
 * Im HUD-Editor stehen zwei Beispielzeilen da, sonst liesse sich der Kasten nie setzen.
 */
public final class DeployableHud {

    private static final long WARN_MILLIS = 30_000L;
    private static final long URGENT_MILLIS = 10_000L;

    private DeployableHud() {
    }

    private static DeployableHudCategory cfg() {
        return ModConfig.INSTANCE.deployables.hud;
    }

    /** Ist gerade der HUD-Editor offen? Die eine Stelle, die je Minecraft-Version anders heisst (26.1: screen, 26.2/26.3: gui.screen()) */
    private static boolean editorOpen() {
        return Minecraft.getInstance().screen instanceof com.shokiteufel.shokimod.gui.HudEditorScreen;
    }

    /** Soll der Kasten jetzt gezeichnet werden? Nur mit etwas darin - im HUD-Editor immer */
    public static boolean shownNow() {
        return cfg().enabled && (editorOpen() || DeployableTimers.hasRows());
    }

    static int colourFor(long remainingMs) {
        if (remainingMs <= URGENT_MILLIS) return HudColours.RED;
        if (remainingMs <= WARN_MILLIS) return HudColours.YELLOW;
        return HudColours.WHITE;
    }

    /** Der Kasten, wie er jetzt gezeichnet wird - nach der Uhr des letzten Ticks */
    public static HudPanel build() {
        return build(DeployableTimers.tickTime(), editorOpen());
    }

    public static HudPanel build(long now, boolean editorOpen) {
        DeployableHudCategory c = cfg();
        HudPanel panel = new HudPanel();
        List<Row> rows = DeployableTimers.rows(now);
        if (rows.isEmpty() && editorOpen) {
            // Im Editor stehen Beispiele da, damit man den Kasten sieht und setzen kann
            panel.pair("Plasmaflux Power Orb", DeployableTimers.format(27_400L, c.tenths), HudColours.GOLD, colourFor(27_400L));
            panel.pair("Totem of Corruption", DeployableTimers.format(127_000L, c.tenths), HudColours.GOLD, colourFor(127_000L));
            return panel;
        }
        boolean own = c.mode == ModConfig.DeployableHudMode.OWN;
        for (Row row : rows) {
            // Im Modus "alle in der Naehe" sind die eigenen golden, die der anderen grau
            int label = own || row.mine() ? HudColours.GOLD : HudColours.GRAY;
            panel.pair(row.name(), DeployableTimers.format(row.remainingMs(), c.tenths), label, colourFor(row.remainingMs()));
        }
        return panel;
    }
}
