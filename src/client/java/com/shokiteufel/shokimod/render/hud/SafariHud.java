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
        PROGRESS, MISSING, CONTEST, NEARBY, HUNTING, GUILD, COLLECTION, PROFIT, MINING, PET, PERFORMANCE, SEATRACKER, EFFECTS, SEAHP, DARK_AUCTION, FIRE_VEIL;

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
                case PROFIT -> ModConfig.INSTANCE.profit.enabled && ModConfig.INSTANCE.profit.showHud;
                case MINING -> ModConfig.INSTANCE.mining.hud.showHud;
                case PET -> ModConfig.INSTANCE.hud.pet.showHud;
                case PERFORMANCE -> ModConfig.INSTANCE.hud.performance.showHud;
                case SEATRACKER -> ModConfig.INSTANCE.fishing.tracker.enabled && ModConfig.INSTANCE.fishing.tracker.showHud
                        && com.shokiteufel.shokimod.handler.SeaCreatureTracker.shownNow();
                case EFFECTS -> ModConfig.INSTANCE.effects.enabled && ModConfig.INSTANCE.effects.showHud
                        && EffectsHud.hasRows();
                // Nur wenn gerade einer in der Naehe steht - im HUD-Editor immer, sonst liesse er sich nicht setzen
                case SEAHP -> ModConfig.INSTANCE.fishing.hp.enabled
                        && (com.shokiteufel.shokimod.handler.SeaCreatureHp.hasRows()
                        || Minecraft.getInstance().gui.screen() instanceof com.shokiteufel.shokimod.gui.HudEditorScreen);
                // Immer oder nur kurz vor der Eroeffnung - je nach Einstellung; im HUD-Editor immer
                case DARK_AUCTION -> DarkAuctionHud.shownNow();
                // Waehrend des Zaubers und der Bereit-Anzeige; im HUD-Editor immer
                case FIRE_VEIL -> FireVeilHud.shownNow();
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
                case PROFIT -> "Profit";
                case MINING -> "Mining HUD";
                case PET -> "Pet";
                case PERFORMANCE -> "Performance";
                case SEATRACKER -> "Sea creature";
                case EFFECTS -> "Effects";
                case SEAHP -> "Sea creature HP";
                case DARK_AUCTION -> "Dark Auction";
                case FIRE_VEIL -> "Fire Veil";
            };
        }

        public boolean showsNow() {
            ModConfig.HudCategory.HudWhen when = ModConfig.INSTANCE.hud.whenFor(name());
            if (when == ModConfig.HudCategory.HudWhen.ALWAYS) return true;
            boolean fensterOffen = menuOpen();
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
            if (this == PET) {
                return ModConfig.INSTANCE.hud.pet.visibility == ModConfig.HudVisibility.EVERYWHERE
                        || com.shokiteufel.shokimod.scanner.MiningState.onMiningIsland();
            }
            if (this == PERFORMANCE) {
                return ModConfig.INSTANCE.hud.performance.visibility == ModConfig.HudVisibility.EVERYWHERE
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
                case PROFIT -> ModConfig.INSTANCE.profit.hudX;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudX;
                case PET -> ModConfig.INSTANCE.hud.pet.hudX;
                case PERFORMANCE -> ModConfig.INSTANCE.hud.performance.hudX;
                case SEATRACKER -> ModConfig.INSTANCE.fishing.tracker.hudX;
                case EFFECTS -> ModConfig.INSTANCE.effects.hudX;
                case SEAHP -> ModConfig.INSTANCE.fishing.hp.hudX;
                case DARK_AUCTION -> ModConfig.INSTANCE.chat.reminder.darkAuctionHudX;
                case FIRE_VEIL -> ModConfig.INSTANCE.iq.fireVeilHudX;
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
                case PROFIT -> ModConfig.INSTANCE.profit.hudY;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudY;
                case PET -> ModConfig.INSTANCE.hud.pet.hudY;
                case PERFORMANCE -> ModConfig.INSTANCE.hud.performance.hudY;
                case SEATRACKER -> ModConfig.INSTANCE.fishing.tracker.hudY;
                case EFFECTS -> ModConfig.INSTANCE.effects.hudY;
                case SEAHP -> ModConfig.INSTANCE.fishing.hp.hudY;
                case DARK_AUCTION -> ModConfig.INSTANCE.chat.reminder.darkAuctionHudY;
                case FIRE_VEIL -> ModConfig.INSTANCE.iq.fireVeilHudY;
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
                case PROFIT -> ModConfig.INSTANCE.profit.hudScale;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudScale;
                case PET -> ModConfig.INSTANCE.hud.pet.hudScale;
                case PERFORMANCE -> ModConfig.INSTANCE.hud.performance.hudScale;
                case SEATRACKER -> ModConfig.INSTANCE.fishing.tracker.hudScale;
                case EFFECTS -> ModConfig.INSTANCE.effects.hudScale;
                case SEAHP -> ModConfig.INSTANCE.fishing.hp.hudScale;
                case DARK_AUCTION -> ModConfig.INSTANCE.chat.reminder.darkAuctionHudScale;
                case FIRE_VEIL -> ModConfig.INSTANCE.iq.fireVeilHudScale;
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
                case PROFIT -> {
                    ModConfig.INSTANCE.profit.hudX = x;
                    ModConfig.INSTANCE.profit.hudY = y;
                }
                case MINING -> {
                    ModConfig.INSTANCE.mining.hud.hudX = x;
                    ModConfig.INSTANCE.mining.hud.hudY = y;
                }
                case PET -> {
                    ModConfig.INSTANCE.hud.pet.hudX = x;
                    ModConfig.INSTANCE.hud.pet.hudY = y;
                }
                case PERFORMANCE -> {
                    ModConfig.INSTANCE.hud.performance.hudX = x;
                    ModConfig.INSTANCE.hud.performance.hudY = y;
                }
                case SEATRACKER -> {
                    ModConfig.INSTANCE.fishing.tracker.hudX = x;
                    ModConfig.INSTANCE.fishing.tracker.hudY = y;
                }
                case EFFECTS -> {
                    ModConfig.INSTANCE.effects.hudX = x;
                    ModConfig.INSTANCE.effects.hudY = y;
                }
                case SEAHP -> {
                    ModConfig.INSTANCE.fishing.hp.hudX = x;
                    ModConfig.INSTANCE.fishing.hp.hudY = y;
                }
                case DARK_AUCTION -> {
                    ModConfig.INSTANCE.chat.reminder.darkAuctionHudX = x;
                    ModConfig.INSTANCE.chat.reminder.darkAuctionHudY = y;
                }
                case FIRE_VEIL -> {
                    ModConfig.INSTANCE.iq.fireVeilHudX = x;
                    ModConfig.INSTANCE.iq.fireVeilHudY = y;
                }
            }
        }

        /** Deckkraft des Untergrunds, 0.0 bis 1.0. Die Schrift bleibt davon unberuehrt */
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
                case PROFIT -> ModConfig.INSTANCE.profit.hudAlpha;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudOpacity;
                case PET -> ModConfig.INSTANCE.hud.pet.hudOpacity;
                case PERFORMANCE -> ModConfig.INSTANCE.hud.performance.hudOpacity;
                case SEATRACKER -> ModConfig.INSTANCE.fishing.tracker.hudAlpha;
                case EFFECTS -> ModConfig.INSTANCE.effects.hudAlpha;
                case SEAHP -> ModConfig.INSTANCE.fishing.hp.hudAlpha;
                case DARK_AUCTION -> ModConfig.INSTANCE.chat.reminder.darkAuctionHudAlpha;
                case FIRE_VEIL -> ModConfig.INSTANCE.iq.fireVeilHudAlpha;
            };
        }

        public void setAlpha(float alpha) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            // Null ist erlaubt: Das nimmt den Kasten weg, nicht den Text. Geschrieben
            // wird ohnehin in voller Deckkraft, die Zahl gilt nur fuer den Untergrund
            float clamped = Math.clamp(alpha, 0.0f, 1.0f);
            switch (this) {
                case PROGRESS -> c.progressHudAlpha = clamped;
                case MISSING -> c.missingHudAlpha = clamped;
                case CONTEST -> c.contestHudAlpha = clamped;
                case NEARBY -> ModConfig.INSTANCE.mobVisuals.nearbyHudAlpha = clamped;
                case HUNTING -> ModConfig.INSTANCE.hunting.tracker.hudAlpha = clamped;
                case GUILD -> ModConfig.INSTANCE.guild.events.hudAlpha = clamped;
                case COLLECTION -> ModConfig.INSTANCE.collections.tracker.hudAlpha = clamped;
                case PROFIT -> ModConfig.INSTANCE.profit.hudAlpha = clamped;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudOpacity = clamped;
                case PET -> ModConfig.INSTANCE.hud.pet.hudOpacity = clamped;
                case PERFORMANCE -> ModConfig.INSTANCE.hud.performance.hudOpacity = clamped;
                case SEATRACKER -> ModConfig.INSTANCE.fishing.tracker.hudAlpha = clamped;
                case EFFECTS -> ModConfig.INSTANCE.effects.hudAlpha = clamped;
                case SEAHP -> ModConfig.INSTANCE.fishing.hp.hudAlpha = clamped;
                case DARK_AUCTION -> ModConfig.INSTANCE.chat.reminder.darkAuctionHudAlpha = clamped;
                case FIRE_VEIL -> ModConfig.INSTANCE.iq.fireVeilHudAlpha = clamped;
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
                case PROFIT -> ModConfig.INSTANCE.profit.hudScale = clamped;
                case MINING -> ModConfig.INSTANCE.mining.hud.hudScale = clamped;
                case PET -> ModConfig.INSTANCE.hud.pet.hudScale = clamped;
                case PERFORMANCE -> ModConfig.INSTANCE.hud.performance.hudScale = clamped;
                case SEATRACKER -> ModConfig.INSTANCE.fishing.tracker.hudScale = clamped;
                case EFFECTS -> ModConfig.INSTANCE.effects.hudScale = clamped;
                case SEAHP -> ModConfig.INSTANCE.fishing.hp.hudScale = clamped;
                case DARK_AUCTION -> ModConfig.INSTANCE.chat.reminder.darkAuctionHudScale = clamped;
                case FIRE_VEIL -> ModConfig.INSTANCE.iq.fireVeilHudScale = clamped;
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
            if (panel != null && fresh(now)) {
                return panel;
            }

            panel = rebuild();
            cache.put(this, panel);
            cachedAt.put(this, this == FIRE_VEIL ? com.shokiteufel.shokimod.handler.FireVeil.tickTime() : now);
            return panel;
        }

        /**
         * Ist der gemerkte Kasten noch aktuell? Meist heisst das: hoechstens eine Viertelsekunde alt. Der Zeitkasten
         * des Fire Veil dagegen wird mit jedem Tick neu gebaut und nur dann: So zaehlen seine Hundertstel gleichmaessig
         * herunter, wie bei IQ, statt im Viertelsekundentakt zu springen.
         */
        private boolean fresh(long now) {
            long at = cachedAt.getOrDefault(this, 0L);
            return this == FIRE_VEIL
                    ? at == com.shokiteufel.shokimod.handler.FireVeil.tickTime()
                    : now - at < REBUILD_INTERVAL_MILLIS;
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
                case PROFIT -> ProfitHud.build();
                case MINING -> MiningHud.build();
                case PET -> PetHud.build();
                case PERFORMANCE -> PerformanceHud.build();
                case SEATRACKER -> SeaTrackerHud.build();
                case EFFECTS -> EffectsHud.build();
                case SEAHP -> SeaHpHud.build();
                case DARK_AUCTION -> DarkAuctionHud.build();
                case FIRE_VEIL -> FireVeilHud.build();
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

    /**
     * Verdeckt das, was gerade offen ist, die Sicht?
     *
     * Der Chat nicht: Er legt sich in eine Ecke, die Welt bleibt sichtbar, und wer
     * etwas tippt, will die Kaesten weiter sehen. Ein Inventar oder ein Menue schon.
     *
     * Diese eine Stelle beantwortet die Frage fuer alle - sonst laufen die
     * Zeichenwege auseinander, und ein Kasten faellt zwischen ihnen hindurch.
     */
    public static boolean menuOpen() {
        net.minecraft.client.gui.screens.Screen offen = Minecraft.getInstance().gui.screen();
        return offen != null
                && !(offen instanceof net.minecraft.client.gui.screens.ChatScreen);
    }

    /** Nach einer Aenderung stimmt der gepufferte Kasten nicht mehr */
    public static void invalidate(Panel panel) {
        cache.remove(panel);
    }

    /** Beim Spielen: nur was eingeschaltet ist und an diesen Ort gehoert */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return;
        if (!GameState.Server.isSkyblock()) return;

        for (Panel panel : PANELS) {
            if (!panel.visible() || !panel.showsHere() || !panel.showsNow()) continue;
            // Der Gilden-Kasten nur, wenn es etwas zu zeigen gibt: ein laufendes Event, oder
            // ein angekuendigtes, sofern die Einstellung das will. "No event running" in der
            // Ecke sagt nichts, was man wissen muesste. Hier und nicht in build(): Der
            // HUD-Editor baut den Kasten auch - dort muss er zum Platzieren sichtbar bleiben
            if (panel == Panel.GUILD && !com.shokiteufel.shokimod.handler.GuildEvents.panelWanted()) continue;
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
        // Einmal zeichnen, nicht zweimal.
        //
        // Der Kasten wird ohnehin nur einmal je Bild gezeichnet: ohne offenes Fenster
        // im gewoehnlichen Durchgang, mit offenem Fenster ueber dem Fenster - der
        // gewoehnliche Durchgang hoert dann vorher auf (HudRenderer). Eine zweite Lage
        // macht die Schrift nicht kraeftiger, sondern matschiger: der dunkle Umriss des
        // zweiten Durchgangs legt sich auf die Raender der Schrift des ersten
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
