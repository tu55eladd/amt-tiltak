package no.nav.amt.tiltak.navansatt

import no.nav.amt.tiltak.common.db_utils.DbUtils.sqlParameters
import no.nav.amt.tiltak.core.domain.nav_ansatt.NavAnsatt
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
internal class NavAnsattRepository(
	private val template: NamedParameterJdbcTemplate
) {

	private val rowMapper = RowMapper { rs, _ ->
		NavAnsattDbo(
			id = UUID.fromString(rs.getString("id")),
			navIdent = rs.getString("nav_ident"),
			navn = rs.getString("navn"),
			telefonnummer = rs.getString("telefonnummer"),
			epost = rs.getString("epost")
		)
	}

	internal fun get(id: UUID): NavAnsattDbo {
		val sql = """
			SELECT * FROM nav_ansatt WHERE id = :id
		""".trimIndent()

		val parameters = sqlParameters("id" to id)

		return template.query(sql, parameters, rowMapper).firstOrNull()
			?: throw NoSuchElementException("Fant ikke NAV Ansatt med id=$id")
	}

	internal fun get(navIdent: String): NavAnsattDbo? {
		return template.query(
			"SELECT * FROM nav_ansatt WHERE nav_ident = :navIdent",
			sqlParameters("navIdent" to navIdent),
			rowMapper
		).firstOrNull()
	}

	fun finnesAnsatt(id: UUID): Boolean {
		val sql = """
			SELECT * FROM nav_ansatt WHERE id = :id
		""".trimIndent()

		val parameters = sqlParameters("id" to id)

		return template.query(sql, parameters, rowMapper).isNotEmpty()
	}

	fun upsert(ansatt: NavAnsatt) {
		val sql = """
			INSERT INTO nav_ansatt(id, nav_ident, navn, telefonnummer, epost)
			VALUES (
				:id,
				:navIdent,
				:navn,
				:telefonnummer,
				:epost
			)
			ON CONFLICT (nav_ident) DO UPDATE SET
				navn = :navn,
				telefonnummer = :telefonnummer,
				epost = :epost
		""".trimIndent()

		val parameterSource = sqlParameters(
			"id" to ansatt.id,
			"navIdent" to ansatt.navIdent,
			"navn" to ansatt.navn,
			"telefonnummer" to ansatt.telefonnummer,
			"epost" to ansatt.epost,
		)

		template.update(sql, parameterSource)
	}

}
