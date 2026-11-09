package com.shokiteufel.shokimod.util;

import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSamplerTest {

    private static StackTraceElement frame(String klasse, String methode, int zeile) {
        return new StackTraceElement(klasse, methode, klasse.substring(klasse.lastIndexOf('.') + 1) + ".java", zeile);
    }

    @Test
    void theOutermostShokiModFrameIsTheEntryPointAndTheInnermostIsWhereTheTimeLay() {
        PerfSampler.Fenster f = new PerfSampler.Fenster();
        // innen -> aussen: ein Minecraft-Aufruf, eine tiefe ShokiMod-Methode, die ShokiMod-Methode, die sie anstiess, dann das Spiel
        StackTraceElement[] stapel = {
                frame("net.minecraft.world.entity.Entity", "getType", 1),
                frame("com.shokiteufel.shokimod.render.EntityHighlightManager", "registerBox", 410),
                frame("com.shokiteufel.shokimod.render.EntityHighlightManager", "tick", 300),
                frame("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents", "lambda$static$0", 1),
                frame("net.minecraft.client.Minecraft", "tick", 1)};
        f.abtasten(stapel);
        f.abtasten(stapel);

        assertEquals(2, f.proben);
        assertEquals(2, f.mod);
        assertEquals(2, f.einstieg.get("render.EntityHighlightManager.tick"));
        assertEquals(2, f.innerste.get("render.EntityHighlightManager.registerBox:410"));
    }

    @Test
    void aStackWithoutShokiModCountsAsOutsideAndNamesItsTopFrame() {
        PerfSampler.Fenster f = new PerfSampler.Fenster();
        f.abtasten(new StackTraceElement[] {
                frame("com.mojang.blaze3d.systems.RenderSystem", "flipFrame", 1),
                frame("net.minecraft.client.Minecraft", "runTick", 1)});

        assertEquals(1, f.proben);
        assertEquals(0, f.mod);
        assertEquals(1, f.fremd.get("com.mojang.blaze3d.systems.RenderSystem.flipFrame"));
    }

    @Test
    void longTicksAreCountedAndTheLongestIsKept() {
        PerfSampler.Fenster f = new PerfSampler.Fenster();
        f.tick(10_000_000L);
        f.tick(80_000_000L);
        f.tick(212_000_000L);

        assertEquals(3, f.ticks);
        assertEquals(2, f.langeTicks, "nur Ticks ueber 50 ms");
        assertEquals(212_000_000L, f.laengsterTick);
    }

    @Test
    void theReportNamesTheBusiestPlaceFirstAndTheShare() {
        PerfSampler.Fenster f = new PerfSampler.Fenster();
        StackTraceElement[] schwer = {frame("com.shokiteufel.shokimod.dungeonkarte.WeltScanner", "durchgang", 77),
                frame("com.shokiteufel.shokimod.dungeonkarte.FunnyKarte", "tick", 240)};
        StackTraceElement[] leicht = {frame("com.shokiteufel.shokimod.handler.MistBoden", "tick", 55)};
        for (int i = 0; i < 9; i++) f.abtasten(schwer);
        f.abtasten(leicht);
        f.abtasten(new StackTraceElement[] {frame("net.minecraft.client.Minecraft", "runTick", 1)});

        String bericht = f.bericht("2 runs, 40 ms", "180 MB/s");

        assertTrue(bericht.contains("11 samples, ShokiMod 90.9 % (10)"), bericht);
        assertTrue(bericht.contains("GC: 2 runs, 40 ms"), bericht);
        assertTrue(bericht.contains("game thread allocates 180 MB/s"), bericht);
        assertTrue(bericht.indexOf("dungeonkarte.FunnyKarte.tick") < bericht.indexOf("handler.MistBoden.tick"), "das Schwerste steht oben:\n" + bericht);
        assertTrue(bericht.contains("dungeonkarte.WeltScanner.durchgang:77"), bericht);
    }

    @Test
    void framesAreCountedWithTheSlowOnesAndTheLongestGap() {
        PerfSampler.Fenster f = new PerfSampler.Fenster();
        f.bild(7_000_000L);    // 143 Bilder je Sekunde
        f.bild(40_000_000L);   // unter 30
        f.bild(90_000_000L);   // unter 20
        f.bild(10_000_000L);

        assertEquals(4, f.bilder);
        assertEquals(2, f.bilderUeber33);
        assertEquals(1, f.bilderUeber50);
        assertEquals(90_000_000L, f.laengstesBild);
        String bericht = f.bericht("0 runs, 0 ms", "n/a");
        assertTrue(bericht.contains("frames: 4"), bericht);
        assertTrue(bericht.contains("over 33 ms: 2, over 50 ms: 1, longest 90 ms"), bericht);
    }

    /** Der Aufruf, den die Probe an einem laufenden Faden sieht */
    private static final class Dreher implements Runnable {
        volatile boolean weiter = true;

        @Override
        public void run() {
            long summe = 0;
            while (weiter) {
                summe += rechnen(summe);
            }
        }

        private static long rechnen(long x) {
            long s = x;
            for (int i = 0; i < 20_000; i++) s = s * 31 + i;
            return s & 7;
        }
    }

    @Test
    void aLiveThreadBusyInAShokiModMethodIsFoundByItsStack() {
        Dreher dreher = new Dreher();
        Thread t = new Thread(dreher, "test-dreher");
        t.setDaemon(true);
        t.start();
        PerfSampler.Fenster f = new PerfSampler.Fenster();
        for (int i = 0; i < 60; i++) {
            // ereignis-ok: Abtasten ist zeitgesteuert, es gibt kein Ereignis, auf das man warten koennte
            LockSupport.parkNanos(5_000_000L);
            f.abtasten(t.getStackTrace());
        }
        dreher.weiter = false;

        // Die Testklasse liegt im Paket von ShokiMod: der Dreher ist "ShokiMod" und wird als Einstieg genannt
        assertTrue(f.mod > 20, "der Faden war fast immer in ShokiMod-Code, gezaehlt: " + f.mod + " von " + f.proben);
        assertTrue(f.einstieg.keySet().stream().anyMatch(k -> k.contains("PerfSamplerTest$Dreher.run")), f.einstieg.toString());
    }
}
