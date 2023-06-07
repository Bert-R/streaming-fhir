package io.github.bertr.streamingfhir.hapi.interceptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;

@Component
@Interceptor
public class ResourceChangePublishingInterceptor
{
	private static final Logger logger = LoggerFactory.getLogger(ResourceChangePublishingInterceptor.class);
	public static final String MESSAGE_TEMPLATE = """
			{
			    "id" : "%s",
			    "aggregate_type" : "%s",
			    "aggregate_id" : "%s",
			    "compartments" :  [ "%s" ],
			    "payload" : %s
			}""";
	public static final String SQL_EMIT_TEMPLATE = "SELECT pg_logical_emit_message(true, '%s', '%s')";
	private FhirContext fhirContext;

	@PersistenceContext
	private EntityManager entityManager;

	@EventListener(ApplicationStartedEvent.class)
	public void doAfterStartup()
	{
		fhirContext = FhirContext.forR4();
		logger.info("Interceptor started");
	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
	public void resourceCreated(IBaseResource newResource)
	{
		publishChange(newResource.getIdElement(), fhirContext.newJsonParser().encodeResourceToString(newResource));
	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED)
	public void resourceUpdated(IBaseResource originalResource, IBaseResource newResource)
	{
		publishChange(newResource.getIdElement(), fhirContext.newJsonParser().encodeResourceToString(newResource));
	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_DELETED)
	public void resourceDeleted(IBaseResource originalResource)
	{
		publishChange(originalResource.getIdElement(), "null");
	}

	private void publishChange(IIdType idElement, String resourceJson)
	{
		String resourceType = idElement.getResourceType();
		String compartment = determineCompartment(resourceType);
		String messageContent = String.format(MESSAGE_TEMPLATE, buildMessageId(idElement), resourceType,
				idElement.getIdPart(), compartment, resourceJson);
		emitToWal(compartment, messageContent);
	}

	private static String determineCompartment(String resourceType)
	{
		return "Patient"; // TODO: determine based on the resource type
	}

	private static String buildMessageId(IIdType idElement)
	{
		return idElement.getResourceType() + "/" + idElement.getIdPart() + "#" + idElement.getVersionIdPart();
	}

	public void emitToWal(String prefix, String content)
	{
		entityManager.unwrap(Session.class).doWork(new Work()
		{
			@Override
			public void execute(Connection connection) throws SQLException
			{
				try (Statement statement = connection.createStatement())
				{
					logger.info("Emitting message to WAL using prefix '{}'", prefix);
					statement.execute(String.format(SQL_EMIT_TEMPLATE, prefix, content));
				}
			}
		});
	}
}
