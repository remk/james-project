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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Duration.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.TEN_SECONDS;

import java.io.IOException;
import java.util.Locale;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.mpt.script.SimpleScriptedTestProtocol;
import org.apache.james.utils.SMTPMessageSender;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

public interface DeploymentValidation {

    String DOMAIN = "domain";
    String USER = "imapuser";
    String USER_ADDRESS = USER + "@" + DOMAIN;
    String PASSWORD = "password";
    String INBOX = "INBOX";
    String ONE_MAIL = "* 1 EXISTS";

    ImapHostSystem imapHostSystem();

    SmtpHostSystem smtpHostSystem();

    ExternalJamesConfiguration getConfiguration();

    Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(slowPacedPollInterval)
        .await();
    ConditionFactory awaitAtMostTenSeconds = calmlyAwait.atMost(TEN_SECONDS);



    default SimpleScriptedTestProtocol simpleScriptedTestProtocol() throws Exception {
         return new SimpleScriptedTestProtocol("/org/apache/james/imap/scripts/", imapHostSystem())
            .withUser(USER_ADDRESS, PASSWORD)
            .withLocale(Locale.US);
    }

    @Test
    default void validateDeployment() throws Exception {
        simpleScriptedTestProtocol().run("ValidateDeployment");
    }

    @Test
    default void selectThenFetchWithExistingMessages() throws Exception {
        simpleScriptedTestProtocol().run("SelectThenFetchWithExistingMessages");
    }

    @Test
    default void validateDeploymentWithMailsFromSmtp() throws Exception {
        IMAPClient imapClient = new IMAPClient();
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender("another-domain");
        smtpHostSystem().connect(smtpMessageSender).sendMessage("test@" + DOMAIN, USER_ADDRESS);
        imapClient.connect(getConfiguration().getAddress(), getConfiguration().getImapPort().getValue());
        imapClient.login(USER_ADDRESS, PASSWORD);
        awaitAtMostTenSeconds.untilAsserted(() -> checkMailDelivery(imapClient));
    }

    default void checkMailDelivery(IMAPClient imapClient) throws IOException {
        imapClient.select(INBOX);
        String replyString = imapClient.getReplyString();
        assertThat(replyString).contains(ONE_MAIL);
    }
}
