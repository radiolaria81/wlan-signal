package de.radiolaria.wlansignal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiMathTest {

    @Test fun kanaele24() {
        assertEquals(1, WifiMath.channel(2412))
        assertEquals(6, WifiMath.channel(2437))
        assertEquals(13, WifiMath.channel(2472))
        assertEquals(14, WifiMath.channel(2484))
    }

    @Test fun kanaele5() {
        assertEquals(36, WifiMath.channel(5180))
        assertEquals(100, WifiMath.channel(5500))
        assertEquals(165, WifiMath.channel(5825))
    }

    @Test fun kanaele6() {
        assertEquals(1, WifiMath.channel(5955))
        assertEquals(2, WifiMath.channel(5935))
        assertEquals(233, WifiMath.channel(7115))
    }

    @Test fun unbekannt() {
        assertNull(WifiMath.channel(0))
        assertEquals(WifiMath.Band.UNKNOWN, WifiMath.band(0))
    }

    @Test fun baender() {
        assertEquals(WifiMath.Band.GHZ_2_4, WifiMath.band(2437))
        assertEquals(WifiMath.Band.GHZ_5, WifiMath.band(5180))
        assertEquals(WifiMath.Band.GHZ_6, WifiMath.band(5955))
    }

    @Test fun einstufung() {
        assertEquals(WifiMath.Rating.EXCELLENT, WifiMath.rating(-45))
        assertEquals(WifiMath.Rating.GOOD, WifiMath.rating(-60))
        assertEquals(WifiMath.Rating.FAIR, WifiMath.rating(-67))
        assertEquals(WifiMath.Rating.WEAK, WifiMath.rating(-75))
        assertEquals(WifiMath.Rating.POOR, WifiMath.rating(-76))
    }

    @Test fun balken() {
        assertEquals(0f, WifiMath.fraction(-110), 0f)
        assertEquals(1f, WifiMath.fraction(-20), 0f)
        assertEquals(0.5f, WifiMath.fraction(-65), 0.001f)
    }

    @Test fun verborgeneWerte() {
        assertNull(WifiMath.cleanSsid("<unknown ssid>"))
        assertEquals("Heimnetz", WifiMath.cleanSsid("\"Heimnetz\""))
        assertNull(WifiMath.cleanBssid("02:00:00:00:00:00"))
        assertEquals("aa:bb:cc:dd:ee:ff", WifiMath.cleanBssid("AA:BB:CC:DD:EE:FF"))
    }
}
