package io.github.bertr.streamingfhir.inttest

import java.time.Duration

import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer

import spock.lang.Specification

class TestPatient extends Specification
{
	def "Get all resources"()
	{
		given:
		String topic = "patient"
		String group = UUID.randomUUID()

		def consumer = new KafkaConsumer([
				"bootstrap.servers"      : 'localhost:9092',
				// Consumer group
				"group.id"               : group,
				"auto.offset.reset"      : "latest",

				// auto offset management
				"enable.auto.commit"     : "true",
				"auto.commit.interval.ms": "1000",

				// serializers
				"value.deserializer"     : "org.apache.kafka.common.serialization.StringDeserializer",
				"key.deserializer"       : "org.apache.kafka.common.serialization.StringDeserializer"
		])
		// subscribe to the topic
		consumer.subscribe([topic])
		consumer.poll(Duration.ofSeconds(10)) // Seek to end

		when:
		def FAMILY_NAME = UUID.randomUUID().toString()
		postString("http://localhost:8080/fhir/Patient", 201, """
{
  "resourceType": "Patient",
  "text": {
    "status": "generated",
    "div": "<div xmlns=\\"http://www.w3.org/1999/xhtml\\">\\n      \\n      <p>Patient Donald ${FAMILY_NAME} @ Acme Healthcare, Inc. MR = 654321</p>\\n    \\n    </div>"
  },
  "identifier": [
    {
      "use": "usual",
      "system": "urn:oid:2.16.840.1.113883.2.4.6.3",
      "value": "738472983"
    },
    {
      "use": "usual",
      "system": "https://iam.philips-healthsuite.com/user",
      "value": "6ba7e0df-7747-40f5-8b8b-100a53882bf7"
    }
  ],
  "active": true,
  "name": [
    {
      "use": "official",
      "family": "${FAMILY_NAME}",
      "given": [
        "Donald"
      ]
    }
  ],
  "gender": "male"
}
""")

		then:
		// loop using the poll mechanism
		ConsumerRecords records = consumer.poll(Duration.ofSeconds(10))
		records.count() == 1
		records[0].toString() ==~ /.*\\"family\\":\\"${FAMILY_NAME}\\",.*/

		cleanup:
		consumer.close()
	}

	private postString(String urlString, int expectedResponseCode, String bodyText)
	{
		def url = new URL(urlString)
		def urlConnection = url.openConnection(Proxy.NO_PROXY)
		urlConnection.setInstanceFollowRedirects(false)
		urlConnection.setRequestMethod("POST")
		urlConnection.setDoOutput(true)
		urlConnection.setRequestProperty("Content-Type", "application/json+fhir")
		urlConnection.setRequestProperty("Accept", "application/json")
		urlConnection.getOutputStream().write(bodyText.getBytes("UTF-8"))
		assert urlConnection.getResponseCode() == expectedResponseCode
	}
}
