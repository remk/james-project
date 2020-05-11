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
package org.apache.james.mailbox.spamassassin;

import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.event.SpamEventListener;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.spamassassin.MessageToLearn;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.streams.Iterators;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SpamAssassinListener implements SpamEventListener {
    public static class SpamAssassinListenerGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SpamAssassinListener.class);
    private static final int LIMIT = 1;
    private static final Group GROUP = new SpamAssassinListenerGroup();

    private final SpamAssassin spamAssassin;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final ExecutionMode executionMode;

    @Inject
    public SpamAssassinListener(SpamAssassin spamAssassin, SystemMailboxesProvider systemMailboxesProvider, MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory, ExecutionMode executionMode) {
        this.spamAssassin = spamAssassin;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.executionMode = executionMode;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MessageMoveEvent || event instanceof Added;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        Username username = Username.of(getClass().getCanonicalName());
        if (event instanceof MessageMoveEvent) {
            MailboxSession session = mailboxManager.createSystemSession(username);
            return handleMessageMove(event, session, (MessageMoveEvent) event);
        }
        if (event instanceof Added) {
            MailboxSession session = mailboxManager.createSystemSession(username);
            return handleAdded(event, session, (Added) event);
        }
        return Mono.empty();
    }

    private Mono<Void> handleAdded(Event event, MailboxSession session, Added addedEvent) {
        if (isAppendedToInbox(addedEvent)) {
            MessageMapper messageMapper = mapperFactory.getMessageMapper(session);

           return mapperFactory.getMailboxMapper(session)
                .findMailboxByIdReactive(addedEvent.getMailboxId())
                .flatMap(mailbox -> retrieveContents(addedEvent, mailbox, messageMapper))
                .flatMap(contents -> spamAssassin.learnHam(contents, event.getUsername()));
        }
        return Mono.empty();
    }

    private Mono<ImmutableList<MessageToLearn>> retrieveContents(Added addedEvent, Mailbox mailbox, MessageMapper messageMapper) {
        return Mono.fromSupplier(() -> MessageRange.toRanges(addedEvent.getUids())
                .stream()
                .flatMap(range -> retrieveMessages(messageMapper, mailbox, range))
                .map((ThrowingFunction<MailboxMessage, MessageToLearn>) message ->
                    new MessageToLearn(message.getFullContent(), message.getFullContentOctets()))
                .collect(Guavate.toImmutableList()))
            .subscribeOn(Schedulers.elastic());
    }

    private Mono<Void> handleMessageMove(Event event, MailboxSession session, MessageMoveEvent messageMoveEvent) {
        return Flux.merge(
            isMessageMovedToSpamMailbox(messageMoveEvent)
                .filter(FunctionalUtils.identityPredicate())
                .flatMap(any -> {
                    LOGGER.debug("Spam event detected");
                    return retrieveMessages(messageMoveEvent, session)
                        .flatMap(messages ->
                            spamAssassin.learnSpam(messages, event.getUsername()));
                }),
            isMessageMovedOutOfSpamMailbox(messageMoveEvent)
                .filter(FunctionalUtils.identityPredicate())
                .flatMap(any ->
                    retrieveMessages(messageMoveEvent, session)
                        .flatMap(messages ->
                            spamAssassin.learnHam(messages, event.getUsername()))))
            .then();
    }

    private Stream<MailboxMessage> retrieveMessages(MessageMapper messageMapper, Mailbox mailbox, MessageRange range) {
        try {
            return Iterators.toStream(messageMapper.findInMailbox(mailbox, range, MessageMapper.FetchType.Full, LIMIT));
        } catch (MailboxException e) {
            LOGGER.warn("Can not retrieve message {} {}", mailbox.getMailboxId(), range.toString(), e);
            return Stream.empty();
        }
    }

    private boolean isAppendedToInbox(Added addedEvent) {
        try {
            return systemMailboxesProvider.findMailbox(Role.INBOX, addedEvent.getUsername())
                .getId().equals(addedEvent.getMailboxId());
        } catch (MailboxException e) {
            LOGGER.warn("Could not resolve Inbox mailbox", e);
            return false;
        }
    }

    private Mono<ImmutableList<MessageToLearn>> retrieveMessages(MessageMoveEvent messageMoveEvent, MailboxSession session) {
        return Mono.fromSupplier(() -> mapperFactory.getMessageIdMapper(session)
                .find(messageMoveEvent.getMessageIds(), MessageMapper.FetchType.Full)
                .stream()
                .map((ThrowingFunction<MailboxMessage, MessageToLearn>) message -> new MessageToLearn(message.getFullContent(), message.getFullContentOctets()))
                .collect(Guavate.toImmutableList()))
            .subscribeOn(Schedulers.elastic());
    }

    @VisibleForTesting
    Mono<Boolean> isMessageMovedToSpamMailbox(MessageMoveEvent event) {
        return findMailboxByRole(Role.SPAM, event.getUsername())
            .map(spamMailboxId -> event.getMessageMoves().addedMailboxIds().contains(spamMailboxId))
            .onErrorResume(e -> {
                LOGGER.warn("Could not resolve Spam mailbox", e);
                return Mono.just(false);
            });
    }

    @VisibleForTesting
    Mono<Boolean> isMessageMovedOutOfSpamMailbox(MessageMoveEvent event) {
        Mono<MailboxId> spamMailboxIdMono = findMailboxByRole(Role.SPAM, event.getUsername());
        Mono<MailboxId> trashMailboxIdMono = findMailboxByRole(Role.TRASH, event.getUsername());

        return spamMailboxIdMono.zipWith(trashMailboxIdMono)
            .map(tuple -> {
                MailboxId spamMailboxId = tuple.getT1();
                MailboxId trashMailboxId = tuple.getT2();

                return event.getMessageMoves().removedMailboxIds().contains(spamMailboxId)
                    && !event.getMessageMoves().addedMailboxIds().contains(trashMailboxId);
            })
            .onErrorResume(e -> {
                LOGGER.warn("Could not resolve Spam mailbox", e);
                return Mono.just(false);
            });
    }

    private Mono<MailboxId> findMailboxByRole(Role role, Username username) {
        return Mono.fromCallable(() -> systemMailboxesProvider.findMailbox(role, username))
            .map(MessageManager::getId)
            .subscribeOn(Schedulers.elastic());
    }
}
