package no.nav.amt.tiltak.deltaker.service

import no.nav.amt.tiltak.clients.amt_person.AmtPersonClient
import no.nav.amt.tiltak.core.domain.tiltak.Bruker
import no.nav.amt.tiltak.core.domain.tiltak.IdentType
import no.nav.amt.tiltak.core.domain.tiltak.NavEnhet
import no.nav.amt.tiltak.core.port.BrukerService
import no.nav.amt.tiltak.core.port.NavEnhetService
import no.nav.amt.tiltak.core.port.PersonService
import no.nav.amt.tiltak.deltaker.dbo.BrukerDbo
import no.nav.amt.tiltak.deltaker.repositories.BrukerRepository
import no.nav.amt.tiltak.log.SecureLog.secureLog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BrukerServiceImpl(
	private val brukerRepository: BrukerRepository,
	private val personService: PersonService,
	private val navEnhetService: NavEnhetService,
	private val amtPersonClient: AmtPersonClient,
) : BrukerService {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun oppdaterPersonIdenter(gjeldendeIdent: String, identType: IdentType, historiskeIdenter: List<String>) {
		val brukere = brukerRepository.getBrukere(historiskeIdenter.plus(gjeldendeIdent))

		if(brukere.size > 1) {
			secureLog.error("Vi har flere brukere knyttet til ident: $gjeldendeIdent, historiske identer:$historiskeIdenter")
			log.error("Vi har flere brukere knyttet til samme person")
			throw IllegalStateException("Vi har flere brukere knyttet til samme person")
		}

		brukere.firstOrNull()?.let { bruker ->
			brukerRepository.oppdaterIdenter(bruker.id, gjeldendeIdent, identType, historiskeIdenter)
		}
	}

	override fun slettBruker(id: UUID) {
		brukerRepository.slettBruker(id)
	}

	override fun slettBruker(personIdent: String) {
		brukerRepository.slettBruker(personIdent)
	}

	override fun getIdOrCreate(fodselsnummer: String): UUID {
		return brukerRepository.get(fodselsnummer)?.id ?: createBruker(fodselsnummer)
	}

	override fun hentBruker(id: UUID): Bruker {
		val brukerDbo = brukerRepository.get(id) ?: throw NoSuchElementException("Fant ikke bruker med id: $id")
		return Bruker(
			id = brukerDbo.id ,
			personIdent = brukerDbo.personIdent,
			personIdentType = brukerDbo.personIdentType,
			historiskeIdenter = brukerDbo.historiskeIdenter,
			fornavn = brukerDbo.fornavn,
			mellomnavn = brukerDbo.mellomnavn,
			etternavn = brukerDbo.etternavn,
			telefonnummer = brukerDbo.telefonnummer,
			epost = brukerDbo.epost,
			ansvarligVeilederId = brukerDbo.ansvarligVeilederId,
			navEnhetId = brukerDbo.navEnhetId,
			erSkjermet = brukerDbo.erSkjermet,
		)
	}

	override fun hentBruker(personIdent: String): Bruker? {
		val brukerDbo = brukerRepository.get(personIdent)
		return brukerDbo?.let {
			Bruker(
				id = it.id,
				personIdent = it.personIdent,
				personIdentType = it.personIdentType,
				historiskeIdenter = it.historiskeIdenter,
				fornavn = it.fornavn,
				mellomnavn = it.mellomnavn,
				etternavn = it.etternavn,
				telefonnummer = it.telefonnummer,
				epost = it.epost,
				ansvarligVeilederId = it.ansvarligVeilederId,
				navEnhetId = it.navEnhetId,
				erSkjermet = it.erSkjermet,
			)
		}
	}

	override fun finnesBruker(personIdent: String): Boolean {
		return brukerRepository.get(personIdent) != null
	}

	override fun oppdaterAnsvarligVeileder(personIdent: String, navAnsattId: UUID) {
		brukerRepository.oppdaterVeileder(personIdent, navAnsattId)
	}

	override fun oppdaterNavEnhet(personIdent: String, navEnhet: NavEnhet?) {
		val bruker = brukerRepository.get(personIdent)
			?: throw IllegalStateException("Kan ikke oppdatere nav enhet. Fant ikke bruker")
		if (bruker.navEnhetId == navEnhet?.id) return
		brukerRepository.oppdaterNavEnhet(personIdent, navEnhet?.id)
	}

	override fun settErSkjermet(personIdent: String, erSkjermet: Boolean) {
		val erBrukerSkjermet = erSkjermet(personIdent)
		if (erSkjermet == erBrukerSkjermet) return
		brukerRepository.settSkjermet(personIdent, erSkjermet)
	}

	override fun erSkjermet(personIdent: String): Boolean {
		val bruker = brukerRepository.get(personIdent)

		if (bruker == null) {
			secureLog.warn("Kan ikke sjekke om bruker er skjermet. Fant ikke bruker med personIdent=$personIdent")
			throw IllegalStateException("Kan ikke sjekke om bruker er skjermet. Fant ikke bruker")
		}

		return bruker.erSkjermet
	}

	override fun updateBrukerByPersonIdent(personIdent: String, fornavn: String, mellomnavn: String?, etternavn: String) {
		val bruker = brukerRepository.get(personIdent)

		if(bruker != null) {
			val kontaktinformasjon = personService.hentPersonKontaktinformasjon(bruker.personIdent)

			val newBruker = bruker.copy(
				fornavn = fornavn,
				mellomnavn = mellomnavn,
				etternavn = etternavn,
				telefonnummer = kontaktinformasjon.telefonnummer,
				epost = kontaktinformasjon.epost
			)

			updateBrukerData(bruker, newBruker)
		}

	}

	override fun logSkjermedeBrukere() {
		var offset = 0
		var brukere: List<BrukerDbo>
		log.info("--- Logger Brukere START ---")
		do {
			brukere = brukerRepository.getBrukere(offset)

			brukere.forEach { bruker ->
				val person = personService.hentPerson(bruker.personIdent)

				if (person.diskresjonskode != null) {
					log.info("BrukerId har diskresjonskode: ${bruker.id}")
				}
			}

			log.info("Sjekket brukere mellom $offset og ${offset + brukere.size}")
			offset += brukere.size
		} while (brukere.isNotEmpty())
		log.info("---- Logger Brukere END ----")
	}

	override fun updateBrukerByPersonIdent(brukerId: UUID): Boolean {
		return brukerRepository.get(brukerId)
			?.let { getAndUpdateBrukerData(it) }
			?: throw NoSuchElementException("Bruker med id $brukerId eksisterer ikke")
	}

	override fun updateAllBrukere() {
		var brukereOppdatert = 0
		var offset = 0
		var brukere: List<BrukerDbo>

		log.info("Oppdaterer brukerinformasjon")
		do {
			brukere = brukerRepository.getBrukere(offset)

			brukere.forEach { bruker -> getAndUpdateBrukerData(bruker).let { if (it) brukereOppdatert++ } }

			log.info("Sjekket brukere mellom $offset og ${offset + brukere.size}")
			offset += brukere.size
		} while (brukere.isNotEmpty())

		log.info("Brukere oppdatert. Det var $brukereOppdatert oppdateringer.")
	}

	override fun upsert(bruker: Bruker) {
		brukerRepository.upsert(bruker)
	}

	private fun createBruker(personident: String): UUID {
		val navBruker = amtPersonClient.hentNavBruker(personident).getOrThrow()

		navBruker.navEnhet?.let { navEnhetService.upsert(it) }
		brukerRepository.upsert(navBruker.toBruker())

		return navBruker.personId
	}

	private fun getAndUpdateBrukerData(bruker: BrukerDbo): Boolean {
		val person = personService.hentPerson(bruker.personIdent)
		val kontaktinformasjon = personService.hentPersonKontaktinformasjon(bruker.personIdent)

		val nyinnhentetBruker = bruker.copy(
			fornavn = person.fornavn,
			mellomnavn = person.mellomnavn,
			etternavn = person.etternavn,
			telefonnummer = person.telefonnummer ?: kontaktinformasjon.telefonnummer,
			epost = kontaktinformasjon.epost,
		)

		return updateBrukerData(bruker, nyinnhentetBruker)
	}

	private fun updateBrukerData(b1: BrukerDbo, b2: BrukerDbo): Boolean {
		val hasChanges = fun(b1: BrukerDbo, b2: BrukerDbo): Boolean {
			return b1.fornavn != b2.fornavn
				|| b1.mellomnavn != b2.mellomnavn
				|| b1.etternavn != b2.etternavn
				|| b1.telefonnummer != b2.telefonnummer
				|| b1.epost != b2.epost
		}

		if (hasChanges(b1, b2)) {
			log.info("Bruker med id ${b2.id} er blitt oppdatert")
			secureLog.info("Bruker med ident ${b2.personIdent} er blitt oppdatert")
			brukerRepository.upsert(b2.upsert())
			return true
		}

		return false

	}


}
