/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.task.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Duration.ONE_MINUTE;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.task.CompletedTask;
import org.apache.james.task.Hostname;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.assertj.core.api.ListAssert;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import scala.Option;

public interface TerminationSubscriberContract {

    Completed COMPLETED_EVENT = new Completed(new TaskAggregateId(TaskId.generateTaskId()), EventId.fromSerialized(42), Task.Result.COMPLETED, Option.empty());
    Failed FAILED_EVENT = new Failed(new TaskAggregateId(TaskId.generateTaskId()), EventId.fromSerialized(42), Option.empty(), Option.empty(), Option.empty());
    Cancelled CANCELLED_EVENT = new Cancelled(new TaskAggregateId(TaskId.generateTaskId()), EventId.fromSerialized(42), Option.empty());
    Duration DELAY_BETWEEN_EVENTS = Duration.ofMillis(50);
    Duration DELAY_BEFORE_PUBLISHING = Duration.ofMillis(50);

    TerminationSubscriber subscriber();

    @Test
    default void handlingCompletedShouldBeListed() {
        TerminationSubscriber subscriber = subscriber();

        sendEvents(subscriber, COMPLETED_EVENT);

        assertEvents(subscriber).containsOnly(COMPLETED_EVENT);
    }

    @Test
    default void handlingFailedShouldBeListed() {
        TerminationSubscriber subscriber = subscriber();

        sendEvents(subscriber, FAILED_EVENT);

        assertEvents(subscriber).containsOnly(FAILED_EVENT);
    }

    @Test
    default void handlingCancelledShouldBeListed() {
        TerminationSubscriber subscriber = subscriber();

        sendEvents(subscriber, CANCELLED_EVENT);

        assertEvents(subscriber).containsOnly(CANCELLED_EVENT);
    }

    @Test
    default void handlingNonTerminalEventShouldNotBeListed() {
        TerminationSubscriber subscriber = subscriber();
        TaskEvent event = new Started(new TaskAggregateId(TaskId.generateTaskId()), EventId.fromSerialized(42), new Hostname("foo"));

        sendEvents(subscriber, event);

        assertEvents(subscriber).isEmpty();
    }

    @Test
    default void handlingMultipleEventsShouldBeListed() {
        TerminationSubscriber subscriber = subscriber();

        sendEvents(subscriber, COMPLETED_EVENT, FAILED_EVENT, CANCELLED_EVENT);

        assertEvents(subscriber).containsExactly(COMPLETED_EVENT, FAILED_EVENT, CANCELLED_EVENT);
    }

    @Test
    default void multipleListeningEventsShouldShareEvents() {
        TerminationSubscriber subscriber = subscriber();

        Flux<Event> firstListener = Flux.from(subscriber.listenEvents());
        Flux<Event> secondListener = Flux.from(subscriber.listenEvents());

        sendEvents(subscriber, COMPLETED_EVENT, FAILED_EVENT, CANCELLED_EVENT);

        List<Event> receivedEventsFirst = new ArrayList<>();
        firstListener.subscribe(receivedEventsFirst::add);
        List<Event> receivedEventsSecond = new ArrayList<>();
        secondListener.subscribe(receivedEventsSecond::add);

        Awaitility.await().atMost(ONE_MINUTE).until(() -> receivedEventsFirst.size() == 3 && receivedEventsSecond.size() == 3);

        assertThat(receivedEventsFirst).containsExactly(COMPLETED_EVENT, FAILED_EVENT, CANCELLED_EVENT);
        assertThat(receivedEventsSecond).containsExactly(COMPLETED_EVENT, FAILED_EVENT, CANCELLED_EVENT);
    }

    @Test
    default void dynamicListeningEventsShouldGetOnlyNewEvents() {
        TerminationSubscriber subscriber = subscriber();

        sendEvents(subscriber, COMPLETED_EVENT, FAILED_EVENT, CANCELLED_EVENT);

        List<Event> listenedEvents = Mono.delay(DELAY_BEFORE_PUBLISHING.plus(DELAY_BETWEEN_EVENTS.multipliedBy(3).dividedBy(2)))
            .then(Mono.defer(() -> collectEvents(subscriber.listenEvents())))
            .subscribeOn(Schedulers.boundedElastic())
            .block();
        assertThat(listenedEvents).containsExactly(FAILED_EVENT, CANCELLED_EVENT);
    }

    default ListAssert<Event> assertEvents(TerminationSubscriber subscriber) {
        return assertThat(collectEvents(subscriber.listenEvents())
            .block());
    }

    default Mono<List<Event>> collectEvents(Publisher<Event> listener) {
        return Flux.from(listener)
            .subscribeOn(Schedulers.boundedElastic())
            .take(DELAY_BEFORE_PUBLISHING.plus(DELAY_BETWEEN_EVENTS.multipliedBy(7)))
            .collectList();
    }

    default void sendEvents(TerminationSubscriber subscriber, Event... events) {
        Mono.delay(DELAY_BEFORE_PUBLISHING)
            .flatMapMany(ignored -> Flux.fromArray(events)
                .subscribeOn(Schedulers.boundedElastic())
                .delayElements(DELAY_BETWEEN_EVENTS)
                .doOnNext(subscriber::handle))
            .subscribe();
    }
}