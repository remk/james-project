/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.webadmin.vault.routes;

import org.apache.james.core.User;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletedMessagesVaultRestoreTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    private static DeletedMessagesVaultRestoreTaskAdditionalInformationDTO fromDomainObject(DeletedMessagesVaultRestoreTask.AdditionalInformation additionalInformation, String type) {
        return new DeletedMessagesVaultRestoreTaskAdditionalInformationDTO(
            type,
            additionalInformation.getUser(),
            additionalInformation.getSuccessfulRestoreCount(),
            additionalInformation.getErrorRestoreCount()
        );
    }

    public static final AdditionalInformationDTOModule<DeletedMessagesVaultRestoreTask.AdditionalInformation, DeletedMessagesVaultRestoreTaskAdditionalInformationDTO> MODULE =
        DTOModule
            .forDomainObject(DeletedMessagesVaultRestoreTask.AdditionalInformation.class)
            .convertToDTO(DeletedMessagesVaultRestoreTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(DeletedMessagesVaultRestoreTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(DeletedMessagesVaultRestoreTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(DeletedMessagesVaultRestoreTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final String user;
    private final Long successfulRestoreCount;
    private final Long errorRestoreCount;

    public DeletedMessagesVaultRestoreTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                   @JsonProperty("user") String user,
                                                                   @JsonProperty("successfulRestoreCount") Long successfulRestoreCount,
                                                                   @JsonProperty("errorRestoreCount") Long errorRestoreCount) {
        this.type = type;
        this.user = user;
        this.successfulRestoreCount = successfulRestoreCount;
        this.errorRestoreCount = errorRestoreCount;
    }

    public String getUser() {
        return user;
    }

    public Long getSuccessfulRestoreCount() {
        return successfulRestoreCount;
    }

    public Long getErrorRestoreCount() {
        return errorRestoreCount;
    }

    DeletedMessagesVaultRestoreTask.AdditionalInformation toDomainObject() {
        return new DeletedMessagesVaultRestoreTask.AdditionalInformation(
            User.fromUsername(user),
            successfulRestoreCount,
            errorRestoreCount
        );
    }

    @Override
    public String getType() {
        return type;
    }
}
