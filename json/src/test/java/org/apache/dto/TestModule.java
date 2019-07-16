package org.apache.dto;

import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;

public class TestModule<T extends BaseType, U extends DTO> extends DTOModule<T, U> {

    protected TestModule(DTOConverter<T, U> converter, DomainObjectConverter<T, U> toDomainObjectConverter, Class<T> domainObjectType, Class<U> dtoType, String typeName) {
        super(converter, toDomainObjectConverter, domainObjectType, dtoType, typeName);
    }
}
