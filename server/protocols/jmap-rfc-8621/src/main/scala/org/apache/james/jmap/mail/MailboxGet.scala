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

package org.apache.james.jmap.mail

import org.apache.james.jmap.mail.MailboxSetRequest.UnparsedMailboxId
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, Properties}

case class Ids(value: List[UnparsedMailboxId])

case class MailboxGetRequest(accountId: AccountId,
                             ids: Option[Ids],
                             properties: Option[Properties]) extends WithAccountId

case class NotFound(value: Set[UnparsedMailboxId]) {
  def merge(other: NotFound): NotFound = NotFound(this.value ++ other.value)
}

case class MailboxGetResponse(accountId: AccountId,
                              state: State,
                              list: List[Mailbox],
                              notFound: NotFound)
