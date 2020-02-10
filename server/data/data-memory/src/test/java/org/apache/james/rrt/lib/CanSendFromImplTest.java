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
package org.apache.james.rrt.lib;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public class CanSendFromImplTest implements CanSendFromContract {

    AbstractRecipientRewriteTable recipientRewriteTable;
    CanSendFrom canSendFrom;

    @BeforeEach
    void setup() throws Exception {
        recipientRewriteTable = new MemoryRecipientRewriteTable();

        DNSService dnsService = Mockito.mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false));
        domainList.addDomain(DOMAIN);
        domainList.addDomain(OTHER_DOMAIN);
        recipientRewriteTable.setDomainList(domainList);

        canSendFrom = new CanSendFromImpl(recipientRewriteTable);
    }

    @Override
    public CanSendFrom canSendFrom() {
        return canSendFrom;
    }

    @Override
    public void addAliasMapping(Username alias, Username user) throws Exception {
        recipientRewriteTable.addAliasMapping(MappingSource.fromUser(alias.getLocalPart(), alias.getDomainPart().get()), user.asString());
    }

    @Override
    public void addDomainMapping(Domain alias, Domain domain) throws Exception {
        recipientRewriteTable.addAliasDomainMapping(MappingSource.fromDomain(alias), domain);
    }

    @Override
    public void addGroupMapping(String group, Username user) throws Exception {
        recipientRewriteTable.addGroupMapping(MappingSource.fromUser(Username.of(group)), user.asString());
    }
}
