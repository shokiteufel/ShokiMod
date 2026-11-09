package com.shokiteufel.shokimod.paket;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Prueft, wie die Diagnose-Datei gelesen wird (der Schalter fuer die Diagnose ohne Startoptionen). */
class PaketDiagnoseTest {
    @Test
    void theFirstLineWithContentThatIsNoCommentIsTheMode() {
        assertEquals("zaehlen,alle", PaketDiagnose.modusAusZeilen(List.of("# Kommentar", "", "  Zaehlen,Alle  ", "ablauf")));
    }

    @Test
    void aByteOrderMarkBeforeACommentDoesNotTurnTheCommentIntoAMode() {
        // Genau der Fall aus dem ersten 26.1.2-Test: PowerShell und Notepad schreiben eine BOM an den Dateianfang
        assertEquals("zaehlen,ablauf", PaketDiagnose.modusAusZeilen(List.of("﻿# Diagnose der Paket-Schicht", "zaehlen,ablauf")));
    }

    @Test
    void aByteOrderMarkDirectlyBeforeTheModeIsIgnored() {
        assertEquals("zaehlen", PaketDiagnose.modusAusZeilen(List.of("﻿zaehlen")));
    }

    @Test
    void aFileWithOnlyCommentsAndBlanksSwitchesTheDiagnosisOff() {
        assertEquals("", PaketDiagnose.modusAusZeilen(List.of("# nur Kommentar", "   ", "")));
        assertEquals("", PaketDiagnose.modusAusZeilen(List.of()));
    }
}
