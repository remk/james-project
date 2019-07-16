/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.mailbox.tools.indexer;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.mailbox.tools.indexer.dto.SingleMailboxReindexingTaskDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

public class SingleMailboxReindexingTask implements Task {

    public static final String MAILBOX_RE_INDEXING = "mailboxReIndexing";

    public static class AdditionalInformation extends ReprocessingContextInformation {
        private final MailboxId mailboxId;

        AdditionalInformation(MailboxId mailboxId, ReprocessingContext reprocessingContext) {
            super(reprocessingContext);
            this.mailboxId = mailboxId;
        }

        public String getMailboxId() {
            return mailboxId.serialize();
        }
    }

    public static class Factory {

        private final ReIndexerPerformer reIndexerPerformer;
        private final MailboxId.Factory mailboxIdFactory;

        @Inject
        public Factory(ReIndexerPerformer reIndexerPerformer, MailboxId.Factory mailboxIdFactory) {
            this.reIndexerPerformer = reIndexerPerformer;
            this.mailboxIdFactory = mailboxIdFactory;
        }

        public SingleMailboxReindexingTask create(SingleMailboxReindexingTaskDTO dto) {
            MailboxId mailboxId = mailboxIdFactory.fromString(dto.getMailboxId());
            return new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId);
        }
    }

    private final ReIndexerPerformer reIndexerPerformer;
    private final MailboxId mailboxId;
    private final AdditionalInformation additionalInformation;
    private final ReprocessingContext reprocessingContext;

    @Inject
    public SingleMailboxReindexingTask(ReIndexerPerformer reIndexerPerformer, MailboxId mailboxId) {
        this.reIndexerPerformer = reIndexerPerformer;
        this.mailboxId = mailboxId;
        this.reprocessingContext = new ReprocessingContext();
        this.additionalInformation = new AdditionalInformation(mailboxId, reprocessingContext);
    }

    @Override
    public Result run() {
        try {
            return reIndexerPerformer.reIndex(mailboxId, reprocessingContext);
        } catch (Exception e) {
            return Result.PARTIAL;
        }
    }

    @Override
    public String type() {
        return MAILBOX_RE_INDEXING;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(additionalInformation);
    }

}
