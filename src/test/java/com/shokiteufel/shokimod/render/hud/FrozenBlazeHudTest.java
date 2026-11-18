package com.shokiteufel.shokimod.render.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Der Aura-Zeitgeber im Kasten: Wortlaut und Farbstufen (gruen ueber zehn Sekunden, gelb bis zehn, rot bis fuenf und aus) */
class FrozenBlazeHudTest {

    @Test
    void theColourFollowsTheSecondsLeft() {
        assertEquals(HudColours.GREEN, FrozenBlazeHud.colourFor(30));
        assertEquals(HudColours.GREEN, FrozenBlazeHud.colourFor(11));
        assertEquals(HudColours.YELLOW, FrozenBlazeHud.colourFor(10));
        assertEquals(HudColours.YELLOW, FrozenBlazeHud.colourFor(6));
        assertEquals(HudColours.RED, FrozenBlazeHud.colourFor(5));
        assertEquals(HudColours.RED, FrozenBlazeHud.colourFor(1));
        assertEquals(HudColours.RED, FrozenBlazeHud.colourFor(0));
    }

    @Test
    void theValueShowsSecondsOrTheHintWhenOff() {
        assertEquals("27s", FrozenBlazeHud.valueFor(27));
        assertEquals("1s", FrozenBlazeHud.valueFor(1));
        assertEquals("OFF - move!", FrozenBlazeHud.valueFor(0));
    }
}
