/** **************************************************************
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
 * ***************************************************************/

package org.apache.james.task.eventsourcing.cassandra;

import java.util.function.Supplier;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjectionContract;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTaskExecutionDetailsProjectionTest implements TaskExecutionDetailsProjectionContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE, CassandraZonedDateTimeModule.MODULE, CassandraTaskExecutionDetailsProjectionModule.MODULE()));

    private static Supplier<CassandraTaskExecutionDetailsProjection> testeeSupplier;

    @BeforeAll
    static void setUp(CassandraCluster cassandra) {
        CassandraTaskExecutionDetailsProjectionDAO cassandraTaskExecutionDetailsProjectionDAO = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider());
        testeeSupplier = () -> new CassandraTaskExecutionDetailsProjection(cassandraTaskExecutionDetailsProjectionDAO);
    }

    @Override
    public TaskExecutionDetailsProjection testee() {
        return testeeSupplier.get();
    }

    @Test
    @Disabled("Serialization and deserialization of additionalInformations is not implemented")
    public void readDetailsShouldBeAbleToRetrieveASavedRecordWithAdditionalInformation() {
    }
}
