package no.nav.amt.tiltak.deltaker.repositories

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.tiltak.test.database.DatabaseTestUtils
import no.nav.amt.tiltak.test.database.SingletonPostgresContainer
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.*


class BrukerRepositoryTest : FunSpec({

	val dataSource = SingletonPostgresContainer.getDataSource()

	lateinit var repository: BrukerRepository

	beforeEach {
		val rootLogger: Logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
		rootLogger.level = Level.WARN

		repository = BrukerRepository(NamedParameterJdbcTemplate(dataSource))

		DatabaseTestUtils.cleanAndInitDatabase(dataSource, "/bruker-repository_test-data.sql")
	}

	test("Insert should insert bruker and return BrukerDbo") {
		val fodselsnummer = "12345678910"
		val fornavn = "Per"
		val mellomnavn = null
		val etternavn = "Testersen"
		val telefonnummer = "74635462"
		val epost = "per.testersen@test.no"
		val ansvarligVeilederId = UUID.fromString("4118216f-b46d-44a1-90c5-d0732e861d6e")

		val dbo = repository.insert(fodselsnummer, fornavn, mellomnavn, etternavn, telefonnummer, epost, ansvarligVeilederId)

		dbo shouldNotBe null
		dbo.id shouldNotBe null
		dbo.fodselsnummer.toString() shouldBe fodselsnummer
		dbo.fornavn shouldBe fornavn
		dbo.etternavn shouldBe etternavn
		dbo.telefonnummer shouldBe telefonnummer
		dbo.epost shouldBe epost
		dbo.ansvarligVeilederId shouldBe ansvarligVeilederId
		dbo.createdAt shouldNotBe null
		dbo.modifiedAt shouldNotBe null
	}

	test("Get user that does not exist should be null") {
		repository.get("234789") shouldBe null
	}
})