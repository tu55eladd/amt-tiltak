package no.nav.amt.tiltak.core.domain.tiltak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime


class DeltakerTest {

	val yesterday = LocalDate.now().minusDays(1)
	val tomorrow = LocalDate.now().plusDays(1)

	@Test
	fun `updatestatus - status oppdateres - endres til DELTAR`() {
		val deltaker = Deltaker(
			startDato = yesterday,
			sluttDato = tomorrow,
			statuser = DeltakerStatuser(listOf(DeltakerStatus.settAktiv(
					status = Deltaker.Status.VENTER_PA_OPPSTART,
					endretDato = LocalDateTime.now().minusWeeks(1)
			))),
			registrertDato = LocalDateTime.now().minusWeeks(1),
		)
		val updatedDeltaker = deltaker.update(Deltaker.Status.DELTAR, yesterday, tomorrow, LocalDateTime.now())

		assertNotEquals(updatedDeltaker, deltaker)
		assertEquals(updatedDeltaker.status, Deltaker.Status.DELTAR)
	}


	@Test
	fun `updatestatus - startdato endres - endres til tomorrow`() {
		val deltaker = Deltaker(
			startDato = yesterday,
			sluttDato = LocalDate.now().plusWeeks(1),
			statuser = DeltakerStatuser(listOf(DeltakerStatus(
				status = Deltaker.Status.VENTER_PA_OPPSTART,
				endretDato = LocalDateTime.now().minusWeeks(1),
				aktiv = true
			))),
			registrertDato = LocalDateTime.now().minusWeeks(1),
		)
		val updatedDeltaker = deltaker.update(Deltaker.Status.VENTER_PA_OPPSTART, tomorrow,  LocalDate.now().plusWeeks(1), LocalDateTime.now())

		assertNotEquals(updatedDeltaker, deltaker)
		assertEquals(updatedDeltaker.startDato, tomorrow)
	}

	@Test
	fun `updatestatus - sluttdatodato endres - endres til tomorrow`() {
		val deltaker = Deltaker(
			startDato = LocalDate.now().minusWeeks(1),
			sluttDato = yesterday,
			statuser = DeltakerStatuser(listOf(DeltakerStatus.settAktiv(
				status = Deltaker.Status.DELTAR,
				endretDato = LocalDateTime.now().minusWeeks(1),
			))),
			registrertDato = LocalDateTime.now().minusWeeks(1),
		)
		val updatedDeltaker = deltaker.update(Deltaker.Status.DELTAR, LocalDate.now().minusWeeks(1),  tomorrow, LocalDateTime.now())

		assertNotEquals(updatedDeltaker, deltaker)
		assertEquals(updatedDeltaker.sluttDato, tomorrow)
	}

	@Test
	fun `updatestatus - status, startdato og sluttdato ikke uendret - forblir uendret`() {
		val deltaker = Deltaker(
			startDato = LocalDate.now().minusWeeks(1),
			sluttDato = yesterday,
			statuser = DeltakerStatuser(listOf(DeltakerStatus.settAktiv(
				status = Deltaker.Status.DELTAR,
				endretDato = LocalDateTime.now().minusWeeks(1),
			))),
			registrertDato = LocalDateTime.now().minusWeeks(1),
		)

		val updatedDeltaker = deltaker.update(
			Deltaker.Status.DELTAR,
			LocalDate.now().minusWeeks(1),
			yesterday,
			LocalDateTime.now()
		)

		assertEquals(updatedDeltaker, deltaker)
	}

}