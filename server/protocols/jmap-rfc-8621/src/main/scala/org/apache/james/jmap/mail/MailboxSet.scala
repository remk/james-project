package org.apache.james.jmap.mail

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.mail.MailboxName.MailboxName
import org.apache.james.jmap.mail.MailboxSetRequest.MailboxCreationId
import org.apache.james.jmap.model.AccountId
import org.apache.james.jmap.model.State.State
import org.apache.james.mailbox.Role
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json.JsObject

case class MailboxSetRequest(accountId: AccountId,
                             ifInState: Option[State],
                             create: Option[Map[MailboxCreationId, JsObject]],
                             update: Option[Map[MailboxId, MailboxPatchObject]],
                             destroy: Option[Seq[MailboxId]],
                             onDestroyRemoveEmails: Option[RemoveEmailsOnDestroy]
                            )


object MailboxSetRequest {
  type MailboxCreationId = String Refined NonEmpty
}

case class RemoveEmailsOnDestroy(value: Boolean) extends AnyVal
//TODO the parentId could be a backreference to a MailboxCreationId
//TODO should we handle server-set parameters which should be allowed in request and that we should match to be the same than the response?
case class MailboxCreationRequest(name: MailboxName, parentId: Option[MailboxId])


//TODO strongly type when implementing update
case class MailboxPatchObject(value: Map[String, JsObject])

case class MailboxSetResponse(accountId: AccountId,
                              oldState: Option[State],
                              newState: State,
                              created: Option[Map[MailboxCreationId, MailboxCreationResponse]],
                              updated: Option[Map[MailboxId, MailboxUpdateResponse]],//TODO could be a backreference to a MailboxCreationId
                              destroyed: Option[Seq[MailboxId]],//TODO could be a backreference to a MailboxCreationId,
                              notCreated: Option[Map[MailboxCreationId, MailboxSetError]],
                              notUpdated: Option[Map[MailboxId, MailboxSetError]],
                              notDestroyed: Option[Map[MailboxId, MailboxSetError]]
                             )

case class MailboxSetError(`type`: SetErrorType, description: Option[SetErrorDescription], properties: Option[Properties])

case class MailboxCreationResponse(id: MailboxId,
                                   role: Option[Role],//TODO see if we need to return this, if a role is set by the server during creation
                                   totalEmails: TotalEmails,
                                   unreadEmails: UnreadEmails,
                                   totalThreads: TotalThreads,
                                   unreadThreads: UnreadThreads,
                                   myRights: MailboxRights,
                                   rights: Option[Rights],//TODO display only if RightsExtension and if some rights are set by the server during creation
                                   namespace: Option[MailboxNamespace], //TODO display only if RightsExtension
                                   quotas: Option[Quotas],//TODO display only if QuotasExtension
                                   isSubscribed: IsSubscribed
                                  )

//TODO strongly type when implementing update
case class MailboxUpdateResponse(value: JsObject)