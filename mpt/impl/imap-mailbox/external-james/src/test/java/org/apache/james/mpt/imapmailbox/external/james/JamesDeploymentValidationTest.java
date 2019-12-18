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

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfigurationEnvironnementVariables;
import org.apache.james.mpt.imapmailbox.external.james.host.external.NoopDomainsAndUserAdder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class JamesDeploymentValidationTest implements DeploymentValidation {

    public static class HostSystemsResolver implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

        private static ImapHostSystem system;
        private static SmtpHostSystem smtpHostSystem;
        private final ExternalJamesConfiguration configuration = new ExternalJamesConfigurationEnvironnementVariables();

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            Injector injector = Guice.createInjector(new ExternalJamesModule(configuration, new NoopDomainsAndUserAdder()));
            system = injector.getInstance(ImapHostSystem.class);
            smtpHostSystem = injector.getInstance(SmtpHostSystem.class);
            system.beforeTest();
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            system.afterTest();
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            Class<?> parameterType = parameterContext.getParameter().getType();
            return ImmutableSet.of(ImapHostSystem.class, parameterType == SmtpHostSystem.class, ExternalJamesConfiguration.class)
                .contains(parameterType);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            Class<?> parameterType = parameterContext.getParameter().getType();
            if (parameterType == ImapHostSystem.class) {
                return system;
            } else if (parameterType == SmtpHostSystem.class) {
                return smtpHostSystem;
            } else if (parameterType == ExternalJamesConfiguration.class) {
                return configuration;
            }
            return null;
        }
    }

    @RegisterExtension
    HostSystemsResolver hostSystemsResolver = new HostSystemsResolver();

}
