package no.nav.amt.tiltak.tiltak.deltaker

import no.nav.amt.tiltak.core.domain.tiltak.Deltaker
import no.nav.amt.tiltak.core.port.PersonService
import no.nav.amt.tiltak.tiltak.deltaker.dbo.BrukerDbo
import no.nav.amt.tiltak.tiltak.deltaker.dbo.NavAnsattDbo
import no.nav.amt.tiltak.tiltak.deltaker.repositories.BrukerRepository
import no.nav.amt.tiltak.tiltak.deltaker.repositories.DeltakerRepository
import no.nav.amt.tiltak.tiltak.utils.UpdateStatus
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*

@Service
open class DeltakerService(
	private val deltakerRepository: DeltakerRepository,
	private val brukerRepository: BrukerRepository,
	private val personService: PersonService
) {

	fun addUpdateDeltaker(
		tiltaksinstans: UUID,
		fodselsnummer: String,
		oppstartDato: LocalDate?,
		sluttDato: LocalDate?,
		status: Deltaker.Status,
		arenaStatus: String?,
		dagerPerUke: Int?,
		prosentStilling: Float?
	): Deltaker {
		val storedDeltaker = deltakerRepository.get(fodselsnummer, tiltaksinstans)

		if (storedDeltaker != null) {
			return storedDeltaker.toDeltaker()
		}

		val bruker = getBruker(fodselsnummer)

		val deltaker = deltakerRepository.get(
			fodselsnummer = fodselsnummer,
			tiltaksinstans = tiltaksinstans
		)


		if (deltaker != null) {
			val updated = deltaker.update(
				newStatus = status,
				newDeltakerStartDato = oppstartDato,
				newDeltakerSluttDato = sluttDato
			)

			return if (updated.status == UpdateStatus.UPDATED) {
				deltakerRepository.update(updated.updatedObject!!).toDeltaker()
			} else {
				deltaker.toDeltaker()
			}
		} else {
			return deltakerRepository.insert(
				brukerId = bruker.internalId,
				tiltaksgjennomforing = tiltaksinstans,
				oppstartDato = oppstartDato,
				sluttDato = sluttDato,
				status = status,
				arenaStatus = arenaStatus,
				dagerPerUke = dagerPerUke,
				prosentStilling = prosentStilling
			).toDeltaker()

		}
	}

	private fun getBruker(fodselsnummer: String): BrukerDbo {
		val storedBruker = brukerRepository.get(fodselsnummer)

		if (storedBruker != null) {
			return storedBruker
		}

		val veileder = getVeileder(fodselsnummer)
		val newBruker = personService.hentPerson(fodselsnummer)

		return brukerRepository.insert(
			fodselsnummer = fodselsnummer,
			fornavn = newBruker.fornavn,
			mellomnavn = newBruker.mellomnavn,
			etternavn = newBruker.etternavn,
			telefonnummer = newBruker.telefonnummer,
			epost = null,
			ansvarligVeilederId = veileder?.internalId
		)

	}

	// @SONAR_START@
	private fun getVeileder(fodselsnummer: String): NavAnsattDbo? {
		return null
	}
	// @SONAR_STOP@

}
