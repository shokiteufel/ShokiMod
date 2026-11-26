package com.shokiteufel.shokimod.render.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishingBaitHudTest {

    @Test
    void theLabelIsTheAmountInAquaAndThenTheNameInItsOwnColours() {
        assertEquals("§b49x §r§fCommon Bait", FishingBaitHud.label(49, "§fCommon Bait"));
    }

    @Test
    void bigAmountsGetThousandsSeparators() {
        assertEquals("§b1,234x §r§aUncommon Bait", FishingBaitHud.label(1234, "§aUncommon Bait"));
    }
}
