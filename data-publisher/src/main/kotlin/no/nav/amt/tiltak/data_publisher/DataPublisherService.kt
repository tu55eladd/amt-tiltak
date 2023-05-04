package no.nav.amt.tiltak.data_publisher

import arrow.core.getOrElse
import no.nav.amt.tiltak.clients.amt_enhetsregister.EnhetsregisterClient
import no.nav.amt.tiltak.common.json.JsonUtils
import no.nav.amt.tiltak.data_publisher.model.DataPublishType
import no.nav.amt.tiltak.data_publisher.model.PublishState
import no.nav.amt.tiltak.data_publisher.publish.*
import no.nav.amt.tiltak.kafka.config.KafkaTopicProperties
import no.nav.common.kafka.producer.KafkaProducerClient
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class DataPublisherService(
	private val kafkaTopicProperties: KafkaTopicProperties,
	private val stringKafkaProducer: KafkaProducerClient<String, String>,
	private val template: NamedParameterJdbcTemplate,
	private val enhetsregisterClient: EnhetsregisterClient,
	private val publishRepository: PublishRepository,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun publish(id: UUID, type: DataPublishType) {
		when (type) {
			DataPublishType.ARRANGOR -> publishArrangor(id)
			DataPublishType.ARRANGOR_ANSATT -> publishArrangorAnsatt(id)
			DataPublishType.DELTAKER -> publishDeltaker(id)
			DataPublishType.DELTAKERLISTE -> publishDeltakerliste(id)
			DataPublishType.ENDRINGSMELDING -> publishEndringsmelding(id)
		}
	}

	fun publishAll(batchSize: Int = 100, forcePublish: Boolean = true) {
		val idQueries = IdQueries(template)

			publishBatch(
				idProvider = { offset -> idQueries.hentDeltakerlisteIds(offset, batchSize) },
				publisher = { id -> publishDeltakerliste(id, forcePublish) }
			)

			publishBatch(
				idProvider = { offset -> idQueries.hentDeltakerIds(offset, batchSize) },
				publisher = { id -> publishDeltaker(id, forcePublish) }
			)

			publishBatch(
				idProvider = { offset -> idQueries.hentArrangorIds(offset, batchSize) },
				publisher = { id -> publishArrangor(id, forcePublish) }
			)

			publishBatch(
				idProvider = { offset -> idQueries.hentArrangorAnsattIds(offset, batchSize) },
				publisher = { id -> publishArrangorAnsatt(id, forcePublish) }
			)

			publishBatch(
				idProvider = { offset -> idQueries.hentEndringsmeldingIds(offset, batchSize) },
				publisher = { id -> publishEndringsmelding(id, forcePublish) }
			)
	}

	private fun publishDeltakerliste(id: UUID, forcePublish: Boolean = false) {

		val currentData = DeltakerlistePublishQuery(template).get(id)

		if (forcePublish || !publishRepository.hasHash(id, DataPublishType.DELTAKERLISTE, currentData.digest())) {
			val key = id.toString()
			val value = JsonUtils.toJsonString(currentData)
			val record = ProducerRecord(kafkaTopicProperties.amtDeltakerlisteTopic, key, value)
			logger.info("Republiserer DELTAKERLISTE med id $id")
			stringKafkaProducer.sendSync(record)
			publishRepository.set(id, DataPublishType.DELTAKERLISTE, currentData.digest())
		}
	}

	private fun publishDeltaker(id: UUID, forcePublish: Boolean = false) {

		DeltakerPublishQuery(template).get(id).getOrElse {
			when (it) {
				PublishState.DONT_PUBLISH -> return
				PublishState.PUBLISH_TOMBSTONE -> {
					val key = id.toString()
					val record = ProducerRecord<String, String?>(kafkaTopicProperties.amtDeltakerTopic, key, null)
					logger.info("Legger inn Tombstone på DELTAKER med id $id")
					stringKafkaProducer.sendSync(record)
					return
				}
			}
		}.let { currentData ->
			if (forcePublish || !publishRepository.hasHash(id, DataPublishType.DELTAKER, currentData.digest())) {
				val key = id.toString()
				val value = JsonUtils.toJsonString(currentData)
				val record = ProducerRecord(kafkaTopicProperties.amtDeltakerTopic, key, value)
				logger.info("Republiserer DELTAKER med id $id")
				stringKafkaProducer.sendSync(record)
				publishRepository.set(id, DataPublishType.DELTAKER, currentData.digest())

			}
		}
	}

	private fun publishArrangor(id: UUID, forcePublish: Boolean = false) {

		val currentData = ArrangorPublishQuery(template, enhetsregisterClient).get(id)

		if (forcePublish || !publishRepository.hasHash(id, DataPublishType.ARRANGOR, currentData.digest())) {
			val key = id.toString()
			val value = JsonUtils.toJsonString(currentData)
			val record = ProducerRecord(kafkaTopicProperties.amtArrangorTopic, key, value)
			logger.info("Republiserer ARRANGOR med id $id")
			stringKafkaProducer.sendSync(record)
			publishRepository.set(id, DataPublishType.ARRANGOR, currentData.digest())
		}
	}

	private fun publishArrangorAnsatt(id: UUID, forcePublish: Boolean = false) {

		val currentData = ArrangorAnsattPublishQuery(template).get(id)

		if (forcePublish || !publishRepository.hasHash(id, DataPublishType.ARRANGOR_ANSATT, currentData.digest())) {
			val key = id.toString()
			val value = JsonUtils.toJsonString(currentData)
			val record = ProducerRecord(kafkaTopicProperties.amtArrangorAnsattTopic, key, value)
			logger.info("Republiserer ARRANGOR_ANSATT med id $id")
			stringKafkaProducer.sendSync(record)
			publishRepository.set(id, DataPublishType.ARRANGOR_ANSATT, currentData.digest())
		}
	}

	private fun publishEndringsmelding(id: UUID, forcePublish: Boolean = false) {

		val currentData = EndringsmeldingPublishQuery(template).get(id)

		if (forcePublish || !publishRepository.hasHash(id, DataPublishType.ENDRINGSMELDING, currentData.digest())) {
			val key = id.toString()
			val value = JsonUtils.toJsonString(currentData)
			val record = ProducerRecord(kafkaTopicProperties.amtEndringsmeldingTopic, key, value)
			logger.info("Republiserer ENDRINGSMELDING med id $id")
			stringKafkaProducer.sendSync(record)
			publishRepository.set(id, DataPublishType.ENDRINGSMELDING, currentData.digest())
		}
	}

	private fun publishBatch(
		idProvider: (offset: Int) -> List<UUID>,
		publisher: (id: UUID) -> Unit
	) {
		var offset = 0
		var ids: List<UUID>
		do {
			ids = idProvider.invoke(offset)
			ids.forEach { publisher.invoke(it) }
			offset += ids.size
		} while (ids.isNotEmpty())
	}
}