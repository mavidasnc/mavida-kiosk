package it.mavida.dashboardalert

import it.mavida.dashboardalert.updater.AppUpdater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit test del confronto semver usato dall'auto-aggiornamento. */
class AppUpdaterTest {

    @Test
    fun `patch piu' alta e' piu' recente`() {
        assertTrue(AppUpdater.isNewer("0.2.4", "0.2.3"))
    }

    @Test
    fun `minor piu' alta e' piu' recente`() {
        assertTrue(AppUpdater.isNewer("0.3.0", "0.2.9"))
    }

    @Test
    fun `confronto numerico non lessicografico`() {
        assertTrue(AppUpdater.isNewer("0.10.0", "0.9.9"))
    }

    @Test
    fun `stessa versione non e' un aggiornamento`() {
        assertFalse(AppUpdater.isNewer("0.2.3", "0.2.3"))
    }

    @Test
    fun `versione piu' vecchia non e' un aggiornamento`() {
        assertFalse(AppUpdater.isNewer("0.2.2", "0.2.3"))
    }

    @Test
    fun `componenti mancanti contano come zero`() {
        assertFalse(AppUpdater.isNewer("1.0", "1.0.0"))
        assertTrue(AppUpdater.isNewer("1.0.1", "1.0"))
    }
}
