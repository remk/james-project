package org.apache.james.server.task.json;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.CompletedTaskDTO;
import org.apache.james.server.task.json.dto.FailedTaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.FailedTask;
import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class TaskSerializationTest {

    public static final String SERIALIZED_FAILED_TASK = "{\"type\": \"failed-task\"}";
    private TaskDTOModule failedTaskModule = DTOModule
            .forDomainObject(FailedTask.class)
            .convertToDTO(FailedTaskDTO.class)
            .toDomainObjectConverter(dto -> new FailedTask())
            .toDTOConverter((task, typeName) -> new FailedTaskDTO(typeName))
            .typeName("failed-task")
            .withFactory(TaskDTOModule::new);

    public static final String SERIALIZED_COMPLETED_TASK = "{\"type\": \"completed-task\"}";
    private TaskDTOModule completedTaskModule = DTOModule
        .forDomainObject(CompletedTask.class)
        .convertToDTO(CompletedTaskDTO.class)
        .toDomainObjectConverter(dto -> new CompletedTask())
        .toDTOConverter((task, typeName) -> new CompletedTaskDTO(typeName))
        .typeName("completed-task")
        .withFactory(TaskDTOModule::new);
    
    @BeforeEach
    void setUp() {
    }

    @Test
    void failedTaskShouldSerialize() throws JsonProcessingException {
        FailedTask failedTask = new FailedTask();

        String actual = new JsonTaskSerializer(failedTaskModule).serialize(failedTask);
        assertThatJson(actual).isEqualTo(SERIALIZED_FAILED_TASK);
    }

    @Test
    void failedTaskShouldDeserialize() throws IOException {
        Task task = new JsonTaskSerializer(failedTaskModule).deserialize(SERIALIZED_FAILED_TASK);
        assertThat(task).isInstanceOf(FailedTask.class);
    }

    @Test
    void completedTaskShouldSerialize() throws JsonProcessingException {
        CompletedTask completedTask = new CompletedTask();

        String actual = new JsonTaskSerializer(completedTaskModule).serialize(completedTask);
        assertThatJson(actual).isEqualTo(SERIALIZED_COMPLETED_TASK);
    }

    @Test
    void completedTaskShouldDeserialize() throws IOException {
        Task task = new JsonTaskSerializer(completedTaskModule).deserialize(SERIALIZED_COMPLETED_TASK);
        assertThat(task).isInstanceOf(CompletedTask.class);
    }
}
