package com.shokiteufel.shokimod.paket.zustand;

/** Macht Anmelden und Zuruecksetzen der Spiegel fuer Tests in anderen Paketen erreichbar (beides ist bewusst nicht oeffentlich). */
public final class ZustandTestHilfe {
    private ZustandTestHilfe() {
    }

    public static void anmelden() {
        PaketZustaende.anmelden();
    }

    public static void zuruecksetzen() {
        PaketZustaende.zuruecksetzenFuerTests();
    }
}
