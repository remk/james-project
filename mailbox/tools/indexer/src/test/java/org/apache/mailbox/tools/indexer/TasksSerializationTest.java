package org.apache.mailbox.tools.indexer;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.apache.james.mailbox.model.TestId;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.mailbox.tools.indexer.dto.SingleMailboxReindexingTaskDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

class TasksSerializationTest {

    private SingleMailboxReindexingTask.Factory factory;
    private TaskDTOModule module;
    private ReIndexerPerformer reIndexerPerformer;

    @BeforeEach
    void setUp() {
        reIndexerPerformer = mock(ReIndexerPerformer.class);
        factory = new SingleMailboxReindexingTask.Factory(reIndexerPerformer, new TestId.Factory());
        module = TaskDTOModule
            .forTask(SingleMailboxReindexingTask.class)
            .convertToDTO(SingleMailboxReindexingTaskDTO.class)
            .toDomainObjectConverter(factory::create)
            .toDTOConverter((task, typeName) ->
                new SingleMailboxReindexingTaskDTO(typeName, task.getMailboxId().serialize())
            )
            .typeName("mailbox-reindexer")
            .withFactory(TaskDTOModule::new);
    }

    @Test
    void singleMailboxReindexingShouldBeSerializable() throws JsonProcessingException {
        TestId mailboxId = TestId.of(1L);
        SingleMailboxReindexingTask task = new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId);

        assertThatJson(new JsonTaskSerializer(module).serialize(task))
                .isEqualTo("{\"type\": \"mailbox-reindexer\", \"mailboxId\": \"1\"}");
    }

    @Test
    void singleMailboxReindexingShouldBeDeserializable() throws IOException {
        TestId mailboxId = TestId.of(1L);
        SingleMailboxReindexingTask task = new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId);

        String serializedTask = "{\"type\": \"mailbox-reindexer\", \"mailboxId\": \"1\"}";
        assertThat(new JsonTaskSerializer(module).deserialize(serializedTask))
            .isEqualToComparingOnlyGivenFields(task, "reIndexerPerformer", "mailboxId");
    }
}