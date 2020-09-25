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
import org.apache.james.jmap.mail.Email.Size
import org.apache.james.jmap.mail.IsAscending.{ASCENDING, DESCENDING}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.jmap.model.Limit.Limit
import org.apache.james.jmap.model.Position.Position
import org.apache.james.jmap.model.{AccountId, CanCalculateChanges, Keyword, LimitUnparsed, PositionUnparsed, QueryState, UTCDate}
import org.apache.james.mailbox.model.SearchQuery.Sort.Order.{NATURAL, REVERSE}
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause
import org.apache.james.mailbox.model.{MailboxId, MessageId, SearchQuery}

case class UnsupportedSortException(unsupportedSort: String) extends UnsupportedOperationException
case class UnsupportedFilterException(unsupportedFilter: String) extends UnsupportedOperationException
case class UnsupportedRequestParameterException(unsupportedParam: String) extends UnsupportedOperationException

case class Text(value: String) extends AnyVal
case class From(value: String) extends AnyVal
case class To(value: String) extends AnyVal
case class Cc(value: String) extends AnyVal
case class Bcc(value: String) extends AnyVal
case class Body(value: String) extends AnyVal
case class Header(value: String) extends AnyVal

case class FilterCondition(inMailbox: Option[MailboxId],
                           inMailboxOtherThan: Option[Seq[MailboxId]],
                           before: Option[UTCDate],
                           after: Option[UTCDate],
                           hasKeyword: Option[Keyword],
                           notKeyword: Option[Keyword],
                           minSize: Option[Size],
                           maxSize: Option[Size],
                           hasAttachment: Option[HasAttachment],
                           allInThreadHaveKeyword: Option[Keyword],
                           someInThreadHaveKeyword: Option[Keyword],
                           noneInThreadHaveKeyword: Option[Keyword],
                           text: Option[Text],
                           from: Option[From],
                           to: Option[To],
                           cc: Option[Cc],
                           bcc: Option[Bcc],
                           subject: Option[Subject],
                           header: Option[Set[Header]],
                           body: Option[Body])

case class EmailQueryRequest(accountId: AccountId,
                             position: Option[PositionUnparsed],
                             limit: Option[LimitUnparsed],
                             filter: Option[FilterCondition],
                             comparator: Option[Set[Comparator]],
                             collapseThreads: Option[CollapseThreads],
                             anchor: Option[Anchor],
                             anchorOffset: Option[AnchorOffset]) extends WithAccountId

sealed trait SortProperty {
  def toSortClause: Either[UnsupportedSortException, SortClause]
}
case object ReceivedAtSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = scala.Right(SortClause.Arrival)
}

case object AllInThreadHaveKeywordSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("allInThreadHaveKeyword"))
}
case object SomeInThreadHaveKeywordSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("someInThreadHaveKeyword"))
}
case object SizeSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("size"))
}
case object FromSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("from"))
}
case object ToSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("to"))
}
case object SubjectSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("subject"))
}
case object HasKeywordSortProperty extends SortProperty {
  override def toSortClause: Either[UnsupportedSortException, SortClause] = Left(UnsupportedSortException("hasKeyword"))
}

object IsAscending {
  val DESCENDING: IsAscending = IsAscending(false)
  val ASCENDING: IsAscending = IsAscending(true)
}
case class IsAscending(sortByASC: Boolean) extends AnyVal {
  def toSortOrder: SearchQuery.Sort.Order = if (sortByASC) {
    NATURAL
  } else {
    REVERSE
  }
}

object Comparator {
  val default: Comparator = Comparator(ReceivedAtSortProperty, Some(DESCENDING), None)
}

case class Collation(value: String) extends AnyVal

case class Comparator(property: SortProperty,
                      isAscending: Option[IsAscending],
                      collation: Option[Collation]) {
  def toSort: Either[UnsupportedSortException, SearchQuery.Sort] =
    for {
      sortClause <- property.toSortClause
    } yield new SearchQuery.Sort(sortClause, isAscending.getOrElse(ASCENDING).toSortOrder)
}

case class CollapseThreads(value: Boolean) extends AnyVal
case class Anchor(value: String) extends AnyVal
case class AnchorOffset(value: Int) extends AnyVal

case class EmailQueryResponse(accountId: AccountId,
                              queryState: QueryState,
                              canCalculateChanges: CanCalculateChanges,
                              ids: Seq[MessageId],
                              position: Position,
                              limit: Option[Limit])
