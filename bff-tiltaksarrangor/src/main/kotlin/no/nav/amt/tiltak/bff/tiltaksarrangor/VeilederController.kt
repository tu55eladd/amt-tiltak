package no.nav.amt.tiltak.bff.tiltaksarrangor

import no.nav.amt.tiltak.bff.tiltaksarrangor.dto.TilgjengeligVeilederDto
import no.nav.amt.tiltak.bff.tiltaksarrangor.dto.VeilederDto
import no.nav.amt.tiltak.bff.tiltaksarrangor.request.LeggTilVeiledereBulkRequest
import no.nav.amt.tiltak.bff.tiltaksarrangor.request.LeggTilVeiledereRequest
import no.nav.amt.tiltak.common.auth.AuthService
import no.nav.amt.tiltak.common.auth.Issuer
import no.nav.amt.tiltak.core.domain.arrangor.Ansatt
import no.nav.amt.tiltak.core.domain.arrangor.ArrangorVeilederInput
import no.nav.amt.tiltak.core.domain.tilgangskontroll.ArrangorAnsattRolle
import no.nav.amt.tiltak.core.exceptions.UnauthorizedException
import no.nav.amt.tiltak.core.port.ArrangorAnsattService
import no.nav.amt.tiltak.core.port.ArrangorAnsattTilgangService
import no.nav.amt.tiltak.core.port.ArrangorVeilederService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController("VeilederControllerTiltaksarrangor")
@RequestMapping("/api/tiltaksarrangor/veiledere")
class VeilederController (
	private val arrangorAnsattTilgangService: ArrangorAnsattTilgangService,
	private val authService: AuthService,
	private val arrangorAnsattService: ArrangorAnsattService,
	private val arrangorVeilederService: ArrangorVeilederService,
	private val controllerService: ControllerService,
) {

	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	@PatchMapping
	fun leggTilVeiledere(@RequestBody request: LeggTilVeiledereBulkRequest) {
		val ansatt = hentInnloggetAnsatt()
		arrangorAnsattTilgangService.verifiserTilgangTilGjennomforing(
			ansatt.id,
			request.gjennomforingId,
			ArrangorAnsattRolle.KOORDINATOR,
		)

		val veiledere = request.veiledere.map { ArrangorVeilederInput(it.ansattId, it.erMedveileder) }

		arrangorVeilederService.opprettVeiledere(veiledere, request.deltakerIder)
	}

	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	@GetMapping(params = ["deltakerId"])
	fun hentVeiledereForDeltaker(@RequestParam("deltakerId") deltakerId: UUID) : List<VeilederDto> {
		val ansatt = hentInnloggetAnsatt()

		verifiserTilgangTilDeltaker(ansatt, deltakerId)

		val veiledere = arrangorVeilederService.hentVeiledereForDeltaker(deltakerId)

		return controllerService.mapAnsatteTilVeilederDtoer(veiledere)
	}

	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	@PatchMapping(params = ["deltakerId"])
	fun tildelVeiledereForDeltaker(
		@RequestParam("deltakerId") deltakerId: UUID,
		@RequestBody request: LeggTilVeiledereRequest,
	) {
		val ansatt = hentInnloggetAnsatt()

		arrangorAnsattTilgangService.verifiserTilgangTilDeltaker(ansatt.id, deltakerId, ArrangorAnsattRolle.KOORDINATOR)

		val veiledere = request.veiledere.map { ArrangorVeilederInput(it.ansattId, it.erMedveileder) }

		arrangorVeilederService.opprettVeiledereForDeltaker(veiledere, deltakerId)
	}

	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	@GetMapping(params = ["gjennomforingId"])
	fun hentAktiveVeiledereForGjennomforing(@RequestParam("gjennomforingId") gjennomforingId: UUID) : List<VeilederDto> {
		val ansatt = hentInnloggetAnsatt()

		arrangorAnsattTilgangService.verifiserTilgangTilGjennomforing(
			ansatt.id,
			gjennomforingId,
			ArrangorAnsattRolle.KOORDINATOR,
		)

		val veiledere = arrangorVeilederService.hentAktiveVeiledereForGjennomforing(gjennomforingId)

		return controllerService.mapAnsatteTilVeilederDtoer(veiledere)
	}

	@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
	@GetMapping("/tilgjengelig")
	fun hentTilgjengeligeVeiledere(@RequestParam("gjennomforingId") gjennomforingId: UUID) : List<TilgjengeligVeilederDto> {
		val ansatt = hentInnloggetAnsatt()
		arrangorAnsattTilgangService.verifiserTilgangTilGjennomforing(
			ansatt.id,
			gjennomforingId,
			ArrangorAnsattRolle.KOORDINATOR,
		)

		val tilgjengelige = arrangorVeilederService.hentTilgjengeligeVeiledereForGjennomforing(gjennomforingId)

		return tilgjengelige.map { TilgjengeligVeilederDto(it.id, it.fornavn, it.mellomnavn, it.etternavn) }
	}

	private fun hentInnloggetAnsatt(): Ansatt {
		val ansattPersonligIdent = authService.hentPersonligIdentTilInnloggetBruker()
		return arrangorAnsattService.getAnsattByPersonligIdent(ansattPersonligIdent)
			?: throw UnauthorizedException("Arrangor ansatt finnes ikke")
	}

	private fun verifiserTilgangTilDeltaker(ansatt: Ansatt, deltakerId: UUID) {
		val roller = arrangorAnsattTilgangService.hentRollerForAnsattTilknyttetDeltaker(ansatt.id, deltakerId)

		if (roller.contains(ArrangorAnsattRolle.KOORDINATOR)) {
			arrangorAnsattTilgangService.verifiserTilgangTilDeltaker(
				ansatt.id,
				deltakerId,
				ArrangorAnsattRolle.KOORDINATOR,
			)
		} else if (!arrangorVeilederService.erVeilederFor(ansatt.id, deltakerId)) {
			throw UnauthorizedException("Ansatt ${ansatt.id} er ikke veileder for deltaker $deltakerId")
		}
	}
}