/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.james.jmap.model

import eu.timepit.refined.auto._

object DefaultCapabilities {
  val CORE_CAPABILITY = CoreCapability(
    properties = CoreCapabilityProperties(
      MaxSizeUpload(10_000_000L),
      MaxConcurrentUpload(4L),
      MaxSizeRequest(10_000_000L),
      MaxConcurrentRequests(4L),
      MaxCallsInRequest(16L),
      MaxObjectsInGet(500L),
      MaxObjectsInSet(500L),
      collationAlgorithms = List("i;unicode-casemap")))

  val MAIL_CAPABILITY = MailCapability(
    properties = MailCapabilityProperties(
      MaxMailboxesPerEmail(Some(10_000_000L)),
      MaxMailboxDepth(None),
      MaxSizeMailboxName(200L),
      MaxSizeAttachmentsPerEmail(20_000_000L),
      emailQuerySortOptions = List("receivedAt", "cc", "from", "to", "subject", "size", "sentAt", "hasKeyword", "uid", "Id"),
      MayCreateTopLevelMailbox(true)
    )
  )

  val SUPPORTED = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)
}

case class Capabilities(coreCapability: CoreCapability, mailCapability: MailCapability) {
  def toSet : Set[Capability] = Set(coreCapability, mailCapability)
}
