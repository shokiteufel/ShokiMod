package com.shokiteufel.shokimod.paket.anschluss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernAnschlussTest {

    @Test
    void onlyAusSwitchesTheKernOff() {
        assertFalse(KernAnschluss.ausgeschaltet(null));
        assertFalse(KernAnschluss.ausgeschaltet("an"));
        assertFalse(KernAnschluss.ausgeschaltet("beobachten"));
        assertTrue(KernAnschluss.ausgeschaltet(" AUS "));
    }
}
