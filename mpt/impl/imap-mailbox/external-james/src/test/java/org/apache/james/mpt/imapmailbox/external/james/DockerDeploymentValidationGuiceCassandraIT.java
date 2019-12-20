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

package org.apache.james.mpt.imapmailbox.external.james;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.es.DockerElasticSearchExtension;
import org.apache.james.core.Username;
import org.apache.james.mpt.imapmailbox.external.james.host.docker.CliProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Network;

public class DockerDeploymentValidationGuiceCassandraIT implements DeploymentValidation {

    private static String retrieveDockerImageName() {
        String imageName = System.getProperty("docker.image.cassandra");
        Assumptions.assumeThat(imageName)
            .describedAs("No property docker.image.cassandra defined to run integration-test")
            .isNotNull();
        return imageName;
    }

    private static final Network network = Network.newNetwork();
    private static final DockerCassandra cassandraContainer = new DockerCassandra("cassandra_3_11_3",
        DockerCassandra.AdditionalDockerFileStep.IDENTITY,
        container -> container.withNetworkAliases("cassandra")
            .withNetwork(network));
    private static final DockerCassandraExtension cassandraExtension = new DockerCassandraExtension(cassandraContainer);
    public static final String KEYSPACE = "apache_james";
    @RegisterExtension
    @Order(1)
    static CassandraClusterExtension cassandraClusterExtension = new CassandraClusterExtension(CassandraModule.aggregateModules(), cassandraExtension, KEYSPACE);
    @RegisterExtension
    @Order(2)
    static DockerElasticSearchExtension elasticSearchExtension = new DockerElasticSearchExtension(network, "elasticsearch");

    @RegisterExtension
    @Order(3)
    DockerJamesExtension dockerJamesRule = new DockerJamesExtension(retrieveDockerImageName(), CliProvisioningAPI.CliType.JAR, network);

    @BeforeEach
    void setUp() throws Exception {
        dockerJamesRule.getProvisioningAPI().addDomain(DOMAIN);
        dockerJamesRule.getProvisioningAPI().addUser(Username.of(USER_ADDRESS), PASSWORD);
    }

    @Override
    public ExternalJamesConfiguration getConfiguration() {
        return dockerJamesRule.getConfiguration();
    }

    @BeforeAll
    static void beforeAll() {
        elasticSearchExtension.start();
    }

    @AfterAll
    static void afterAll() {
        elasticSearchExtension.stop();

    }
}
