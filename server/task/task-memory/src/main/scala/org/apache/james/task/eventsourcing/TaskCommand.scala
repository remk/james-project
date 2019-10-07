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
package org.apache.james.task.eventsourcing

import org.apache.james.eventsourcing.Command
import org.apache.james.task.Task.Result
import org.apache.james.task.TaskExecutionDetails.AdditionalInformation
import org.apache.james.task.{Task, TaskId, TaskType}

sealed trait TaskCommand extends Command

object TaskCommand {

  case class Create(id: TaskId, task: Task) extends TaskCommand

  case class Start(id: TaskId) extends TaskCommand

  case class UpdateAdditionalInformation(id: TaskId, additionalInformation: AdditionalInformation) extends TaskCommand

  case class Complete(id: TaskId, result: Result, additionalInformation: Option[AdditionalInformation]) extends TaskCommand

  case class RequestCancel(id: TaskId) extends TaskCommand

  case class Fail(id: TaskId, additionalInformation: Option[AdditionalInformation], errorMessage: Option[String], exception: Option[String]) extends TaskCommand

  case class Cancel(id: TaskId, additionalInformation: Option[AdditionalInformation]) extends TaskCommand

}
