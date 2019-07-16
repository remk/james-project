/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.mailbox.tools.indexer;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.mockito.Mockito.mock;

import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;


class TaskSerializerTest {

    public static final String TASK_AS_STRING = "{" +
            "\"type\": \"mailboxReIndexing\"," +
            "\"parameters\": {\"mailboxId\": \"1\"}" +
            "}";

    @Test
    void shouldSerializeTaskWithItsType() {
        TaskSerializer testee = new TaskSerializer();
        ReIndexerPerformer reIndexerPerformer = mock(ReIndexerPerformer.class);
        TestId mailboxId = TestId.of(1L);
        SingleMailboxReindexingTask task = new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId);
        assertThatJson(testee.serialize(task).toString()).isEqualTo(TASK_AS_STRING);
    }

}