package no.nav.amt.tiltak.tilgangskontroll.tiltaksansvarlig_tilgang

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.tiltak.core.domain.nav_ansatt.NavAnsatt
import no.nav.amt.tiltak.core.port.VeilederService
import org.springframework.web.server.ResponseStatusException
import java.time.ZonedDateTime
import java.util.*

class TiltaksansvarligTilgangServiceImplTest : FunSpec({

	val veilederService: VeilederService = mockk()

	val tiltaksansvarligGjennomforingTilgangRepository: TiltaksansvarligGjennomforingTilgangRepository = mockk()

	val service = TiltaksansvarligTilgangServiceImpl(
		veilederService,
		tiltaksansvarligGjennomforingTilgangRepository
	)

	test("harTilgangTilGjennomforing - skal returnere true hvis tilgang til gjennomføring") {
		val navIdent = "Z123"
		val navAnsattId = UUID.randomUUID()
		val gjennomforingId = UUID.randomUUID()

		every {
			veilederService.getOrCreateVeileder(navIdent)
		} returns NavAnsatt(
			navAnsattId,
			navIdent,
			""
		)

		every {
			tiltaksansvarligGjennomforingTilgangRepository.hentAktiveTilganger(navAnsattId)
		} returns listOf(
			TiltaksansvarligGjennomforingTilgangDbo(
				id = UUID.randomUUID(),
				navAnsattId = navAnsattId,
				gjennomforingId = gjennomforingId,
				gyldigTil = ZonedDateTime.now().plusDays(1),
				createdAt = ZonedDateTime.now(),
			)
		)

		service.harTilgangTilGjennomforing(navIdent, gjennomforingId) shouldBe true
	}

	test("harTilgangTilGjennomforing - skal returnere false hvis ikke tilgang til gjennomføring") {
		val navIdent = "Z123"
		val navAnsattId = UUID.randomUUID()
		val gjennomforingId = UUID.randomUUID()

		every {
			veilederService.getOrCreateVeileder(navIdent)
		} returns NavAnsatt(
			navAnsattId,
			navIdent,
			""
		)

		every {
			tiltaksansvarligGjennomforingTilgangRepository.hentAktiveTilganger(navAnsattId)
		} returns listOf(
			TiltaksansvarligGjennomforingTilgangDbo(
				id = UUID.randomUUID(),
				navAnsattId = navAnsattId,
				gjennomforingId = UUID.randomUUID(),
				gyldigTil = ZonedDateTime.now().plusDays(1),
				createdAt = ZonedDateTime.now(),
			)
		)

		service.harTilgangTilGjennomforing(navIdent, gjennomforingId) shouldBe false
	}

	test("giTilgangTilGjennomforing - skal kaste exception hvis tilgang allerede finnes") {
		val navIdent = "Z123"
		val navAnsattId = UUID.randomUUID()
		val gjennomforingId = UUID.randomUUID()

		every {
			veilederService.getOrCreateVeileder(navIdent)
		} returns NavAnsatt(
			navAnsattId,
			navIdent,
			""
		)

		every {
			tiltaksansvarligGjennomforingTilgangRepository.hentAktiveTilganger(navAnsattId)
		} returns listOf(
			TiltaksansvarligGjennomforingTilgangDbo(
				id = UUID.randomUUID(),
				navAnsattId = navAnsattId,
				gjennomforingId = gjennomforingId,
				gyldigTil = ZonedDateTime.now().plusDays(1),
				createdAt = ZonedDateTime.now(),
			)
		)

		shouldThrowExactly<ResponseStatusException> {
			service.giTilgangTilGjennomforing(navAnsattId, gjennomforingId)
		}
	}

	test("stopTilgangTilGjennomforing - skal kaste exception hvis ikke er aktiv tilgang") {
		val navIdent = "Z123"
		val navAnsattId = UUID.randomUUID()
		val gjennomforingId = UUID.randomUUID()

		every {
			veilederService.getOrCreateVeileder(navIdent)
		} returns NavAnsatt(
			navAnsattId,
			navIdent,
			""
		)

		every {
			tiltaksansvarligGjennomforingTilgangRepository.hentAktiveTilganger(navAnsattId)
		} returns emptyList()

		shouldThrowExactly<ResponseStatusException> {
			service.stopTilgangTilGjennomforing(navAnsattId, gjennomforingId)
		}
	}


})
