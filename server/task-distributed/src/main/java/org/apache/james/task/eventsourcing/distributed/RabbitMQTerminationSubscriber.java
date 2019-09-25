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

package org.apache.james.task.eventsourcing.distributed;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.task.eventsourcing.TerminationSubscriber;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import reactor.core.Disposable;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;

public class RabbitMQTerminationSubscriber implements TerminationSubscriber, Startable, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQTerminationSubscriber.class);
    private static final Integer MAX_CHANNELS_NUMBER = 1;
    private static final String EXCHANGE_NAME = "terminationSubscriberExchange";
    private static final String QUEUE_NAME_PREFIX = "terminationSubscriber";
    private static final String ROUTING_KEY = "terminationSubscriberRoutingKey";

    private final JsonEventSerializer serializer;
    private final Mono<Connection> connectionMono;
    private final ReactorRabbitMQChannelPool channelPool;
    private final String queueName;
    private UnicastProcessor<OutboundMessage> sendQueue;
    private DirectProcessor<Event> listener;
    private Disposable sendQueueHandle;
    private Disposable listenQueueHandle;

    @Inject
    public RabbitMQTerminationSubscriber(SimpleConnectionPool simpleConnectionPool, JsonEventSerializer serializer) {
        this.serializer = serializer;
        this.connectionMono = simpleConnectionPool.getResilientConnection();
        this.channelPool = new ReactorRabbitMQChannelPool(connectionMono, MAX_CHANNELS_NUMBER);
        this.queueName = QUEUE_NAME_PREFIX + UUID.randomUUID().toString();
    }

    public void start() {
        Sender sender = channelPool.createSender();

        sender.declareExchange(ExchangeSpecification.exchange(EXCHANGE_NAME)).block();
        sender.declare(QueueSpecification.queue(queueName).durable(false).autoDelete(true)).block();
        sender.bind(BindingSpecification.binding(EXCHANGE_NAME, ROUTING_KEY, queueName)).block();
        sendQueue = UnicastProcessor.create();
        sendQueueHandle = sender
            .send(sendQueue)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();

        Receiver receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionMono));
        listener = DirectProcessor.create();
        listenQueueHandle = receiver
            .consumeAutoAck(queueName)
            .subscribeOn(Schedulers.boundedElastic())
            .concatMap(this::toEvent)
            .subscribe(listener::onNext);
    }

    @Override
    public void addEvent(Event event) {
        try {
            byte[] payload = serializer.serialize(event).getBytes(StandardCharsets.UTF_8);
            AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder().build();
            OutboundMessage message = new OutboundMessage(EXCHANGE_NAME, ROUTING_KEY, basicProperties, payload);
            sendQueue.onNext(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Publisher<Event> listenEvents() {
        return listener
            .share();
    }

    private Mono<Event> toEvent(Delivery delivery) {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        try {
            Event event = serializer.deserialize(message);
            return Mono.just(event);
        } catch (Exception e) {
            LOGGER.error("Unable to deserialize '{}'", message, e);
            return Mono.empty();
        }
    }

    @Override
    @PreDestroy
    public void close() {
        Optional.ofNullable(sendQueueHandle).ifPresent(Disposable::dispose);
        Optional.ofNullable(listenQueueHandle).ifPresent(Disposable::dispose);
        channelPool.close();
    }
}
