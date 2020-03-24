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

import eu.timepit.refined.api.RefType
import eu.timepit.refined.auto._
import org.scalatest.{Matchers, WordSpec}

class IdTest extends WordSpec with Matchers {

  private val INVALID_CHARACTERS = List("\"", "(", ")", ",", ":", ";", "<", ">", "@", "[", "\\", "]", " ")

  /**
   * From Refined documentation:
   *
   * Macros can only validate literals because their values are known at
   * compile-time. To validate arbitrary (runtime) values we can use the
   * RefType.applyRef function
   */
  "apply" when {
    "in Runtime" should {

      "return left(error message) when empty value" in {
        val maybeId: Option[String] = RefType.applyRef[Id.Id]("") match {
          case Left(errorMessage) => Option(errorMessage)
          case Right(_) => Option.empty
        }

        maybeId.get should equal("Predicate failed: value does not meet Id requirements.")
      }

      "return left(error message) when too long value" in {
        val maybeId: Option[String] = RefType.applyRef[Id.Id]("a" * 256) match {
          case Left(errorMessage) => Option(errorMessage)
          case Right(_) => Option.empty
        }

        maybeId.get should equal("Predicate failed: value does not meet Id requirements.")
      }

      "return left(error message) when containing invalid characters" in {
        INVALID_CHARACTERS.foreach { invalidChar =>
          val maybeId: Option[String] = RefType.applyRef[Id.Id](invalidChar) match {
            case Left(errorMessage) => Option(errorMessage)
            case Right(_) => Option.empty
          }

          maybeId.get should equal("Predicate failed: value does not meet Id requirements.")
        }
      }

      "return right when valid value" in {
        val myId : Id.Id = "myId"

        val maybeId: Option[Id.Id] = RefType.applyRef[Id.Id]("myId") match {
          case Left(_) => Option.empty
          case Right(validValue) => Option(validValue)
        }

        maybeId.get should equal(myId)
      }
    }
  }
}