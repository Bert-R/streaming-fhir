package io.github.bertr.streamingfhir.dbz;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.header.Headers;
import org.apache.kafka.connect.transforms.Transformation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HapiMessageTransformation<R extends ConnectRecord<R>> implements Transformation<R>
{
    public static final String MESSAGE_ID_FIELD_NAME = "messageId";
    public static final String RESOURCE_TYPE_FIELD_NAME = "resourceType";
    public static final String RESOURCE_ID_FIELD_NAME = "resourceId";
    private static final Schema KEY_SCHEMA = SchemaBuilder.struct().name("io.github.bertr.streamingfhir..FhirResourceKey")
            .field(MESSAGE_ID_FIELD_NAME, Schema.STRING_SCHEMA)
            .field(RESOURCE_TYPE_FIELD_NAME, Schema.STRING_SCHEMA)
            .field(RESOURCE_ID_FIELD_NAME, Schema.STRING_SCHEMA)
            .build();

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public R apply(R rec)
    {
        JsonNode walMessageJson = parseJson(new String(((Struct) rec.value()).getStruct("message").getBytes("content")));
        Struct outKey = new Struct(KEY_SCHEMA)
                .put(MESSAGE_ID_FIELD_NAME, getText(walMessageJson, "id"))
                .put(RESOURCE_TYPE_FIELD_NAME, getText(walMessageJson, "aggregate_type"))
                .put(RESOURCE_ID_FIELD_NAME, getText(walMessageJson, "aggregate_id"));
        Headers headers = rec.headers();
        headers.add("fhir-version", "R4", Schema.STRING_SCHEMA);
        return rec.newRecord(determineTopic(walMessageJson.get("compartments")), null, KEY_SCHEMA, outKey, Schema.STRING_SCHEMA, serializeJson(walMessageJson.get("payload")), rec.timestamp(), headers);
    }

    private static String getText(JsonNode json, String fieldName)
    {
        return json.get(fieldName).asText();
    }

    private JsonNode parseJson(String jsonString)
    {
        try {
            return this.objectMapper.readTree(jsonString);
        } catch (JsonProcessingException e) {
            throw new UnexpectedException(e);
        }
    }

    private String serializeJson(JsonNode resourceBody)
    {
        try {
            return objectMapper.writeValueAsString(resourceBody);
        } catch (JsonProcessingException e) {
            throw new UnexpectedException(e);
        }
    }

    private String determineTopic(JsonNode compartmentNode)
    {
        for (JsonNode n : compartmentNode) {
            if (n.asText().equals("Patient")) {
                return "patient";
            }
        }
        return "nonpatient";
    }

    @Override
    public ConfigDef config()
    {
        return new ConfigDef();
    }

    @Override
    public void close()
    {
        // Not needed
    }

    @Override
    public void configure(Map<String, ?> configs)
    {
        // Not needed
    }
}
