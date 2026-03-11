/*
 * Copyright 2024 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.events.queue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive implementation of event queue using Conductor's internal {@link QueueDAO}. This
 * implementation uses Project Reactor for non-blocking, scalable event processing.
 */
public class ConductorReactiveQueue implements ReactiveQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConductorReactiveQueue.class);
    private static final String QUEUE_TYPE = "conductor";

    private final String queueName;
    private final QueueDAO queueDAO;
    private final Duration pollInterval;
    private final int pollCount;
    private final int longPollTimeout;
    private volatile boolean running = false;

    public ConductorReactiveQueue(
            String queueName, QueueDAO queueDAO, ConductorProperties properties) {
        this.queueName = queueName;
        this.queueDAO = queueDAO;
        this.pollInterval = properties.getEventQueuePollInterval();
        this.pollCount = properties.getEventQueuePollCount();
        this.longPollTimeout = (int) properties.getEventQueueLongPollTimeout().toMillis();
    }

    @Override
    public Flux<Message> messages() {
        return Flux.interval(pollInterval, Schedulers.boundedElastic())
                .filter(tick -> running)
                .flatMap(tick -> poll())
                .flatMapIterable(messages -> messages)
                .doOnNext(
                        msg ->
                                LOGGER.debug(
                                        "Received message: {} from queue: {}",
                                        msg.getId(),
                                        queueName))
                .doOnError(
                        error -> {
                            LOGGER.error("Error polling queue: {}", queueName, error);
                            Monitors.recordObservableQMessageReceivedErrors(QUEUE_TYPE);
                        })
                .onErrorResume(
                        error -> {
                            // Continue polling even on error
                            return Flux.empty();
                        })
                .retry(); // Retry indefinitely on errors
    }

    @Override
    public Mono<List<Message>> poll() {
        return Mono.fromCallable(
                        () -> {
                            if (!running) {
                                LOGGER.debug("Queue not running, skipping poll: {}", queueName);
                                return new ArrayList<Message>();
                            }

                            try {
                                List<Message> messages =
                                        queueDAO.pollMessages(
                                                queueName, pollCount, longPollTimeout);
                                Monitors.recordEventQueueMessagesProcessed(
                                        QUEUE_TYPE, queueName, messages.size());
                                Monitors.recordEventQueuePollSize(queueName, messages.size());
                                LOGGER.debug(
                                        "Polled {} messages from queue: {}",
                                        messages.size(),
                                        queueName);
                                return messages;
                            } catch (Exception e) {
                                LOGGER.error(
                                        "Exception while polling messages from queue: {}",
                                        queueName,
                                        e);
                                Monitors.recordObservableQMessageReceivedErrors(QUEUE_TYPE);
                                return new ArrayList<Message>();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> ack(List<Message> messages) {
        return Mono.fromRunnable(
                        () -> {
                            for (Message msg : messages) {
                                queueDAO.ack(queueName, msg.getId());
                            }
                            LOGGER.debug(
                                    "Acknowledged {} messages from queue: {}",
                                    messages.size(),
                                    queueName);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> publish(List<Message> messages) {
        return Mono.fromRunnable(
                        () -> {
                            queueDAO.push(queueName, messages);
                            LOGGER.debug(
                                    "Published {} messages to queue: {}",
                                    messages.size(),
                                    queueName);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> setUnackTimeout(Message message, long unackTimeout) {
        return Mono.fromRunnable(
                        () -> queueDAO.setUnackTimeout(queueName, message.getId(), unackTimeout))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Long> size() {
        return Mono.fromCallable(() -> queueDAO.getSize(queueName))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public String getType() {
        return QUEUE_TYPE;
    }

    @Override
    public String getName() {
        return queueName;
    }

    @Override
    public String getURI() {
        return queueName;
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(
                        () -> {
                            running = true;
                            LOGGER.info("Started ConductorReactiveQueue: {}", queueName);
                        })
                .then();
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(
                        () -> {
                            running = false;
                            LOGGER.info("Stopped ConductorReactiveQueue: {}", queueName);
                        })
                .then();
    }
}
