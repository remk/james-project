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
package org.apache.james.jmap.method

import java.time.ZoneId

import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import javax.inject.Inject
import org.apache.james.jmap.api.model.Preview
import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.json.{EmailGetSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.Email.UnparsedEmailId
import org.apache.james.jmap.mail.{Email, EmailBodyPart, EmailGetRequest, EmailGetResponse, EmailIds, EmailNotFound, EmailView, EmailViewReaderFactory, SpecificHeaderRequest}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.State.INSTANCE
import org.apache.james.jmap.model.{AccountId, Capabilities, ErrorCode, Invocation, Properties}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MessageId
import org.apache.james.metrics.api.MetricFactory
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

object EmailGetResults {
  private val logger: Logger = LoggerFactory.getLogger(classOf[EmailGetResults])

  def merge(result1: EmailGetResults, result2: EmailGetResults): EmailGetResults = result1.merge(result2)
  def empty(): EmailGetResults = EmailGetResults(Set.empty, EmailNotFound(Set.empty))
  def found(email: EmailView): EmailGetResults = EmailGetResults(Set(email), EmailNotFound(Set.empty))
  def notFound(emailId: UnparsedEmailId): EmailGetResults = EmailGetResults(Set.empty, EmailNotFound(Set(emailId)))
  def notFound(messageId: MessageId): EmailGetResults = Email.asUnparsed(messageId)
    .fold(e => {
        logger.error("messageId is not a valid UnparsedEmailId", e)
        empty()
      },
      id => notFound(id))
}

case class EmailGetResults(emails: Set[EmailView], notFound: EmailNotFound) {
  def merge(other: EmailGetResults): EmailGetResults = EmailGetResults(this.emails ++ other.emails, this.notFound.merge(other.notFound))

  def asResponse(accountId: AccountId): EmailGetResponse = EmailGetResponse(
    accountId = accountId,
    state = INSTANCE,
    list = emails.toList,
    notFound = notFound)
}

object EmailGetMethod {
  private val logger: Logger = LoggerFactory.getLogger(classOf[EmailGetMethod])
}

trait ZoneIdProvider {
  def get(): ZoneId
}

class SystemZoneIdProvider extends ZoneIdProvider {
  override def get(): ZoneId = ZoneId.systemDefault()
}

class EmailGetMethod @Inject() (readerFactory: EmailViewReaderFactory,
                                messageIdFactory: MessageId.Factory,
                                zoneIdProvider: ZoneIdProvider,
                                previewFactory: Preview.Factory,
                                val metricFactory: MetricFactory,
                                val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[EmailGetRequest] {
  override val methodName = MethodName("Email/get")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailGetRequest): SMono[InvocationWithContext] = {
    computeResponseInvocation(request, invocation.invocation, mailboxSession).onErrorResume({
      case e: IllegalArgumentException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.invocation.methodCallId))
      case e: Throwable => SMono.raiseError(e)
    }).map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[EmailGetRequest] = asEmailGetRequest(invocation.arguments)

  private def computeResponseInvocation(request: EmailGetRequest, invocation: Invocation, mailboxSession: MailboxSession): SMono[Invocation] =
    validateProperties(request)
      .flatMap(properties => validateBodyProperties(request).map((properties, _)))
      .fold(
        e => SMono.raiseError(e), {
          case (properties, bodyProperties) => getEmails(request, properties, mailboxSession)
            .map(response => Invocation(
              methodName = methodName,
              arguments = Arguments(EmailGetSerializer.serialize(response, properties, bodyProperties).as[JsObject]),
              methodCallId = invocation.methodCallId))
        })

  private def validateProperties(request: EmailGetRequest): Either[IllegalArgumentException, Properties] =
    request.properties match {
      case None => Right(Email.defaultProperties)
      case Some(properties) =>
        val invalidProperties: Set[NonEmptyString] = properties.value
          .flatMap(property => SpecificHeaderRequest.from(property)
            .fold(
              invalidProperty => Some(invalidProperty),
              _ => None
            )) -- Email.allowedProperties.value

        if (invalidProperties.nonEmpty) {
          Left(new IllegalArgumentException(s"The following properties [${invalidProperties.map(p => p.value).mkString(", ")}] do not exist."))
        } else {
          Right(properties ++ Email.idProperty)
        }
    }

  private def validateBodyProperties(request: EmailGetRequest): Either[IllegalArgumentException, Properties] =
    request.bodyProperties match {
      case None => Right(EmailBodyPart.defaultProperties)
      case Some(properties) =>
        val invalidProperties = properties -- EmailBodyPart.allowedProperties
        if (invalidProperties.isEmpty()) {
          Right(properties)
        } else {
          Left(new IllegalArgumentException(s"The following bodyProperties [${invalidProperties.format()}] do not exist."))
        }
    }

  private def asEmailGetRequest(arguments: Arguments): SMono[EmailGetRequest] =
    EmailGetSerializer.deserializeEmailGetRequest(arguments.value) match {
      case JsSuccess(emailGetRequest, _) => SMono.just(emailGetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def getEmails(request: EmailGetRequest, properties: Properties, mailboxSession: MailboxSession): SMono[EmailGetResponse] =
    request.ids match {
      case None => SMono.raiseError(new IllegalArgumentException("ids can not be ommited for email/get"))
      case Some(ids) => getEmails(ids, mailboxSession, request)
        .map(result => EmailGetResponse(
          accountId = request.accountId,
          state = INSTANCE,
          list = result.emails.toList,
          notFound = result.notFound))
    }

  private def getEmails(ids: EmailIds, mailboxSession: MailboxSession, request: EmailGetRequest): SMono[EmailGetResults] = {
    val parsedIds: List[Either[(UnparsedEmailId, IllegalArgumentException),  MessageId]] = ids.value
      .map(asMessageId)
    val messagesIds: List[MessageId] = parsedIds.flatMap({
      case Left(_) => None
      case Right(messageId) => Some(messageId)
    })
    val parsingErrors: SFlux[EmailGetResults] = SFlux.fromIterable(parsedIds.flatMap({
      case Left((id, error)) =>
        EmailGetMethod.logger.warn(s"id parsing failed", error)
        Some(EmailGetResults.notFound(id))
      case Right(_) => None
    }))

    SFlux.merge(Seq(retrieveEmails(messagesIds, mailboxSession, request), parsingErrors))
      .reduce(EmailGetResults.empty(), EmailGetResults.merge)
  }

  private def asMessageId(id: UnparsedEmailId): Either[(UnparsedEmailId, IllegalArgumentException),  MessageId] =
    try {
      Right(messageIdFactory.fromString(id))
    } catch {
      case e: Exception => Left((id, new IllegalArgumentException(e)))
    }

  private def retrieveEmails(ids: Seq[MessageId], mailboxSession: MailboxSession, request: EmailGetRequest): SFlux[EmailGetResults] = {
    val foundResultsMono: SMono[Map[MessageId, EmailView]] =
      readerFactory.selectReader(request)
        .read(ids, request, mailboxSession)
        .collectMap(_.metadata.id)

    foundResultsMono.flatMapMany(foundResults => SFlux.fromIterable(ids)
      .map(id => foundResults.get(id)
        .map(EmailGetResults.found)
        .getOrElse(EmailGetResults.notFound(id))))
  }
}