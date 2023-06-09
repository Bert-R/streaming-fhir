# Introduction

This repo implements a basic data platform for streaming FHIR resources. Every insert/update/delete on the HAPI FHIR store is posted as a message on Kafka.

# Design

The chain starts from an interceptor in HAPI. That writes a message to the PostgreSQL write-ahead log (WAL) for every insert/update/delete, containing the involved FHIR resource. This message is read by Debezium. A custom transformer in Debezium converts the message to a simple FHIR resource, with a ``Struct`` key and posts it on Kafka. Resources in the ``Patient`` compartment are posted on the ``patient`` topic, others on the ``nonpatient`` topic.

# Running it

Run the script ``bin/setup`` to start everything. This enables the following interesting endpoints:
* [Adminer](https://www.adminer.org/), a simple database management tool: http://localhost:8081/ (login with System: PostgreSQL, server: postgres, username: admin, password: admin and database: hapi)
* [HAPI](https://hapifhir.io/), a FHIR store: http://localhost:8080/fhir/metadata
* [Kafdrop](https://github.com/obsidiandynamics/kafdrop), a web UI for viewing Kafka topics and browsing consumer groups: http://localhost:9000/

To post a new ``Patient`` resource, run ``bin/post-pat``, optionaly specifying the family name as parameter (defaults to Duck). To update it run ``bin/put-pat``, optionaly specifying the resource ID and family name as parameter (family name defaults to Trump). This will result in messages in [the ``patient`` topic](http://localhost:9001/topic/patient/messages?partition=0&offset=0&count=100&keyFormat=DEFAULT&format=DEFAULT). Note that HAPI skips identical updates, so subsequent calls to ``bin/put-pat`` with the same family name would not result in Kafka messages.
