package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;

import java.lang.reflect.Method;

/**
 * Erzwingt ein Partikel - fuer "Show always".
 *
 * Sodium Extra schaltet Partikel je Art ab, indem es {@code ParticleEngine.createParticle} gleich am Anfang abbricht.
 * Wer dort einen Schalter "Dust aus" hat, sieht auch den Staub der Divan-Ruestung nicht - egal, was diese Mod
 * einstellt. Fuer ein Item, das auf "Show always" steht, soll das nicht gelten.
 *
 * So geht es: Der Mixin an {@code handleParticleEvent} setzt die Markierung ({@link #arm()}), sobald ein Paket zu so
 * einem Item gehoert, und nimmt sie nach der Verarbeitung wieder weg. Solange sie steht, greift der Einsprung
 * {@code ParticleEngineForceMixin} - mit hoeherer Prioritaet als Sodium Extra, also vor dessen Abbruch - und erzeugt das
 * Partikel selbst, genau so wie {@code createParticle} es tut: {@code makeParticle}, dann {@code add}.
 *
 * {@code makeParticle} ist privat und steht deshalb nur per Reflection im Zugriff - kein {@code @Shadow}: Ein Mixin, das auf
 * eine umbenannte Methode zeigt, laesst das Spiel beim Start abstuerzen; eine Reflection, die ins Leere greift, schaltet nur
 * dieses eine Feature ab ("Show always" tut dann nichts) und schreibt eine Zeile ins Log.
 */
public final class ParticleForce {

    private static boolean armed = false;
    private static boolean broken = false;
    private static Method make;
    private static Method add;
    /** Wie viele Partikel auf diesem Weg erzeugt wurden - fuer die Pruefung und den Mitschreiber */
    private static int created = 0;

    private ParticleForce() {
    }

    /** Die naechsten Partikel, die die Engine bekommt, werden erzwungen - bis {@link #disarm()} */
    public static void arm() {
        armed = true;
    }

    public static void disarm() {
        armed = false;
    }

    public static boolean armed() {
        return armed;
    }

    public static int created() {
        return created;
    }

    /** Ob das Erzwingen auf dieser Version geht (nein, wenn die Reflection nichts fand) */
    public static boolean works() {
        return !broken;
    }

    /**
     * Das Partikel selbst erzeugen und in die Engine legen. Null, wenn es nicht geht - dann laeuft das Original weiter,
     * und was Sodium Extra abbricht, bleibt abgebrochen.
     */
    public static Particle create(ParticleEngine engine, ParticleOptions options, double x, double y, double z,
                                  double xd, double yd, double zd) {
        if (broken) return null;
        try {
            if (make == null) {
                make = ParticleEngine.class.getDeclaredMethod("makeParticle", ParticleOptions.class,
                        double.class, double.class, double.class, double.class, double.class, double.class);
                make.setAccessible(true);
                add = ParticleEngine.class.getMethod("add", Particle.class);
            }
            Particle particle = (Particle) make.invoke(engine, options, x, y, z, xd, yd, zd);
            if (particle != null) {
                add.invoke(engine, particle);
                created++;
            }
            return particle;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            ShokiMod.LOGGER.warn("[Particles] Show always cannot create particles on its own on this version - it does nothing", e);
            return null;
        }
    }
}
