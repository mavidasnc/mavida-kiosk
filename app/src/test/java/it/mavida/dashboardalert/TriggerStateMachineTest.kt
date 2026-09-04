package it.mavida.dashboardalert

import it.mavida.dashboardalert.domain.trigger.TriggerStateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test della logica edge-trigger / cooldown / ripeti-finche'-vera
 * (spec §4). Il tempo e' un parametro: i test sono deterministici.
 */
class TriggerStateMachineTest {

    private fun machine(
        cooldownMs: Long = 300_000,
        repeatWhileTrue: Boolean = false,
        repeatIntervalMs: Long = 60_000,
    ) = TriggerStateMachine(cooldownMs, repeatWhileTrue, repeatIntervalMs)

    @Test
    fun `prima transizione falso-vero scatta`() {
        val m = machine()
        assertTrue(m.shouldFire(conditionTrue = true, nowMs = 1_000))
    }

    @Test
    fun `condizione persistente non scatta di nuovo (edge-trigger)`() {
        val m = machine()
        assertTrue(m.shouldFire(true, 1_000))
        assertFalse(m.shouldFire(true, 2_000))
        assertFalse(m.shouldFire(true, 999_000))
    }

    @Test
    fun `nuovo edge entro il cooldown non scatta`() {
        val m = machine(cooldownMs = 300_000)
        assertTrue(m.shouldFire(true, 1_000))
        assertFalse(m.shouldFire(false, 2_000))
        // Nuovo edge ma sono passati solo 100s dall'ultimo scatto: soppresso.
        assertFalse(m.shouldFire(true, 101_000))
    }

    @Test
    fun `nuovo edge dopo il cooldown scatta`() {
        val m = machine(cooldownMs = 300_000)
        assertTrue(m.shouldFire(true, 1_000))
        assertFalse(m.shouldFire(false, 2_000))
        // Edge a t=301s: cooldown (300s) rispettato.
        assertTrue(m.shouldFire(true, 301_000))
    }

    @Test
    fun `cooldown si misura dall ultimo scatto effettivo`() {
        val m = machine(cooldownMs = 300_000)
        assertTrue(m.shouldFire(true, 1_000))
        assertFalse(m.shouldFire(false, 2_000))
        assertFalse(m.shouldFire(true, 200_000)) // soppresso: non aggiorna lastFired
        assertFalse(m.shouldFire(false, 210_000))
        // L'ultimo scatto resta a t=1s: a t=301s il cooldown e' passato.
        assertTrue(m.shouldFire(true, 301_000))
    }

    @Test
    fun `ripeti finche vera ogni N secondi`() {
        val m = machine(repeatWhileTrue = true, repeatIntervalMs = 60_000)
        assertTrue(m.shouldFire(true, 1_000))          // primo scatto
        assertFalse(m.shouldFire(true, 30_000))        // troppo presto
        assertTrue(m.shouldFire(true, 61_000))         // intervallo raggiunto
        assertFalse(m.shouldFire(true, 100_000))       // 39s dall'ultimo
        assertTrue(m.shouldFire(true, 121_000))        // altri 60s
    }

    @Test
    fun `ripeti finche vera si ferma quando la condizione torna falsa`() {
        val m = machine(repeatWhileTrue = true, repeatIntervalMs = 60_000)
        assertTrue(m.shouldFire(true, 1_000))
        assertFalse(m.shouldFire(false, 2_000))
        assertFalse(m.shouldFire(false, 70_000))
        // Nuovo edge: con cooldown di default (300s) non e' passato -> soppresso.
        assertFalse(m.shouldFire(true, 80_000))
    }

    @Test
    fun `reset riporta la macchina allo stato iniziale`() {
        val m = machine()
        assertTrue(m.shouldFire(true, 1_000))
        assertFalse(m.shouldFire(true, 2_000))
        m.reset()
        // Dopo il reset il primo "vero" scatta di nuovo (stato iniziale).
        assertTrue(m.shouldFire(true, 3_000))
    }
}
