/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.mailbox.cassandra.mail.migration;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.apache.james.server.task.json.JsonTaskAdditionalInformationsSerializer;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.TaskExecutionDetails;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

class MailboxPathV2MigrationTaskSerializationTest {
    private static final MailboxPathV2Migration MIGRATION = mock(MailboxPathV2Migration.class);
    private static final MailboxPathV2Migration.MailboxPathV2MigrationTask TASK = new MailboxPathV2Migration.MailboxPathV2MigrationTask(MIGRATION);
    private static final String SERIALIZED_TASK = "{\"type\": \"Cassandra_mailboxPathV2Migration\"}";
    private static final MailboxPathV2Migration.AdditionalInformation DETAILS = new MailboxPathV2Migration.AdditionalInformation(42L, 10);
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{\"type\": \"Cassandra_mailboxPathV2Migration\", \"remainingCount\":42,\"initialCount\":10}";

    private static final JsonTaskSerializer TASK_SERIALIZER = new JsonTaskSerializer(MailboxPathV2MigrationTaskDTO.MODULE.apply(MIGRATION));
    private static final JsonTaskAdditionalInformationsSerializer JSON_TASK_ADDITIONAL_INFORMATIONS_SERIALIZER = new JsonTaskAdditionalInformationsSerializer(MailboxPathV2MigrationTaskAdditionalInformationDTO.MODULE);

    @Test
    void taskShouldBeSerializable() throws JsonProcessingException {
        assertThatJson(TASK_SERIALIZER.serialize(TASK))
            .isEqualTo(SERIALIZED_TASK);
    }

    @Test
    void taskShouldBeDeserializable() throws IOException {
        assertThat(TASK_SERIALIZER.deserialize(SERIALIZED_TASK))
            .isEqualToComparingFieldByField(TASK);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws JsonProcessingException {
        assertThatJson(JSON_TASK_ADDITIONAL_INFORMATIONS_SERIALIZER.serialize(DETAILS)).isEqualTo(SERIALIZED_ADDITIONAL_INFORMATION);
    }

    @Test
    void additonalInformationShouldBeDeserializable() throws IOException {
        TaskExecutionDetails.AdditionalInformation deserialized = JSON_TASK_ADDITIONAL_INFORMATIONS_SERIALIZER.deserialize(SERIALIZED_ADDITIONAL_INFORMATION);
        assertThat(deserialized).isInstanceOf(MailboxPathV2Migration.AdditionalInformation.class);

        MailboxPathV2Migration.AdditionalInformation additionalInformation = (MailboxPathV2Migration.AdditionalInformation) deserialized;
        assertThat(additionalInformation.getRemainingCount()).isEqualTo(DETAILS.getRemainingCount());
        assertThat(additionalInformation.getInitialCount()).isEqualTo(DETAILS.getInitialCount());
    }
}
