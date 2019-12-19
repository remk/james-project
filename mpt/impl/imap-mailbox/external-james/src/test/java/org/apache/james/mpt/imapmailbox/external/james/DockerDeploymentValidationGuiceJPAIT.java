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

import org.apache.james.core.Username;
import org.apache.james.mpt.imapmailbox.external.james.host.docker.CliProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Network;

public class DockerDeploymentValidationGuiceJPAIT implements DeploymentValidation {

    private static String retrieveDockerImageName() {
        String imageName = System.getProperty("docker.image.jpa");
        Assumptions.assumeThat(imageName)
            .describedAs("No property docker.image.jpa defined to run integration-test")
            .isNotNull();
        return imageName;
    }

    @RegisterExtension
    public DockerJamesExtension dockerJamesRule = new DockerJamesExtension(retrieveDockerImageName(), CliProvisioningAPI.CliType.JAR, Network.newNetwork());

    @BeforeEach
    public void setUp() throws Exception {
        dockerJamesRule.getProvisioningAPI().addDomain(DOMAIN);
        dockerJamesRule.getProvisioningAPI().addUser(Username.of(USER_ADDRESS), PASSWORD);
    }

    @Override
    public ExternalJamesConfiguration getConfiguration() {
        return dockerJamesRule.getConfiguration();
    }

}
