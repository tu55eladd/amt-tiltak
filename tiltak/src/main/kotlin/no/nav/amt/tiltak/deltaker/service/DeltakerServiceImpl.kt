package no.nav.amt.tiltak.deltaker.service

import no.nav.amt.tiltak.core.domain.tiltak.*
import no.nav.amt.tiltak.core.kafka.KafkaProducerService
import no.nav.amt.tiltak.core.port.DeltakerService
import no.nav.amt.tiltak.core.port.EndringsmeldingService
import no.nav.amt.tiltak.deltaker.dbo.*
import no.nav.amt.tiltak.deltaker.repositories.DeltakerRepository
import no.nav.amt.tiltak.deltaker.repositories.DeltakerStatusRepository
import no.nav.amt.tiltak.deltaker.repositories.SkjultDeltakerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
open class DeltakerServiceImpl(
	private val deltakerRepository: DeltakerRepository,
	private val deltakerStatusRepository: DeltakerStatusRepository,
	private val brukerService: BrukerService,
	private val endringsmeldingService: EndringsmeldingService,
	private val skjultDeltakerRepository: SkjultDeltakerRepository,
	private val transactionTemplate: TransactionTemplate,
	private val kafkaProducerService: KafkaProducerService
) : DeltakerService {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun upsertDeltaker(personIdent: String, deltaker: DeltakerUpsert) {
		val lagretDeltaker = hentDeltaker(deltaker.id)

		transactionTemplate.executeWithoutResult {
			if (lagretDeltaker == null) {
				insertDeltaker(personIdent, deltaker)
			} else if(!deltaker.compareTo(lagretDeltaker)){
				update(deltaker)
			}

			oppdaterStatus(deltaker.statusInsert)

			val oppdatertDeltaker = hentDeltaker(deltaker.id)
				?: throw IllegalStateException("Fant ikke deltaker med id ${deltaker.id}")

			kafkaProducerService.publiserDeltaker(oppdatertDeltaker)
		}

	}

	override fun insertStatus(status: DeltakerStatusInsert) {
		transactionTemplate.executeWithoutResult {
			oppdaterStatus(status)

			val oppdatertDeltaker = hentDeltaker(status.deltakerId)
				?: throw IllegalStateException("Fant ikke deltaker med id ${status.deltakerId}")

			kafkaProducerService.publiserDeltaker(oppdatertDeltaker)
		}
	}

	override fun hentDeltakerePaaGjennomforing(gjennomforingId: UUID): List<Deltaker> {
		val deltakere = deltakerRepository.getDeltakerePaaTiltak(gjennomforingId)
		return mapDeltakereOgAktiveStatuser(deltakere)
	}

	override fun hentDeltaker(deltakerId: UUID): Deltaker? {
		val deltaker = deltakerRepository.get(deltakerId)
			?: return null

		return deltaker.toDeltaker(hentStatusOrThrow(deltakerId))
	}

	override fun hentDeltakereMedPersonIdent(personIdent: String): List<Deltaker>{
		val deltakere =  deltakerRepository.getDeltakereMedPersonIdent(personIdent)
		return mapDeltakereOgAktiveStatuser(deltakere)
	}

	override fun oppdaterStatuser() {
		val avsluttetEllerIkkeAktuell = deltakerRepository.erPaaAvsluttetGjennomforing()
		val deltakere = mapDeltakereOgAktiveStatuser(avsluttetEllerIkkeAktuell)
		val skalBliIkkeAktuell = deltakere.filter { it.status.type === DeltakerStatus.Type.VENTER_PA_OPPSTART}
		val skalBliAvsluttet = deltakere.filter { it.status.type !== DeltakerStatus.Type.VENTER_PA_OPPSTART }

		oppdaterStatuser(skalBliIkkeAktuell.map { it.id }, DeltakerStatus.Type.IKKE_AKTUELL)
		oppdaterStatuser(skalBliAvsluttet.map { it.id }, DeltakerStatus.Type.HAR_SLUTTET)
		oppdaterStatuser(deltakerRepository.skalAvsluttes().map { it.id }, DeltakerStatus.Type.HAR_SLUTTET)
		oppdaterStatuser(deltakerRepository.skalHaStatusDeltar().map { it.id }, DeltakerStatus.Type.DELTAR)
	}

	override fun slettDeltaker(deltakerId: UUID) {
		transactionTemplate.execute {
			endringsmeldingService.slettEndringsmeldingerForDeltaker(deltakerId)
			deltakerStatusRepository.slettDeltakerStatus(deltakerId)
			deltakerRepository.slettDeltaker(deltakerId)

			kafkaProducerService.publiserSlettDeltaker(deltakerId)
		}

		log.info("Deltaker med id=$deltakerId er slettet")
	}

	override fun oppdaterNavEnhet(personIdent: String, navEnhet: NavEnhet?) {
		brukerService.oppdaterNavEnhet(personIdent, navEnhet)
	}

	override fun erSkjermet(deltakerId: UUID) : Boolean {
		val deltaker = hentDeltaker(deltakerId)?: throw NoSuchElementException("Fant ikke deltaker med id $deltakerId")
		return brukerService.erSkjermet(deltaker.personIdent)
	}

	override fun settSkjermet(personIdent: String, erSkjermet: Boolean) {
		brukerService.settErSkjermet(personIdent, erSkjermet)
	}

	private fun update(deltaker: DeltakerUpsert) {
		val toUpdate = DeltakerUpdateDbo(
			id = deltaker.id,
			startDato = deltaker.startDato,
			sluttDato = deltaker.sluttDato,
			registrertDato = deltaker.registrertDato,
			dagerPerUke = deltaker.dagerPerUke,
			prosentStilling = deltaker.prosentStilling,
			innsokBegrunnelse = deltaker.innsokBegrunnelse
		)

		deltakerRepository.update(toUpdate)
	}

	private fun oppdaterStatus(status: DeltakerStatusInsert) {
		val forrigeStatus = deltakerStatusRepository.getStatusForDeltaker(status.deltakerId)
		if (forrigeStatus?.type == status.type && forrigeStatus.aarsak == status.aarsak) return

		val nyStatus = DeltakerStatusInsertDbo(
			id = status.id,
			deltakerId = status.deltakerId,
			type = status.type,
			aarsak = status.aarsak,
			gyldigFra = status.gyldigFra?: LocalDateTime.now()
		)

		transactionTemplate.executeWithoutResult {
			forrigeStatus?.let { deltakerStatusRepository.deaktiver(it.id) }
			deltakerStatusRepository.insert(nyStatus)
		}
	}

	private fun insertDeltaker(fodselsnummer: String, deltaker: DeltakerUpsert) {
		val brukerId = brukerService.getOrCreate(fodselsnummer)
		val toInsert = DeltakerInsertDbo(
			id = deltaker.id,
			brukerId = brukerId,
			gjennomforingId = deltaker.gjennomforingId,
			startDato = deltaker.startDato,
			sluttDato = deltaker.sluttDato,
			dagerPerUke = deltaker.dagerPerUke,
			prosentStilling = deltaker.prosentStilling,
			registrertDato = deltaker.registrertDato,
			innsokBegrunnelse = deltaker.innsokBegrunnelse
		)

		deltakerRepository.insert(toInsert)
	}

	private fun hentStatusOrThrow(deltakerId: UUID) : DeltakerStatus {
		return hentStatus(deltakerId) ?: throw NoSuchElementException("Fant ikke status på deltaker med id $deltakerId")
	}

	private fun hentStatus(deltakerId: UUID) : DeltakerStatus? {
		return deltakerStatusRepository.getStatusForDeltaker(deltakerId)?.toModel() ?: return null
	}

	private fun hentAktiveStatuserForDeltakere(deltakerIder: List<UUID>): Map<UUID, DeltakerStatusDbo> {
		return deltakerStatusRepository.getAktiveStatuserForDeltakere(deltakerIder).associateBy { it.deltakerId }
	}

	private fun mapDeltakereOgAktiveStatuser(deltakere: List<DeltakerDbo>): List<Deltaker> {
		val statuser = hentAktiveStatuserForDeltakere(deltakere.map { it.id })
		return deltakere.map { d ->
			val status = statuser[d.id] ?: throw NoSuchElementException("Fant ikke status på deltaker med id ${d.id}")
			return@map d.toDeltaker(status.toModel())
		}
	}

	private fun oppdaterStatuser(deltakere: List<UUID>, type: DeltakerStatus.Type) = deltakere
		.also { log.info("Oppdaterer status på ${it.size} deltakere") }
		.forEach {
			insertStatus(DeltakerStatusInsert(
				id = UUID.randomUUID(),
				deltakerId = it,
				type = type,
				aarsak = null,
				gyldigFra = LocalDateTime.now()
			))
		}

	override fun finnesBruker(personIdent: String): Boolean {
		return brukerService.finnesBruker(personIdent)
	}

	override fun oppdaterAnsvarligVeileder(personIdent: String, navAnsattId: UUID) {
		brukerService.oppdaterAnsvarligVeileder(personIdent, navAnsattId)
	}

	override fun leggTilOppstartsdato(deltakerId: UUID, arrangorAnsattId: UUID, oppstartsdato: LocalDate) {
		endringsmeldingService.opprettLeggTilOppstartsdatoEndringsmelding(deltakerId, arrangorAnsattId, oppstartsdato)
	}

	override fun endreOppstartsdato(deltakerId: UUID, arrangorAnsattId: UUID, oppstartsdato: LocalDate) {
		endringsmeldingService.opprettEndreOppstartsdatoEndringsmelding(deltakerId, arrangorAnsattId, oppstartsdato)
	}

	override fun forlengDeltakelse(deltakerId: UUID, arrangorAnsattId: UUID, sluttdato: LocalDate) {
		endringsmeldingService.opprettForlengDeltakelseEndringsmelding(deltakerId, arrangorAnsattId, sluttdato)
	}

	override fun endreDeltakelsesprosent(deltakerId: UUID, arrangorAnsattId: UUID, deltakerProsent: Int) {
		endringsmeldingService.opprettEndreDeltakelseProsentEndringsmelding(
			deltakerId = deltakerId,
			arrangorAnsattId = arrangorAnsattId,
			deltakerProsent = deltakerProsent
		)
	}

	override fun avsluttDeltakelse(
		deltakerId: UUID,
		arrangorAnsattId: UUID,
		sluttdato: LocalDate,
		statusAarsak: DeltakerStatus.Aarsak
	) {
		endringsmeldingService.opprettAvsluttDeltakelseEndringsmelding(deltakerId, arrangorAnsattId, sluttdato, statusAarsak)
	}

	override fun deltakerIkkeAktuell(deltakerId: UUID, arrangorAnsattId: UUID, statusAarsak: DeltakerStatus.Aarsak) {
		endringsmeldingService.opprettDeltakerIkkeAktuellEndringsmelding(deltakerId, arrangorAnsattId, statusAarsak)
	}

	override fun hentDeltakerMap(deltakerIder: List<UUID>): Map<UUID, Deltaker> {
		val deltakere = deltakerRepository.getDeltakere(deltakerIder)
		return mapDeltakereOgAktiveStatuser(deltakere).associateBy { it.id }
	}

	override fun kanDeltakerSkjulesForTiltaksarrangor(deltakerId: UUID): Boolean {
		val statuserSomKanSkjules = listOf(DeltakerStatus.Type.IKKE_AKTUELL, DeltakerStatus.Type.HAR_SLUTTET)

		val deltakerStatus = hentStatusOrThrow(deltakerId)

		return statuserSomKanSkjules.contains(deltakerStatus.type)
	}

	override fun skjulDeltakerForTiltaksarrangor(deltakerId: UUID, arrangorAnsattId: UUID) {
		if (!kanDeltakerSkjulesForTiltaksarrangor(deltakerId))
			throw IllegalStateException("Kan ikke skjule deltaker $deltakerId. Ugyldig status")

		skjultDeltakerRepository.skjulDeltaker(UUID.randomUUID(), deltakerId, arrangorAnsattId)
	}

	override fun opphevSkjulDeltakerForTiltaksarrangor(deltakerId: UUID) {
		skjultDeltakerRepository.opphevSkjulDeltaker(deltakerId)
	}

	override fun erSkjultForTiltaksarrangor(deltakerId: UUID): Boolean {
		return skjultDeltakerRepository.erSkjultForTiltaksarrangor(listOf(deltakerId)).getOrDefault(deltakerId, false)
	}

	override fun erSkjultForTiltaksarrangor(deltakerIder: List<UUID>): Map<UUID, Boolean> {
		return skjultDeltakerRepository.erSkjultForTiltaksarrangor(deltakerIder)
	}

	override fun republiserAlleDeltakerePaKafka(batchSize: Int) {
		var offset = 0

		var deltakere: List<DeltakerDbo>

		do {
			deltakere = deltakerRepository.hentDeltakere(offset, batchSize)

			val statuser = hentAktiveStatuserForDeltakere(deltakere.map { it.id })

			deltakere.forEach {
				val status = statuser[it.id]?.toModel()

				if (status == null) {
					log.error("Klarte ikke å republisere deltaker med id ${it.id} fordi status mangler")
					return@forEach
				}

				val deltaker = it.toDeltaker(status)

				kafkaProducerService.publiserDeltaker(deltaker)
			}

			offset += deltakere.size

			log.info("Publisert batch med deltakere på kafka, offset=$offset, batchSize=${deltakere.size}")
		} while (deltakere.isNotEmpty())

		log.info("Ferdig med republisering av deltakere på kafka")
	}

}


