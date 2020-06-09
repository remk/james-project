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

package org.apache.james.mailets;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.transport.matchers.SizeGreaterThan;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SizeGreaterThanIntegrationTest {
    public static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;
    public static final String BOUNCE_RECEIVER = "bounce.receiver@" + DEFAULT_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void sizeGreaterThanMatcherShouldBounceWhenSizeExceeded() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration())
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessageWithHeaders(BOUNCE_RECEIVER, RECIPIENT, "01234567\r\n".repeat(1025));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOUNCE_RECEIVER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void sizeGreaterThanMatcherShouldNotBounceWhenSizeWithinLimit() throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(
                generateMailetContainerConfiguration())
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(BOUNCE_RECEIVER, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessageWithHeaders(BOUNCE_RECEIVER, RECIPIENT, "01234567\r\n".repeat(1000));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    private MailetContainer.Builder generateMailetContainerConfiguration() {
        return TemporaryJamesServer.DEFAULT_MAILET_CONTAINER_CONFIGURATION
            .postmaster(POSTMASTER)
            .putProcessor(transport())
            .putProcessor(bounces());
    }

    private ProcessorConfiguration.Builder transport() {
        // This processor delivers emails to BOUNCE_RECEIVER and POSTMASTER
        // Other recipients will be bouncing
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(BOUNCE_RECEIVER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(POSTMASTER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.TO_BOUNCE);
    }

    public static ProcessorConfiguration.Builder bounces() {
        return ProcessorConfiguration.bounces()
            .addMailet(MailetConfiguration.builder()
                .matcher(SizeGreaterThan.class)
                .matcherCondition("10k")
                .mailet(DSNBounce.class)
                .addProperty("passThrough", "false")
            )
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(LocalDelivery.class));
    }
}
