package org.apache.james.server.task.json;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.apache.james.task.Task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskDeserializer {

    public interface Factory {
        Task create(JsonNode parameters);
    }

    private final Map<String, Factory> registry;

    public TaskDeserializer(Map<String, Factory> registry) {
        this.registry = registry;
    }

    public Task deserialize(String taskAsString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode taskAsJson = objectMapper.readTree(taskAsString);
        JsonNode parameters = taskAsJson.get("parameters");

        return getFactory(taskAsJson).create(parameters);
    }

    private Factory getFactory(JsonNode taskAsJson) {
        String type = Optional.ofNullable(taskAsJson.get("type"))
            .map(JsonNode::asText)
            .orElseThrow(() -> new InvalidTaskException());
        return Optional.ofNullable(registry.get(type))
            .orElseThrow(() -> new UnsupportedTypeException(type));

    }
}
