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

package org.apache;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.apache.dto.BaseType;
import org.apache.dto.FirstDomainObject;
import org.apache.dto.SecondDomainObject;
import org.apache.dto.TestModules;
import org.apache.james.json.DTO;
import org.apache.james.json.JsonGenericSerializer;
import org.junit.jupiter.api.Test;

class JsonGenericSerializerTest {
    FirstDomainObject FIRST = new FirstDomainObject(Optional.of(1L), ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]"), "first payload");
    SecondDomainObject SECOND = new SecondDomainObject(UUID.fromString("4a2c853f-7ffc-4ce3-9410-a47e85b3b741"), "second payload");

    String MISSING_TYPE_JSON = "{\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    String DUPLICATE_TYPE_JSON = "{\"type\":\"first\", \"type\":\"second\", \"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    String FIRST_JSON = "{\"type\":\"first\",\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    String SECOND_JSON = "{\"type\":\"second\",\"id\":\"4a2c853f-7ffc-4ce3-9410-a47e85b3b741\",\"payload\":\"second payload\"}";

    @Test
    void shouldDeserializeKnownType() throws Exception {
        assertThat(JsonGenericSerializer.of(TestModules.FIRST_TYPE)
            .deserialize(FIRST_JSON))
            .isEqualTo(FIRST);
    }

    @Test
    void shouldThrowWhenDeserializeEventWithMissingType() {
        assertThatThrownBy(() -> JsonGenericSerializer.of(TestModules.FIRST_TYPE)
            .deserialize(MISSING_TYPE_JSON))
            .isInstanceOf(JsonGenericSerializer.InvalidTypeException.class);
    }

    @Test
    void shouldThrowWhenDeserializeEventWithDuplicateType() {
        assertThatThrownBy(() -> JsonGenericSerializer.of(
                TestModules.FIRST_TYPE,
                TestModules.SECOND_TYPE)
            .deserialize(DUPLICATE_TYPE_JSON))
            .isInstanceOf(JsonGenericSerializer.InvalidTypeException.class);
    }

    @Test
    void shouldThrowWhenDeserializeUnknownEvent() {
        assertThatThrownBy(() -> JsonGenericSerializer.of()
            .deserialize(FIRST_JSON))
            .isInstanceOf(JsonGenericSerializer.UnknownTypeException.class);
    }

    @Test
    void serializeShouldHandleAllKnownEvents() throws Exception {
        JsonGenericSerializer<BaseType, DTO> serializer = JsonGenericSerializer.of(
                TestModules.FIRST_TYPE,
                TestModules.SECOND_TYPE);

        assertThatJson(
                serializer.serialize(SECOND))
            .isEqualTo(SECOND_JSON);

        assertThatJson(
                serializer.serialize(FIRST))
            .isEqualTo(FIRST_JSON);
    }

    @Test
    void deserializeShouldHandleAllKnownEvents() throws Exception {
        JsonGenericSerializer serializer = JsonGenericSerializer.of(
                TestModules.FIRST_TYPE,
                TestModules.SECOND_TYPE);

        assertThatJson(
                serializer.deserialize(SECOND_JSON))
            .isEqualTo(SECOND);

        assertThatJson(
                serializer.deserialize(FIRST_JSON))
            .isEqualTo(FIRST);
    }

    @Test
    void shouldSerializeKnownEvent() throws Exception {
        assertThatJson(JsonGenericSerializer.of(TestModules.FIRST_TYPE)
            .serialize(FIRST))
            .isEqualTo(FIRST_JSON);
    }

    @Test
    void shouldThrowWhenSerializeUnknownEvent() {
        assertThatThrownBy(() -> JsonGenericSerializer.of()
            .serialize(FIRST))
            .isInstanceOf(JsonGenericSerializer.UnknownTypeException.class);
    }

}