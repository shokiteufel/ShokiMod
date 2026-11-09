package com.shokiteufel.shokimod.paket.zustand;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Fehler von Hoerern der Zustandsspiegel. Jedes Feature hoert fuer sich: Wirft eines, werden die anderen trotzdem
 * bedient und der Spiegel selbst bleibt unversehrt ("Mod darf nie abstuerzen"). Gemeldet werden nur die ersten Fehler.
 */
final class HoererFehler {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_GEMELDET = 10;
    private static int gemeldet;

    private HoererFehler() {
    }

    static void melden(String quelle, Throwable fehler) {
        if (gemeldet++ < MAX_GEMELDET) {
            LOG.error("ShokiMod Pakete: Ein Hoerer von '{}' warf einen Fehler (die anderen werden weiter bedient)", quelle, fehler);
        }
    }

    /** Nur fuer Tests. */
    static int gemeldetFuerTests() {
        return gemeldet;
    }
}
