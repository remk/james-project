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
package org.apache.james.mailrepository.cassandra;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

@ExtendWith(CassandraRestartExtension.class)
class CassandraMailRepositoryMailDAOV1Test extends CassandraMailRepositoryMailDAOTestSuite {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailRepositoryMailDAOTestFixture.MODULE);

    private static CassandraMailRepositoryMailDAO testee;

    @BeforeAll
    static void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailRepositoryMailDAO(cassandra.getConf(), CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY, cassandra.getTypesProvider());
    }

    @Override
    CassandraMailRepositoryMailDaoAPI testee() {
        return testee;
    }

    @Test
    void readShouldReturnAllMailMetadata() throws Exception {
        CassandraMailRepositoryMailDaoAPI testee = testee();

        BlobId blobIdBody = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody");
        String errorMessage = "error message";
        String state = "state";
        String remoteAddr = "remoteAddr";
        String remoteHost = "remoteHost";
        PerRecipientHeaders.Header header = PerRecipientHeaders.Header.builder().name("headerName").value("headerValue").build();
        ImmutableList<String> attributeValue = ImmutableList.of("value1", "value2");
        Attribute attribute = Attribute.convertToAttribute("att1", attributeValue);

        testee.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .sender(MailAddressFixture.SENDER)
                .recipients(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2)
                .errorMessage(errorMessage)
                .state(state)
                .remoteAddr(remoteAddr)
                .remoteHost(remoteHost)
                .addHeaderForRecipient(header, MailAddressFixture.RECIPIENT1)
                .attribute(attribute)
                .build(),
            blobIdHeader,
            blobIdBody)
            .block();

        CassandraMailRepositoryMailDAO.MailDTO mailDTO = testee.read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block().get();

        Mail partialMail = mailDTO.getMailBuilder().build();
        assertSoftly(softly -> {
            softly.assertThat(mailDTO.getBodyBlobId()).isEqualTo(blobIdBody);
            softly.assertThat(mailDTO.getHeaderBlobId()).isEqualTo(blobIdHeader);
            softly.assertThat(partialMail.getName()).isEqualTo(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString());
            softly.assertThat(partialMail.getErrorMessage()).isEqualTo(errorMessage);
            softly.assertThat(partialMail.getState()).isEqualTo(state);
            softly.assertThat(partialMail.getRemoteAddr()).isEqualTo(remoteAddr);
            softly.assertThat(partialMail.getRemoteHost()).isEqualTo(remoteHost);
            softly.assertThat(partialMail.attributes()).containsOnly(attribute);
            softly.assertThat(partialMail.getAttribute(attribute.getName())).contains(attribute);
            softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getRecipientsWithSpecificHeaders())
                .containsOnly(MailAddressFixture.RECIPIENT1);
            softly.assertThat(partialMail.getPerRecipientSpecificHeaders().getHeadersForRecipient(MailAddressFixture.RECIPIENT1))
                .containsOnly(header);
            softly.assertThat(partialMail.getMaybeSender().asOptional()).contains(MailAddressFixture.SENDER);
            softly.assertThat(partialMail.getRecipients()).containsOnly(MailAddressFixture.RECIPIENT1, MailAddressFixture.RECIPIENT2);
        });
    }
}
