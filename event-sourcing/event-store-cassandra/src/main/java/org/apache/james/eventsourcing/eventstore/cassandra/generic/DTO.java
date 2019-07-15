package org.apache.james.eventsourcing.eventstore.cassandra.generic;

public interface DTO<T> {
    T toDomainObject();
}
