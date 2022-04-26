package no.nav.amt.tiltak.tilgangskontroll.tilgang

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.amt.tiltak.core.domain.arrangor.Ansatt
import no.nav.amt.tiltak.core.port.ArrangorAnsattService
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.*

class ArrangorAnsattTilgangServiceImplTest : FunSpec({

	lateinit var arrangorAnsattService: ArrangorAnsattService

	lateinit var ansattRolleRepository: AnsattRolleRepository

	lateinit var arrangorAnsattTilgangServiceImpl: ArrangorAnsattTilgangServiceImpl

	lateinit var gjennomforingTilgangRepository: GjennomforingTilgangRepository

	val personligIdent = "fnr"

	val ansattId = UUID.randomUUID()

	val gjennomforingId = UUID.randomUUID()

	val arrangorId = UUID.randomUUID()

	beforeEach {
		arrangorAnsattService = mockk()

		ansattRolleRepository = mockk()

		gjennomforingTilgangRepository = mockk()

		arrangorAnsattTilgangServiceImpl = ArrangorAnsattTilgangServiceImpl(
			arrangorAnsattService, ansattRolleRepository, gjennomforingTilgangRepository
		)

		every {
			arrangorAnsattService.getAnsattByPersonligIdent(personligIdent)
		} returns Ansatt(
			ansattId,
			personligIdent,
			"",
			"",
			emptyList()
		)
	}

	test("verifiserTilgangTilGjennomforing skal kaste exception hvis ikke tilgang") {
		every {
			gjennomforingTilgangRepository.hentGjennomforingerForAnsatt(ansattId)
		} returns listOf(UUID.randomUUID())

		val exception = shouldThrowExactly<ResponseStatusException> {
			arrangorAnsattTilgangServiceImpl.verifiserTilgangTilGjennomforing(personligIdent, gjennomforingId)
		}

		exception.status shouldBe HttpStatus.FORBIDDEN
	}

	test("verifiserTilgangTilGjennomforing skal ikke kaste exception hvis tilgang") {
		every {
			gjennomforingTilgangRepository.hentGjennomforingerForAnsatt(ansattId)
		} returns listOf(gjennomforingId)

		shouldNotThrow<Throwable> {
			arrangorAnsattTilgangServiceImpl.verifiserTilgangTilGjennomforing(personligIdent, gjennomforingId)
		}
	}

	test("verifiserTilgangTilArrangor skal kaste exception hvis ikke tilgang") {
		every {
			ansattRolleRepository.hentArrangorIderForAnsatt(ansattId)
		} returns listOf(UUID.randomUUID())

		val exception = shouldThrowExactly<ResponseStatusException> {
			arrangorAnsattTilgangServiceImpl.verifiserTilgangTilArrangor(personligIdent, arrangorId)
		}

		exception.status shouldBe HttpStatus.FORBIDDEN
	}

	test("verifiserTilgangTilArrangor skal ikke kaste exception hvis tilgang") {
		every {
			ansattRolleRepository.hentArrangorIderForAnsatt(ansattId)
		} returns listOf(arrangorId)

		shouldNotThrow<Throwable> {
			arrangorAnsattTilgangServiceImpl.verifiserTilgangTilArrangor(personligIdent, arrangorId)
		}
	}

})