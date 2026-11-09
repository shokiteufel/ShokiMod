package com.shokiteufel.shokimod.paket;

/** Macht den Zuruecksetzer des Busses fuer Tests in anderen Paketen erreichbar (er ist bewusst nicht oeffentlich). */
public final class PaketTestHilfe {
    private PaketTestHilfe() {
    }

    public static void zuruecksetzen() {
        PaketBus.zuruecksetzenFuerTests();
    }
}
