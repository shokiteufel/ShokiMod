package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Platziert die Kaesten dieser Mod und zeichnet sie.
 *
 * Die Lage wird als Anteil der Bildschirmgroesse gespeichert, nicht in Pixeln - sonst
 * wandert der Kasten beim Wechsel der Aufloesung oder der GUI-Skalierung aus dem Bild.
 */
public final class SafariHud {

    /** Ein Kasten auf dem Bildschirm. Traegt seine eigene Lage und Groesse in der Config */
    public enum Panel {
        PROGRESS, MISSING, CONTEST, NEARBY, HUNTING, GUILD, COLLECTION, MINING, DAY, PET;

        public boolean visible() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.showProgressHud;
                case MISSING -> c.showMissingHud;
                case CONTEST -> c.showContestHud;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.masterEnabled && ModConfig.INSTANCE.mobVisuals.showNearbyHud;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.enabled && ModConfig.INSTANCE.hunting.tracker.showHud;
                case GUILD -> ModConfig.INSTANCE.guild.events.enabled && ModConfig.INSTANCE.guild.events.showHud;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.enabled && ModConfig.INSTANCE.collections.tracker.showHud;
                case MINING -> ModConfig.INSTANCE.mining.hud.showHud;
                case DAY -> ModConfig.INSTANCE.hud.day.showHud;
                case PET -> ModConfig.INSTANCE.hud.pet.showHud;
            };
        }

        /**
         * Gehoert der Kasten hierher?
         *
         * Zuerst zaehlt, was im HUD-Editor unter "Areas" gewaehlt wurde. Ist dort nichts
         * gesetzt, gilt die eingebaute Vorgabe: die beiden Safari-Kaesten haben ausserhalb
         * der Safari nichts zu sagen, der Rest laeuft ueberall in SkyBlock.
         */
        /**
         * Passt der Kasten zu dem, was gerade auf dem Bildschirm ist?
         *
         * Manche Angaben will man nur beim Blick ins Inventar sehen, andere nur
         * draussen. Ohne eigene Wahl gilt: immer.
         */
        /** Wonach im Einstellungsfenster gesucht wird, um bei diesem Kasten zu landen */
        public String settingsSearch() {
            return switch (this) {
                case PROGRESS, MISSING -> "Safari";
                case CONTEST -> "Contest";
                case NEARBY -> "Mob";
                case HUNTING -> "Hunting";
                case GUILD -> "Guild";
                case COLLECTION -> "Collection";
                case MINING -> "Mining HUD";
                case DAY -> "Day";
                case PET -> "Pet";
            };
        }

        public boolean showsNow() {
            ModConfig.HudCategory.HudWhen when = ModConfig.INSTANCE.hud.whenFor(name());
            if (when == ModConfig.HudCategory.HudWhen.ALWAYS) return true;
            boolean fensterOffen = net.minecraft.client.Minecraft.getInstance().screen != null;
            return when == ModConfig.HudCategory.HudWhen.INVENTORY ? fensterOffen : !fensterOffen;
        }

        public boolean showsHere() {
            Boolean chosen = ModConfig.INSTANCE.hud.allowsHere(name(), GameState.Server.map);
            if (chosen != null) return chosen;
            // Der Mining-Kasten folgt seiner eigenen Einstellung: nur in den Minen, oder
            // ueberall mit dem zuletzt gelesenen Stand
            if (this == MINING) {
                return ModConfig.INSTANCE.mining.hud.visibility == ModConfig.HudVisibility.EVERYWHERE
                        || com.shokiteufel.shokimod.scanner.MiningState.onMiningIsland();
            }
            if (this == DAY) {
                return ModConfig.INSTANCE.hud.day.visibility == ModConfig.HudVisibility.EVERYWHERE
                        || com.shokiteufel.shokimod.scanner.MiningState.onMiningIsland();
            }
            if (this == PET) {
                return ModConfig.INSTANCE.hud.pet.visibility == ModConfig.HudVisibility.EVERYWHERE
                        || com.shokiteufel.shokimod.scanner.MiningState.onMiningIsland();
            }
            return this != PROGRESS && this != MISSING || GameState.Server.isSafari();
        }

        public float x() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.progressHudX;
                case MISSING -> c.missingHudX;
                case CONTEST -> c.contestHudX;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.nearbyHudX;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.hudX;
                case GUILD -> ModConfig.INSTANCE.guild.events.hudX;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.hudX;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudX;
                case DAY -> ModConfig.INSTANCE.hud.day.hudX;
                case PET -> ModConfig.INSTANCE.hud.pet.hudX;
            };
        }

        public float y() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.progressHudY;
                case MISSING -> c.missingHudY;
                case CONTEST -> c.contestHudY;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.nearbyHudY;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.hudY;
                case GUILD -> ModConfig.INSTANCE.guild.events.hudY;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.hudY;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudY;
                case DAY -> ModConfig.INSTANCE.hud.day.hudY;
                case PET -> ModConfig.INSTANCE.hud.pet.hudY;
            };
        }

        public float scale() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.progressHudScale;
                case MISSING -> c.missingHudScale;
                case CONTEST -> c.contestHudScale;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.nearbyHudScale;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.hudScale;
                case GUILD -> ModConfig.INSTANCE.guild.events.hudScale;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.hudScale;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudScale;
                case DAY -> ModConfig.INSTANCE.hud.day.hudScale;
                case PET -> ModConfig.INSTANCE.hud.pet.hudScale;
            };
        }

        public void setPosition(float x, float y) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            switch (this) {
                case PROGRESS -> {
                    c.progressHudX = x;
                    c.progressHudY = y;
                }
                case MISSING -> {
                    c.missingHudX = x;
                    c.missingHudY = y;
                }
                case CONTEST -> {
                    c.contestHudX = x;
                    c.contestHudY = y;
                }
                case NEARBY -> {
                    ModConfig.INSTANCE.mobVisuals.nearbyHudX = x;
                    ModConfig.INSTANCE.mobVisuals.nearbyHudY = y;
                }
                case HUNTING -> {
                    ModConfig.INSTANCE.hunting.tracker.hudX = x;
                    ModConfig.INSTANCE.hunting.tracker.hudY = y;
                }
                case GUILD -> {
                    ModConfig.INSTANCE.guild.events.hudX = x;
                    ModConfig.INSTANCE.guild.events.hudY = y;
                }
                case COLLECTION -> {
                    ModConfig.INSTANCE.collections.tracker.hudX = x;
                    ModConfig.INSTANCE.collections.tracker.hudY = y;
                }
                case MINING -> {
                    ModConfig.INSTANCE.mining.hud.hudX = x;
                    ModConfig.INSTANCE.mining.hud.hudY = y;
                }
                case DAY -> {
                    ModConfig.INSTANCE.hud.day.hudX = x;
                    ModConfig.INSTANCE.hud.day.hudY = y;
                }
                case PET -> {
                    ModConfig.INSTANCE.hud.pet.hudX = x;
                    ModConfig.INSTANCE.hud.pet.hudY = y;
                }
            }
        }

        /** Deckkraft, 0.1 bis 1.0 */
        public float alpha() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.progressHudAlpha;
                case MISSING -> c.missingHudAlpha;
                case CONTEST -> c.contestHudAlpha;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.nearbyHudAlpha;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.hudAlpha;
                case GUILD -> ModConfig.INSTANCE.guild.events.hudAlpha;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.hudAlpha;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudOpacity;
                case DAY -> ModConfig.INSTANCE.hud.day.hudOpacity;
                case PET -> ModConfig.INSTANCE.hud.pet.hudOpacity;
            };
        }

        public void setAlpha(float alpha) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            // Ganz durchsichtig waere dasselbe wie aus, nur ohne dass man es merkt
            float clamped = Math.clamp(alpha, 0.1f, 1.0f);
            switch (this) {
                case PROGRESS -> c.progressHudAlpha = clamped;
                case MISSING -> c.missingHudAlpha = clamped;
                case CONTEST -> c.contestHudAlpha = clamped;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.nearbyHudAlpha = clamped;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.hudAlpha = clamped;
                case GUILD -> ModConfig.INSTANCE.guild.events.hudAlpha = clamped;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.hudAlpha = clamped;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudOpacity = clamped;
                case DAY -> ModConfig.INSTANCE.hud.day.hudOpacity = clamped;
                case PET -> ModConfig.INSTANCE.hud.pet.hudOpacity = clamped;
            }
        }

        public void setScale(float scale) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            float clamped = Math.clamp(scale, 0.5f, 3.0f);
            switch (this) {
                case PROGRESS -> c.progressHudScale = clamped;
                case MISSING -> c.missingHudScale = clamped;
                case CONTEST -> c.contestHudScale = clamped;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.nearbyHudScale = clamped;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.hudScale = clamped;
                case GUILD -> ModConfig.INSTANCE.guild.events.hudScale = clamped;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.hudScale = clamped;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudScale = clamped;
                case DAY -> ModConfig.INSTANCE.hud.day.hudScale = clamped;
                case PET -> ModConfig.INSTANCE.hud.pet.hudScale = clamped;
            }
        }

        /**
         * Der fertige Kasten, gepuffert.
         *
         * Gezeichnet wird in jedem Bild. Neu gebaut werden muss dafuer nichts: die
         * Inhalte aendern sich hoechstens im Sekundentakt, das Ausmessen der Zeilen
         * ist aber teuer. Viermal je Sekunde genuegt - auch fuer die Uhr im
         * Contest-Kasten, die im Sekundentakt weiterzaehlt.
         */
        public HudPanel build() {
            long now = System.currentTimeMillis();
            HudPanel panel = cache.get(this);
            if (panel != null && now - cachedAt.getOrDefault(this, 0L) < REBUILD_INTERVAL_MILLIS) {
                return panel;
            }

            panel = rebuild();
            cache.put(this, panel);
            cachedAt.put(this, now);
            return panel;
        }

        private HudPanel rebuild() {
            return switch (this) {
                case PROGRESS -> ProgressHud.build();
                case MISSING -> MissingHud.build();
                case CONTEST -> ContestHud.build();
                case NEARBY -> NearbyHud.build();
                case HUNTING -> HuntingHud.build();
                case GUILD -> GuildEventHud.build();
                case COLLECTION -> CollectionHud.build();
                case MINING -> MiningHud.build();
                case DAY -> DayHud.build();
                case PET -> PetHud.build();
            };
        }
    }

    /** Hoechstens viermal je Sekunde neu bauen */
    private static final long REBUILD_INTERVAL_MILLIS = 250L;

    /**
     * Einmal geholt statt je Bild: values() gibt jedes Mal eine frische Kopie zurueck,
     * und diese Schleife laeuft in jedem Bild - zweimal, sobald ein Fenster offen ist
     */
    private static final Panel[] PANELS = Panel.values();

    private static final Map<Panel, HudPanel> cache = new EnumMap<>(Panel.class);
    private static final Map<Panel, Long> cachedAt = new EnumMap<>(Panel.class);

    private SafariHud() {
    }

    /** Die Kaesten, ohne bei jedem Aufruf eine neue Kopie anzulegen */
    public static Panel[] panels() {
        return PANELS;
    }

    /** Nach einer Aenderung stimmt der gepufferte Kasten nicht mehr */
    public static void invalidate(Panel panel) {
        cache.remove(panel);
    }

    /** Beim Spielen: nur was eingeschaltet ist und an diesen Ort gehoert */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!GameState.Server.isSkyblock()) return;

        for (Panel panel : PANELS) {
            if (!panel.visible() || !panel.showsHere() || !panel.showsNow()) continue;
            draw(graphics, client.font, panel, panel.build());
        }
    }

    /** Zeichnet einen Kasten an seiner Stelle, in seiner Groesse */
    public static void draw(GuiGraphicsExtractor graphics, Font font, Panel panel, HudPanel content) {
        if (content.isEmpty()) return;

        float scale = panel.scale();
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX(panel), originY(panel));
        graphics.pose().scale(scale, scale);
        content.render(graphics, font, 0, 0, panel.alpha());
        graphics.pose().popMatrix();
    }

    public static int originX(Panel panel) {
        return Math.round(panel.x() * Minecraft.getInstance().getWindow().getGuiScaledWidth());
    }

    public static int originY(Panel panel) {
        return Math.round(panel.y() * Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    /** Groesse des Kastens auf dem Bildschirm, mit Skalierung */
    public static int scaledWidth(Panel panel, HudPanel content, Font font) {
        return Math.round(content.width(font) * panel.scale());
    }

    public static int scaledHeight(Panel panel, HudPanel content) {
        return Math.round(content.height() * panel.scale());
    }
}
