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

import java.time.Duration;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.ProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.StaticJamesConfiguration;
import org.apache.james.mpt.imapmailbox.external.james.host.docker.CliProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.util.Port;
import org.apache.james.util.docker.DockerContainer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class DockerJamesExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerJamesExtension.class);

    private static final int IMAP_PORT = 143;
    private static final int SMTP_PORT = 587;
    private static final int WEBADMIN_PORT = 8000;

    private final DockerContainer container;

    private ImapHostSystem system;
    private SmtpHostSystem smtpHostSystem;
    private final ThrowingSupplier<ProvisioningAPI> provisioningAPI;


    public DockerJamesExtension(DockerContainer container, CliProvisioningAPI.CliType cliProvisioningType) {
        this.container = container;
        this.provisioningAPI = () -> new CliProvisioningAPI(cliProvisioningType, container);
    }

    public DockerJamesExtension(String image, CliProvisioningAPI.CliType cliProvisioningType) {
        this(DockerContainer.fromName(image)
            .withExposedPorts(SMTP_PORT, IMAP_PORT)
            .waitingFor(new HostPortWaitStrategy())
            .withStartupTimeout(Duration.ofMinutes(60))
            .withLogConsumer(frame -> {
                switch (frame.getType()) {
                    case STDOUT:
                        LOGGER.info(frame.getUtf8String());
                        break;
                    case STDERR:
                        LOGGER.error(frame.getUtf8String());
                        break;
                    case END:
                        break; //Ignore
                }
            }), cliProvisioningType);
    }


    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void pause() {
        container.pause();
    }

    public void unpause() {
        container.unpause();
    }

    public ExternalJamesConfiguration getConfiguration() {
        return new StaticJamesConfiguration("localhost", getMappedPort(IMAP_PORT), getMappedPort(SMTP_PORT));
    }

    public Port getWebadminPort() {
        return getMappedPort(WEBADMIN_PORT);
    }

    private Port getMappedPort(int port) {
        return Port.of(container.getMappedPort(port));
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        start();
        Injector injector = Guice.createInjector(new ExternalJamesModule(getConfiguration(), getProvisioningAPI()));
        system = injector.getInstance(ImapHostSystem.class);
        smtpHostSystem = injector.getInstance(SmtpHostSystem.class);
        system.beforeTest();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        stop();
        system.afterTest();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> parameterType = parameterContext.getParameter().getType();
        return parameterType == ImapHostSystem.class || parameterType == SmtpHostSystem.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> parameterType = parameterContext.getParameter().getType();
        if (parameterType == ImapHostSystem.class) {
            return system;
        } else if (parameterType == SmtpHostSystem.class) {
            return smtpHostSystem;
        }
        return null;
    }

    public ProvisioningAPI getProvisioningAPI() throws Exception {
        try {
            return provisioningAPI.get();
        } catch (Exception e) {
            throw e;
        }
        catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
