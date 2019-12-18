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

import static org.hamcrest.CoreMatchers.notNullValue;

import org.apache.james.core.Username;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.ProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class DockerDeploymentValidationSpringJPAIT extends DeploymentValidation {

    private ImapHostSystem system;
    private SmtpHostSystem smtpHostSystem;

    private static String retrieveDockerImageName() {
        String imageName = System.getProperty("docker.image.spring.jpa");
        Assume.assumeThat("No property docker.image.spring.jpa defined to run integration-test", imageName, notNullValue());
        return imageName;
    }

    @Rule
    public DockerJamesRule dockerJamesRule = new DockerJamesRule(retrieveDockerImageName());

    @Override
    @Before
    public void setUp() throws Exception {

        dockerJamesRule.start();

        ProvisioningAPI provisioningAPI = dockerJamesRule.cliShellDomainsAndUsersAdder();
        Injector injector = Guice.createInjector(new ExternalJamesModule(getConfiguration(), provisioningAPI));
        system = injector.getInstance(ImapHostSystem.class);
        provisioningAPI.addDomain(DOMAIN);
        provisioningAPI.addUser(Username.of(USER_ADDRESS), PASSWORD);
        smtpHostSystem = injector.getInstance(SmtpHostSystem.class);
        system.beforeTest();

        super.setUp();
    }

    @Test
    @Override
    public void validateDeployment() throws Exception {
    }

    @Test
    @Override
    public void validateDeploymentWithMailsFromSmtp() throws Exception {
    }

    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }

    @Override
    protected SmtpHostSystem createSmtpHostSystem() {
        return smtpHostSystem;
    }

    @Override
    protected ExternalJamesConfiguration getConfiguration() {
        return dockerJamesRule.getConfiguration();
    }

    @After
    public void tearDown() throws Exception {
        system.afterTest();
        dockerJamesRule.stop();
    }

}
