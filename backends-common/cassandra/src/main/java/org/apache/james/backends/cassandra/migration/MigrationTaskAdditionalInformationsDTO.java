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
package org.apache.james.backends.cassandra.migration;

import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MigrationTaskAdditionalInformationsDTO implements AdditionalInformationDTO {

    static final AdditionalInformationDTOModule<MigrationTask.AdditionalInformations, MigrationTaskAdditionalInformationsDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(MigrationTask.AdditionalInformations.class)
            .convertToDTO(MigrationTaskAdditionalInformationsDTO.class)
            .toDomainObjectConverter(dto -> new MigrationTask.AdditionalInformations(new SchemaVersion(dto.getTargetVersion())))
            .toDTOConverter((details, type) -> new MigrationTaskAdditionalInformationsDTO(details.getToVersion()))
            .typeName(MigrationTask.CASSANDRA_MIGRATION.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final int targetVersion;

    public MigrationTaskAdditionalInformationsDTO(@JsonProperty("targetVersion") int targetVersion) {
        this.targetVersion = targetVersion;
    }

    public int getTargetVersion() {
        return targetVersion;
    }
}
