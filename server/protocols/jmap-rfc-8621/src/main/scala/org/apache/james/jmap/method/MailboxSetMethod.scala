/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.json.{MailboxSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.MailboxSetRequest.{MailboxCreationId, UnparsedMailboxId}
import org.apache.james.jmap.mail.{InvalidPatchException, InvalidPropertyException, InvalidUpdateException, IsSubscribed, MailboxCreationRequest, MailboxCreationResponse, MailboxPatchObject, MailboxRights, MailboxSetError, MailboxSetRequest, MailboxSetResponse, MailboxUpdateResponse, NameUpdate, ParentIdUpdate, RemoveEmailsOnDestroy, ServerSetPropertyException, SortOrder, TotalEmails, TotalThreads, UnreadEmails, UnreadThreads, UnsupportedPropertyUpdatedException, ValidatedMailboxPatchObject}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.SetError.SetErrorDescription
import org.apache.james.jmap.model.{Capabilities, ClientId, Id, Invocation, Properties, ServerId, SetError, State}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.jmap.utils.quotas.QuotaLoaderWithPreloadedDefaultFactory
import org.apache.james.mailbox.MailboxManager.RenameOption
import org.apache.james.mailbox.exception.{InsufficientRightsException, MailboxExistsException, MailboxNameException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{FetchGroup, MailboxId, MailboxPath, MessageRange}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager, Role, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, Json, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

case class MailboxHasMailException(mailboxId: MailboxId) extends Exception
case class SystemMailboxChangeException(mailboxId: MailboxId) extends Exception
case class LoopInMailboxGraphException(mailboxId: MailboxId) extends Exception
case class MailboxHasChildException(mailboxId: MailboxId) extends Exception
case class MailboxCreationParseException(setError: SetError) extends Exception

sealed trait CreationResult {
  def mailboxCreationId: MailboxCreationId
}
case class CreationSuccess(mailboxCreationId: MailboxCreationId, mailboxCreationResponse: MailboxCreationResponse) extends CreationResult
case class CreationFailure(mailboxCreationId: MailboxCreationId, exception: Exception) extends CreationResult {
  def asMailboxSetError: SetError = exception match {
    case e: MailboxNotFoundException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("parentId")))
    case e: MailboxExistsException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("name")))
    case e: MailboxNameException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("name")))
    case e: MailboxCreationParseException => e.setError
    case _: InsufficientRightsException => SetError.forbidden(SetErrorDescription("Insufficient rights"), Properties("parentId"))
    case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
  }
}
case class CreationResults(created: Seq[CreationResult]) {
  def retrieveCreated: Map[MailboxCreationId, MailboxCreationResponse] = created
    .flatMap(result => result match {
      case success: CreationSuccess => Some(success.mailboxCreationId, success.mailboxCreationResponse)
      case _ => None
    })
    .toMap
    .map(creation => (creation._1, creation._2))

  def retrieveErrors: Map[MailboxCreationId, SetError] = created
    .flatMap(result => result match {
      case failure: CreationFailure => Some(failure.mailboxCreationId, failure.asMailboxSetError)
      case _ => None
    })
    .toMap
}

sealed trait DeletionResult
case class DeletionSuccess(mailboxId: MailboxId) extends DeletionResult
case class DeletionFailure(mailboxId: UnparsedMailboxId, exception: Throwable) extends DeletionResult {
  def asMailboxSetError: SetError = exception match {
    case e: MailboxNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
    case e: MailboxHasMailException => MailboxSetError.mailboxHasEmail(SetErrorDescription(s"${e.mailboxId.serialize} is not empty"))
    case e: MailboxHasChildException => MailboxSetError.mailboxHasChild(SetErrorDescription(s"${e.mailboxId.serialize} has child mailboxes"))
    case e: SystemMailboxChangeException => SetError.invalidArguments(SetErrorDescription("System mailboxes cannot be destroyed"))
    case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${mailboxId} is not a mailboxId: ${e.getMessage}"))
    case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
  }
}
case class DeletionResults(results: Seq[DeletionResult]) {
  def destroyed: Seq[MailboxId] =
    results.flatMap(result => result match {
      case success: DeletionSuccess => Some(success)
      case _ => None
    }).map(_.mailboxId)

  def retrieveErrors: Map[UnparsedMailboxId, SetError] =
    results.flatMap(result => result match {
      case failure: DeletionFailure => Some(failure.mailboxId, failure.asMailboxSetError)
      case _ => None
    })
      .toMap
}

sealed trait UpdateResult
case class UpdateSuccess(mailboxId: MailboxId) extends UpdateResult
case class UpdateFailure(mailboxId: UnparsedMailboxId, exception: Throwable, patch: Option[ValidatedMailboxPatchObject]) extends UpdateResult {
  def filter(acceptableProperties: Properties): Option[Properties] = Some(patch
    .map(_.updatedProperties.intersect(acceptableProperties))
    .getOrElse(acceptableProperties))

  def asMailboxSetError: SetError = exception match {
    case e: MailboxNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
    case e: MailboxNameException => SetError.invalidArguments(SetErrorDescription(e.getMessage), filter(Properties("name", "parentId")))
    case e: MailboxExistsException => SetError.invalidArguments(SetErrorDescription(e.getMessage), filter(Properties("name", "parentId")))
    case e: UnsupportedPropertyUpdatedException => SetError.invalidArguments(SetErrorDescription(s"${e.property} property do not exist thus cannot be updated"), Some(Properties(e.property)))
    case e: InvalidUpdateException => SetError.invalidArguments(SetErrorDescription(s"${e.cause}"), Some(Properties(e.property)))
    case e: ServerSetPropertyException => SetError.invalidArguments(SetErrorDescription("Can not modify server-set properties"), Some(Properties(e.property)))
    case e: InvalidPropertyException => SetError.invalidPatch(SetErrorDescription(s"${e.cause}"))
    case e: InvalidPatchException => SetError.invalidPatch(SetErrorDescription(s"${e.cause}"))
    case e: SystemMailboxChangeException => SetError.invalidArguments(SetErrorDescription("Invalid change to a system mailbox"), filter(Properties("name", "parentId")))
    case e: LoopInMailboxGraphException => SetError.invalidArguments(SetErrorDescription("A mailbox parentId property can not be set to itself or one of its child"), Some(Properties("parentId")))
    case e: InsufficientRightsException => SetError.invalidArguments(SetErrorDescription("Invalid change to a delegated mailbox"))
    case e: MailboxHasChildException => SetError.invalidArguments(SetErrorDescription(s"${e.mailboxId.serialize()} parentId property cannot be updated as this mailbox has child mailboxes"), Some(Properties("parentId")))
    case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
  }
}
case class UpdateResults(results: Seq[UpdateResult]) {
  def updated: Map[MailboxId, MailboxUpdateResponse] =
    results.flatMap(result => result match {
      case success: UpdateSuccess => Some((success.mailboxId, MailboxSetResponse.empty))
      case _ => None
    }).toMap
  def notUpdated: Map[UnparsedMailboxId, SetError] = results.flatMap(result => result match {
    case failure: UpdateFailure => Some(failure.mailboxId, failure.asMailboxSetError)
    case _ => None
  }).toMap
}

class MailboxSetMethod @Inject()(serializer: MailboxSerializer,
                                 mailboxManager: MailboxManager,
                                 subscriptionManager: SubscriptionManager,
                                 mailboxIdFactory: MailboxId.Factory,
                                 quotaFactory : QuotaLoaderWithPreloadedDefaultFactory,
                                 val metricFactory: MetricFactory,
                                 val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[MailboxSetRequest] {
  override val methodName: MethodName = MethodName("Mailbox/set")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: MailboxSetRequest): SMono[InvocationWithContext] = for {
    creationResultsWithUpdatedProcessingContext <- createMailboxes(mailboxSession, request, invocation.processingContext)
    deletionResults <- deleteMailboxes(mailboxSession, request, invocation.processingContext)
    updateResults <- updateMailboxes(mailboxSession, request, invocation.processingContext, capabilities)
  } yield InvocationWithContext(createResponse(capabilities, invocation.invocation, request, creationResultsWithUpdatedProcessingContext._1, deletionResults, updateResults), creationResultsWithUpdatedProcessingContext._2)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[MailboxSetRequest] = asMailboxSetRequest(invocation.arguments)

  private def updateMailboxes(mailboxSession: MailboxSession,
                              mailboxSetRequest: MailboxSetRequest,
                              processingContext: ProcessingContext,
                              capabilities: Set[CapabilityIdentifier]): SMono[UpdateResults] = {
    SFlux.fromIterable(mailboxSetRequest.update.getOrElse(Seq()))
      .flatMap({
        case (unparsedMailboxId: UnparsedMailboxId, patch: MailboxPatchObject) =>
          processingContext.resolveMailboxId(unparsedMailboxId, mailboxIdFactory).fold(
              e => SMono.just(UpdateFailure(unparsedMailboxId, e, None)),
              mailboxId => updateMailbox(mailboxSession, processingContext, mailboxId, unparsedMailboxId, patch, capabilities))
            .onErrorResume(e => SMono.just(UpdateFailure(unparsedMailboxId, e, None)))
      })
      .collectSeq()
      .map(UpdateResults)
  }

  private def updateMailbox(mailboxSession: MailboxSession,
                            processingContext: ProcessingContext,
                            mailboxId: MailboxId,
                            unparsedMailboxId: UnparsedMailboxId,
                            patch: MailboxPatchObject,
                            capabilities: Set[CapabilityIdentifier]): SMono[UpdateResult] = {
    patch.validate(processingContext, mailboxIdFactory, serializer, capabilities, mailboxSession)
      .fold(e => SMono.raiseError(e), validatedPatch =>
        updateMailboxRights(mailboxId, validatedPatch, mailboxSession)
          .`then`(updateSubscription(mailboxId, validatedPatch, mailboxSession))
          .`then`(updateMailboxPath(mailboxId, unparsedMailboxId, validatedPatch, mailboxSession)))
  }

  private def updateSubscription(mailboxId: MailboxId, validatedPatch: ValidatedMailboxPatchObject, mailboxSession: MailboxSession): SMono[UpdateResult] = {
    validatedPatch.isSubscribedUpdate.map(isSubscribedUpdate => {
      SMono.fromCallable(() => {
        val mailbox = mailboxManager.getMailbox(mailboxId, mailboxSession)
        val isOwner = mailbox.getMailboxPath.belongsTo(mailboxSession)
        val shouldSubscribe = isSubscribedUpdate.isSubscribed.map(_.value).getOrElse(isOwner)

        if (shouldSubscribe) {
          subscriptionManager.subscribe(mailboxSession, mailbox.getMailboxPath.getName)
        } else {
          subscriptionManager.unsubscribe(mailboxSession, mailbox.getMailboxPath.getName)
        }
      }).`then`(SMono.just[UpdateResult](UpdateSuccess(mailboxId)))
        .subscribeOn(Schedulers.elastic())
    })
      .getOrElse(SMono.just[UpdateResult](UpdateSuccess(mailboxId)))
  }

  private def updateMailboxPath(mailboxId: MailboxId,
                                unparsedMailboxId: UnparsedMailboxId,
                                validatedPatch: ValidatedMailboxPatchObject,
                                mailboxSession: MailboxSession): SMono[UpdateResult] = {
    if (validatedPatch.shouldUpdateMailboxPath) {
      SMono.fromCallable[UpdateResult](() => {
        try {
          val mailbox = mailboxManager.getMailbox(mailboxId, mailboxSession)
          if (isASystemMailbox(mailbox)) {
            throw SystemMailboxChangeException(mailboxId)
          }
          if (validatedPatch.parentIdUpdate.flatMap(_.newId).contains(mailboxId)) {
            throw LoopInMailboxGraphException(mailboxId)
          }
          val oldPath = mailbox.getMailboxPath
          val newPath = applyParentIdUpdate(mailboxId, validatedPatch.parentIdUpdate, mailboxSession)
            .andThen(applyNameUpdate(validatedPatch.nameUpdate, mailboxSession))
            .apply(oldPath)
          if (!oldPath.equals(newPath)) {
            mailboxManager.renameMailbox(mailboxId,
              newPath,
              RenameOption.RENAME_SUBSCRIPTIONS,
              mailboxSession)
          }
          UpdateSuccess(mailboxId)
        } catch {
          case e: Exception => UpdateFailure(unparsedMailboxId, e, Some(validatedPatch))
        }
      })
        .subscribeOn(Schedulers.elastic())
    } else {
      SMono.just[UpdateResult](UpdateSuccess(mailboxId))
    }
  }

  private def applyParentIdUpdate(mailboxId: MailboxId, maybeParentIdUpdate: Option[ParentIdUpdate], mailboxSession: MailboxSession): MailboxPath => MailboxPath = {
    maybeParentIdUpdate.map(parentIdUpdate => applyParentIdUpdate(mailboxId, parentIdUpdate, mailboxSession))
      .getOrElse(x => x)
  }

  private def applyNameUpdate(maybeNameUpdate: Option[NameUpdate], mailboxSession: MailboxSession): MailboxPath => MailboxPath = {
    originalPath => maybeNameUpdate.map(nameUpdate => {
      val originalParentPath: Option[MailboxPath] = originalPath.getHierarchyLevels(mailboxSession.getPathDelimiter)
        .asScala
        .reverse
        .drop(1)
        .headOption
      originalParentPath.map(_.child(nameUpdate.newName, mailboxSession.getPathDelimiter))
        .getOrElse(MailboxPath.forUser(mailboxSession.getUser, nameUpdate.newName))
    }).getOrElse(originalPath)
  }

  private def applyParentIdUpdate(mailboxId: MailboxId, parentIdUpdate: ParentIdUpdate, mailboxSession: MailboxSession): MailboxPath => MailboxPath = {
    originalPath => {
      val currentName = originalPath.getName(mailboxSession.getPathDelimiter)
      parentIdUpdate.newId
        .map(id => {
          if (mailboxManager.hasChildren(originalPath, mailboxSession)) {
            throw MailboxHasChildException(mailboxId)
          }
          val parentPath = mailboxManager.getMailbox(id, mailboxSession).getMailboxPath
          parentPath.child(currentName, mailboxSession.getPathDelimiter)
        })
        .getOrElse(MailboxPath.forUser(originalPath.getUser, currentName))
    }
  }

  private def updateMailboxRights(mailboxId: MailboxId,
                                  validatedPatch: ValidatedMailboxPatchObject,
                                  mailboxSession: MailboxSession): SMono[UpdateResult] = {

    val resetOperation: SMono[Unit] = validatedPatch.rightsReset.map(sharedWithResetUpdate => {
      SMono.fromCallable(() => {
        mailboxManager.setRights(mailboxId, sharedWithResetUpdate.rights.toMailboxAcl.asJava, mailboxSession)
      }).`then`()
    }).getOrElse(SMono.empty)

    val partialUpdatesOperation: SMono[Unit] = SFlux.fromIterable(validatedPatch.rightsPartialUpdates)
      .flatMap(partialUpdate => SMono.fromCallable(() => {
        mailboxManager.applyRightsCommand(mailboxId, partialUpdate.asACLCommand(), mailboxSession)
      }))
      .`then`()

    SFlux.merge(Seq(resetOperation, partialUpdatesOperation))
      .`then`()
      .`then`(SMono.just[UpdateResult](UpdateSuccess(mailboxId)))
      .subscribeOn(Schedulers.elastic())

  }


  private def deleteMailboxes(mailboxSession: MailboxSession, mailboxSetRequest: MailboxSetRequest, processingContext: ProcessingContext): SMono[DeletionResults] = {
    SFlux.fromIterable(mailboxSetRequest.destroy.getOrElse(Seq()))
      .flatMap(id => delete(mailboxSession, processingContext, id, mailboxSetRequest.onDestroyRemoveEmails.getOrElse(RemoveEmailsOnDestroy(false)))
        .onErrorRecover(e => DeletionFailure(id, e)))
      .collectSeq()
      .map(DeletionResults)
  }

  private def delete(mailboxSession: MailboxSession, processingContext: ProcessingContext, id: UnparsedMailboxId, onDestroy: RemoveEmailsOnDestroy): SMono[DeletionResult] = {
    processingContext.resolveMailboxId(id, mailboxIdFactory) match {
      case Right(mailboxId) => SMono.fromCallable(() => delete(mailboxSession, mailboxId, onDestroy))
        .subscribeOn(Schedulers.elastic())
        .`then`(SMono.just[DeletionResult](DeletionSuccess(mailboxId)))
      case Left(e) => SMono.raiseError(e)
    }
  }

  private def delete(mailboxSession: MailboxSession, id: MailboxId, onDestroy: RemoveEmailsOnDestroy): Unit = {
    val mailbox = mailboxManager.getMailbox(id, mailboxSession)

    if (isASystemMailbox(mailbox)) {
      throw SystemMailboxChangeException(id)
    }

    if (mailboxManager.hasChildren(mailbox.getMailboxPath, mailboxSession)) {
      throw MailboxHasChildException(id)
    }

    if (onDestroy.value) {
      val deletedMailbox = mailboxManager.deleteMailbox(id, mailboxSession)
      subscriptionManager.unsubscribe(mailboxSession, deletedMailbox.getName)
    } else {
      if (mailbox.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).hasNext) {
        throw MailboxHasMailException(id)
      }

      val deletedMailbox = mailboxManager.deleteMailbox(id, mailboxSession)
      subscriptionManager.unsubscribe(mailboxSession, deletedMailbox.getName)
    }
  }

  private def isASystemMailbox(mailbox: MessageManager): Boolean = Role.from(mailbox.getMailboxPath.getName).isPresent

  private def createMailboxes(mailboxSession: MailboxSession,
                              mailboxSetRequest: MailboxSetRequest,
                              processingContext: ProcessingContext): SMono[(CreationResults, ProcessingContext)] = {
    SFlux.fromIterable(mailboxSetRequest.create
      .getOrElse(Map.empty)
      .view)
      .foldLeft((CreationResults(Nil), processingContext)){
         (acc : (CreationResults, ProcessingContext), elem: (MailboxCreationId, JsObject)) => {
           val (mailboxCreationId, jsObject) = elem
           val (creationResult, updatedProcessingContext) = createMailbox(mailboxSession, mailboxCreationId, jsObject, acc._2)
           (CreationResults(acc._1.created :+ creationResult), updatedProcessingContext)
          }
      }
      .subscribeOn(Schedulers.elastic())
  }

  private def createMailbox(mailboxSession: MailboxSession,
                            mailboxCreationId: MailboxCreationId,
                            jsObject: JsObject,
                            processingContext: ProcessingContext): (CreationResult, ProcessingContext) = {
    parseCreate(jsObject)
      .flatMap(mailboxCreationRequest => resolvePath(mailboxSession, mailboxCreationRequest, processingContext)
        .flatMap(path => createMailbox(mailboxSession = mailboxSession,
          path = path,
          mailboxCreationRequest = mailboxCreationRequest)))
      .flatMap(creationResponse => recordCreationIdInProcessingContext(mailboxCreationId, processingContext, creationResponse.id)
        .map(context => (creationResponse, context)))
      .fold(e => (CreationFailure(mailboxCreationId, e), processingContext),
        creationResponseWithUpdatedContext => {
          (CreationSuccess(mailboxCreationId, creationResponseWithUpdatedContext._1), creationResponseWithUpdatedContext._2)
        })
  }

  private def parseCreate(jsObject: JsObject): Either[MailboxCreationParseException, MailboxCreationRequest] =
    MailboxCreationRequest.validateProperties(jsObject)
      .flatMap(validJsObject => Json.fromJson(validJsObject)(serializer.mailboxCreationRequest) match {
        case JsSuccess(creationRequest, _) => Right(creationRequest)
        case JsError(errors) => Left(MailboxCreationParseException(mailboxSetError(errors)))
      })

  private def mailboxSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in mailbox object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in mailbox object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in mailbox object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }

  private def createMailbox(mailboxSession: MailboxSession,
                            path: MailboxPath,
                            mailboxCreationRequest: MailboxCreationRequest): Either[Exception, MailboxCreationResponse] = {
    try {
      //can safely do a get as the Optional is empty only if the mailbox name is empty which is forbidden by the type constraint on MailboxName
      val mailboxId = mailboxManager.createMailbox(path, mailboxSession).get()

      val defaultSubscribed = IsSubscribed(true)
      if (mailboxCreationRequest.isSubscribed.getOrElse(defaultSubscribed).value) {
        subscriptionManager.subscribe(mailboxSession, path.getName)
      }

      mailboxCreationRequest.rights
        .foreach(rights => mailboxManager.setRights(mailboxId, rights.toMailboxAcl.asJava, mailboxSession))

      val quotas = quotaFactory.loadFor(mailboxSession)
        .flatMap(quotaLoader => quotaLoader.getQuotas(path))
        .block()

      Right(MailboxCreationResponse(
        id = mailboxId,
        sortOrder = SortOrder.defaultSortOrder,
        role = None,
        totalEmails = TotalEmails(0L),
        unreadEmails = UnreadEmails(0L),
        totalThreads = TotalThreads(0L),
        unreadThreads = UnreadThreads(0L),
        myRights = MailboxRights.FULL,
        quotas = Some(quotas),
        isSubscribed =  if (mailboxCreationRequest.isSubscribed.isEmpty) {
          Some(defaultSubscribed)
        } else {
          None
        }))
    } catch {
      case error: Exception => Left(error)
    }
  }

  private def recordCreationIdInProcessingContext(mailboxCreationId: MailboxCreationId,
                                                  processingContext: ProcessingContext,
                                                  mailboxId: MailboxId): Either[IllegalArgumentException, ProcessingContext] = {
    for {
      creationId <- Id.validate(mailboxCreationId)
      serverAssignedId <- Id.validate(mailboxId.serialize())
    } yield {
      processingContext.recordCreatedId(ClientId(creationId), ServerId(serverAssignedId))
    }
  }

  private def resolvePath(mailboxSession: MailboxSession,
                          mailboxCreationRequest: MailboxCreationRequest,
                          processingContext: ProcessingContext): Either[Exception, MailboxPath] = {
    if (mailboxCreationRequest.name.value.contains(mailboxSession.getPathDelimiter)) {
      return Left(new MailboxNameException(s"The mailbox '${mailboxCreationRequest.name.value}' contains an illegal character: '${mailboxSession.getPathDelimiter}'"))
    }
    mailboxCreationRequest.parentId
      .map(maybeParentId => for {
        parentId <- processingContext.resolveMailboxId(maybeParentId, mailboxIdFactory)
        parentPath <- retrievePath(parentId, mailboxSession)
      } yield {
        parentPath.child(mailboxCreationRequest.name, mailboxSession.getPathDelimiter)
      })
      .getOrElse(Right(MailboxPath.forUser(mailboxSession.getUser, mailboxCreationRequest.name)))
  }

  private def retrievePath(mailboxId: MailboxId, mailboxSession: MailboxSession): Either[Exception, MailboxPath] = try {
    Right(mailboxManager.getMailbox(mailboxId, mailboxSession).getMailboxPath)
  } catch {
    case e: Exception => Left(e)
  }

  private def createResponse(capabilities: Set[CapabilityIdentifier],
                             invocation: Invocation,
                             mailboxSetRequest: MailboxSetRequest,
                             creationResults: CreationResults,
                             deletionResults: DeletionResults,
                             updateResults: UpdateResults): Invocation = {
    val response = MailboxSetResponse(
      mailboxSetRequest.accountId,
      oldState = None,
      newState = State.INSTANCE,
      destroyed = Some(deletionResults.destroyed).filter(_.nonEmpty),
      created = Some(creationResults.retrieveCreated).filter(_.nonEmpty),
      notCreated = Some(creationResults.retrieveErrors).filter(_.nonEmpty),
      updated = Some(updateResults.updated).filter(_.nonEmpty),
      notUpdated = Some(updateResults.notUpdated).filter(_.nonEmpty),
      notDestroyed = Some(deletionResults.retrieveErrors).filter(_.nonEmpty))

    Invocation(methodName,
      Arguments(serializer.serialize(response, capabilities).as[JsObject]),
      invocation.methodCallId)
  }

  private def asMailboxSetRequest(arguments: Arguments): SMono[MailboxSetRequest] = {
    serializer.deserializeMailboxSetRequest(arguments.value) match {
      case JsSuccess(mailboxSetRequest, _) => SMono.just(mailboxSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
  }
}
