package no.nav.amt.tiltak.tiltak.deltaker.dbo

import java.util.*

data class NavAnsattDbo(
	val id: UUID,
	val personligIdent: String,
	val fornavn: String?,
	val etternavn: String?,
	val telefonnummer: String?,
	val epost: String?
)