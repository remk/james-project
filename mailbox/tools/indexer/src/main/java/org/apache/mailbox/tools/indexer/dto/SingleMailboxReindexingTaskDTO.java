package org.apache.mailbox.tools.indexer.dto;

import org.apache.james.server.task.json.dto.TaskDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SingleMailboxReindexingTaskDTO implements TaskDTO {

    private final String type;
    private final String mailboxId;

    public SingleMailboxReindexingTaskDTO(@JsonProperty("type") String type, @JsonProperty("mailboxId") String mailboxId) {
        this.type = type;
        this.mailboxId = mailboxId;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getMailboxId() {
        return mailboxId;
    }
}
