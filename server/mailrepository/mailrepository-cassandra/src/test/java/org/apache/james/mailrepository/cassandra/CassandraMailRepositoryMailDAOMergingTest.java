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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(CassandraRestartExtension.class)
class CassandraMailRepositoryMailDAOMergingTest extends CassandraMailRepositoryMailDAOTestSuite {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailRepositoryMailDAOTestFixture.MODULE);

    private static MergingCassandraMailRepositoryMailDao testee;
    private static CassandraMailRepositoryMailDAO v1;
    private static CassandraMailRepositoryMailDaoV2 v2;

    @BeforeAll
    static void setUp(CassandraCluster cassandra) {
        v1 = new CassandraMailRepositoryMailDAO(cassandra.getConf(), CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY, cassandra.getTypesProvider());
        v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY);
        testee = new MergingCassandraMailRepositoryMailDao(v1, v2);
    }

    @Override
    CassandraMailRepositoryMailDaoAPI testee() {
        return testee;
    }

    @Test
    void readShouldReturnV1Value() throws MessagingException {
        BlobId blobIdBody = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody");

        v1.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .build(),
            blobIdHeader,
            blobIdBody)
            .block();

        CassandraMailRepositoryMailDaoAPI.MailDTO actual = testee.read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block().get();
        Mail partialMail = actual.getMailBuilder().build();
        assertSoftly(softly -> {
            softly.assertThat(actual.getBodyBlobId()).isEqualTo(blobIdBody);
            softly.assertThat(actual.getHeaderBlobId()).isEqualTo(blobIdHeader);
            softly.assertThat(partialMail.getName()).isEqualTo(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString());
        });
    }

    @Test
    void readShouldReturnV2Value() throws MessagingException {
        BlobId blobIdBody = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody");

        v2.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .build(),
            blobIdHeader,
            blobIdBody)
            .block();

        CassandraMailRepositoryMailDaoAPI.MailDTO actual = testee.read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block().get();
        Mail partialMail = actual.getMailBuilder().build();
        assertSoftly(softly -> {
            softly.assertThat(actual.getBodyBlobId()).isEqualTo(blobIdBody);
            softly.assertThat(actual.getHeaderBlobId()).isEqualTo(blobIdHeader);
            softly.assertThat(partialMail.getName()).isEqualTo(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString());
        });
    }

    @Test
    void readShouldReturnV2ValueIfPresentInBoth() throws MessagingException {
        BlobId blobIdBody1 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdBody2 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader2");
        BlobId blobIdHeader1 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody");
        BlobId blobIdHeader2 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody2");

        v1.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .build(),
            blobIdHeader1,
            blobIdBody1)
            .block();

        v2.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .build(),
            blobIdHeader2,
            blobIdBody2)
            .block();

        CassandraMailRepositoryMailDaoAPI.MailDTO actual = testee.read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block().get();
        Mail partialMail = actual.getMailBuilder().build();
        assertSoftly(softly -> {
            softly.assertThat(actual.getBodyBlobId()).isEqualTo(blobIdBody2);
            softly.assertThat(actual.getHeaderBlobId()).isEqualTo(blobIdHeader2);
            softly.assertThat(partialMail.getName()).isEqualTo(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString());
        });
    }

    @Test
    void removeShouldRemoveInBOth() throws MessagingException {
        BlobId blobIdBody1 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdBody2 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader2");
        BlobId blobIdHeader1 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody");
        BlobId blobIdHeader2 = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody2");

        v1.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .build(),
            blobIdHeader1,
            blobIdBody1)
            .block();

        v2.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .build(),
            blobIdHeader2,
            blobIdBody2)
            .block();

        testee.remove(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block();

        Optional<CassandraMailRepositoryMailDaoAPI.MailDTO> v1Entry = v1.read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block();
        Optional<CassandraMailRepositoryMailDaoAPI.MailDTO> v2Entry = v2.read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block();
        assertThat(v1Entry).isEmpty();
        assertThat(v2Entry).isEmpty();
    }
}
