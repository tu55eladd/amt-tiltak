package no.nav.amt.tiltak.ingestors.arena_acl_ingestor.processor

import no.nav.amt.tiltak.core.domain.tiltak.Deltaker
import no.nav.amt.tiltak.core.port.DeltakerService
import no.nav.amt.tiltak.core.port.GjennomforingService
import no.nav.amt.tiltak.ingestors.arena_acl_ingestor.dto.DeltakerPayload
import no.nav.amt.tiltak.ingestors.arena_acl_ingestor.dto.MessageWrapper
import org.springframework.stereotype.Service

@Service
class DeltakerProcessor(
	private val gjennomforingService: GjennomforingService,
	private val deltakerService: DeltakerService,
) : GenericProcessor<DeltakerPayload>() {

	override fun processInsertMessage(message: MessageWrapper<DeltakerPayload>) {
		upsert(message)
	}

	override fun processModifyMessage(message: MessageWrapper<DeltakerPayload>) {
		upsert(message)
	}

	override fun processDeleteMessage(message: MessageWrapper<DeltakerPayload>) {
		TODO("Not yet implemented")
	}

	private fun upsert(message: MessageWrapper<DeltakerPayload>) {
		val deltaker = message.payload

		val tiltaksgjennomforing = gjennomforingService.getGjennomforing(deltaker.gjennomforingId)

		deltakerService.upsertDeltaker(
			id = deltaker.id,
			gjennomforingId = tiltaksgjennomforing.id,
			fodselsnummer = deltaker.personIdent,
			startDato = deltaker.startDato,
			sluttDato = deltaker.sluttDato,
			status = tilDeltakerStatus(deltaker.status),
			dagerPerUke = deltaker.dagerPerUke,
			prosentStilling = deltaker.prosentDeltid,
			registrertDato = deltaker.registrertDato
		)
	}

	private fun tilDeltakerStatus(status: DeltakerPayload.Status): Deltaker.Status {
		return when(status){
			DeltakerPayload.Status.VENTER_PA_OPPSTART -> Deltaker.Status.VENTER_PA_OPPSTART
			DeltakerPayload.Status.DELTAR -> Deltaker.Status.DELTAR
			DeltakerPayload.Status.HAR_SLUTTET -> Deltaker.Status.HAR_SLUTTET
			DeltakerPayload.Status.IKKE_AKTUELL -> Deltaker.Status.IKKE_AKTUELL
		}
	}

}
