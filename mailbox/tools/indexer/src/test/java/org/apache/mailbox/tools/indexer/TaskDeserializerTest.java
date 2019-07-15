package org.apache.mailbox.tools.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

class TaskDeserializerTest {

    static final String TASK_AS_STRING = "{" +
            "\"type\": \"mailboxReIndexing\"," +
            "\"parameters\": {\"mailboxId\": \"1\"}" +
            "}";

    static final String UNREGISTERED_TASK_AS_STRING = "{" +
        "\"type\": \"unknown\"," +
        "\"parameters\": {\"mailboxId\": \"1\"}" +
        "}";

    static final String MISSING_TASK_AS_STRING = "{" +
        "\"parameters\": {\"mailboxId\": \"1\"}" +
        "}";

    private TaskDeserializer testee;

    @BeforeEach
    void setUp() {
        ImmutableMap<String, TaskDeserializer.Factory> registry = ImmutableMap.of(SingleMailboxReindexingTask.MAILBOX_RE_INDEXING, new TaskDeserializer.SingleMailboxReindexingTaskFactory());
        testee = new TaskDeserializer(registry);
    }

    @Test
    void shouldDeserializeTaskWithRegisteredType() throws IOException {
        Task task = testee.deserialize(TASK_AS_STRING);
        assertThat(task).isInstanceOf(SingleMailboxReindexingTask.class);
        assertThat(task.parameters()).isEqualTo(ImmutableMap.of("mailboxId", "1"));
    }

    @Test
    void shouldThrowWhenNotRegisteredType() {
       assertThatThrownBy(() -> testee.deserialize(UNREGISTERED_TASK_AS_STRING)).isInstanceOf(UnsupportedTypeException.class);
    }


    @Test
    void shouldThrowWhenMissingType() {
        assertThatThrownBy(() -> testee.deserialize(MISSING_TASK_AS_STRING)).isInstanceOf(InvalidTaskException.class);
    }



}