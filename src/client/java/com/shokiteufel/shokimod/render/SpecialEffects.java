package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.BannerDesign;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Der grosse Auftritt neben dem Banner: das Stueck fliegt auf, Funken steigen.
 *
 * Zwei Dinge, die das Spiel schon kann und die kein Banner zeichnen kann, weil sie
 * nicht in seinem Kasten stattfinden. Das Stueck fliegt gross ueber den Bildschirm -
 * dieselbe Vorstellung, die der Totem der Unsterblichkeit macht - und um den Spieler
 * steigen Funken auf.
 *
 * Beides gehoert zum {@link BannerDesign} und nicht zur Stufe: Wer sein Banner
 * weitergibt, gibt den ganzen Auftritt weiter. Der Ton bleibt bei der Stufe, wo er
 * schon immer stand; zwei Toene aus zwei Quellen laegen sonst uebereinander.
 *
 * Angeregt von Skyblockers Special Effects (LGPL-3.0, hysky). Uebernommen ist die
 * Erkenntnis, welche zwei Spielfunktionen das koennen - eine Tatsache ueber
 * Minecraft, kein Code.
 */
public final class SpecialEffects {

    /**
     * So dicht duerfen zwei Auftritte nicht aufeinander folgen.
     *
     * Aus einem Beutebuendel fallen fuenf Sachen im selben Augenblick. Ohne diese
     * Sperre startete die Flugbahn des Stuecks fuenfmal neu - zu sehen waere nur die
     * letzte gewesen - und fuenf Funkenquellen laegen uebereinander. Eine knappe
     * halbe Sekunde reicht: Wer die Banner nacheinander zeigen laesst, hat zwischen
     * zwei Funden Sekunden, und dort greift sie nie.
     */
    private static final long COOLDOWN_MILLIS = 400L;

    private static long lastAt = 0L;

    private SpecialEffects() {
    }

    /**
     * Spielt, was das Design will - oder nichts.
     *
     * @param icon das Stueck, das fliegen soll; leer heisst: nur Funken
     */
    public static void play(BannerDesign design, ItemStack icon) {
        if (design == null) return;
        boolean flug = design.itemFlourish && icon != null && !icon.isEmpty();
        BannerDesign.Particles funken = design.particles == null
                ? BannerDesign.Particles.NONE : design.particles;
        if (!flug && funken == BannerDesign.Particles.NONE) return;

        long now = Util.getMillis();
        if (now - lastAt < COOLDOWN_MILLIS) return;
        lastAt = now;

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player == null || client.level == null) return;
            if (flug && client.gameRenderer != null) {
                client.gameRenderer.displayItemActivation(icon.copy());
            }
            ParticleOptions art = particleFor(funken);
            if (art != null && client.particleEngine != null) {
                client.particleEngine.createTrackingEmitter(client.player, art,
                        Math.clamp(design.particleTicks, 1, 100));
            }
        });
    }

    /**
     * Den Funken zum Namen finden.
     *
     * Nachgeschlagen statt fest verdrahtet: Das Design traegt nur den Namen mit sich,
     * damit ein geteiltes Design nichts aus Minecraft in der Zwischenablage hat.
     * Kennt das Spiel den Namen nicht - eine spaetere Fassung raeumt auf -, bleibt es
     * still, statt mit einer Ausnahme mitten im Fund abzubrechen.
     *
     * Nur die einfachen Arten kommen in Frage. Staub und Bloecke brauchen zusaetzlich
     * eine Farbe oder einen Block; ohne den waeren sie nicht zu bauen.
     */
    private static ParticleOptions particleFor(BannerDesign.Particles choice) {
        if (choice == null || choice.particle.isEmpty()) return null;
        Identifier id = Identifier.tryParse("minecraft:" + choice.particle);
        if (id == null) return null;
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        return type instanceof SimpleParticleType simple ? simple : null;
    }
}
