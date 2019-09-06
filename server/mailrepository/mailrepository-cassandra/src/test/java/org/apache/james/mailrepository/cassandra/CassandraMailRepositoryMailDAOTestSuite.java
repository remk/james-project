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

import org.apache.james.blob.api.BlobId;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

abstract class CassandraMailRepositoryMailDAOTestSuite {

    abstract CassandraMailRepositoryMailDaoAPI testee();

    @Test
    void storeShouldAcceptMailWithOnlyName() throws Exception {
        CassandraMailRepositoryMailDaoAPI testee = testee();
        BlobId blobIdBody = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody");

        testee.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
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
        });
    }

    @Test
    void removeShouldDeleteMailMetaData() throws Exception {
        CassandraMailRepositoryMailDaoAPI testee = testee();
        BlobId blobIdBody = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobHeader");
        BlobId blobIdHeader = CassandraMailRepositoryMailDAOTestFixture.BLOB_ID_FACTORY.from("blobBody");

        testee.store(CassandraMailRepositoryMailDAOTestFixture.URL,
            FakeMail.builder()
                .name(CassandraMailRepositoryMailDAOTestFixture.KEY_1.asString())
                .build(),
            blobIdHeader,
            blobIdBody)
            .block();

        testee.remove(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block();

        assertThat(testee.read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block())
            .isEmpty();
    }


    @Test
    void readShouldReturnEmptyWhenAbsent() {
        assertThat(testee().read(CassandraMailRepositoryMailDAOTestFixture.URL, CassandraMailRepositoryMailDAOTestFixture.KEY_1).block())
            .isEmpty();
    }
}
