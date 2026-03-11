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
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adapter that wraps an {@link ObservableQueue} to provide {@link ReactiveQueue} interface. This
 * allows existing queue implementations to work with the new Reactor-based event processing.
 *
 * <p>This adapter:
 *
 * <ul>
 *   <li>Converts blocking operations to non-blocking Reactor operations
 *   <li>Uses elastic scheduler for I/O operations
 *   <li>Handles errors gracefully
 * </ul>
 */
public class ReactiveQueueAdapter implements ReactiveQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveQueueAdapter.class);

    private final ObservableQueue observableQueue;
    private final Duration pollInterval;
    private volatile boolean running = false;

    /**
     * Creates a new adapter for the given ObservableQueue.
     *
     * @param observableQueue The queue to adapt
     * @param pollInterval The interval at which to poll the queue
     */
    public ReactiveQueueAdapter(ObservableQueue observableQueue, Duration pollInterval) {
        this.observableQueue = observableQueue;
        this.pollInterval = pollInterval;
    }

    @Override
    public Flux<Message> messages() {
        return Flux.interval(pollInterval, Schedulers.boundedElastic())
                .filter(tick -> running)
                .flatMap(tick -> poll())
                .flatMapIterable(messages -> messages)
                .doOnError(
                        error ->
                                LOGGER.error(
                                        "Error polling queue: {}",
                                        observableQueue.getName(),
                                        error))
                .retry(); // Retry on error to keep polling
    }

    @Override
    public Mono<List<Message>> poll() {
        return Mono.fromCallable(
                        () -> {
                            try {
                                // Call the blocking receiveMessages method on elastic scheduler
                                // This is a workaround since ObservableQueue doesn't expose
                                // receiveMessages
                                // In practice, we'll need to enhance ObservableQueue or use
                                // queue-specific implementations
                                return Collections.<Message>emptyList();
                            } catch (Exception e) {
                                LOGGER.error(
                                        "Error polling queue: {}", observableQueue.getName(), e);
                                return Collections.<Message>emptyList();
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> ack(List<Message> messages) {
        return Mono.fromRunnable(() -> observableQueue.ack(messages))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> nack(List<Message> messages) {
        return Mono.fromRunnable(() -> observableQueue.nack(messages))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> publish(List<Message> messages) {
        return Mono.fromRunnable(() -> observableQueue.publish(messages))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> setUnackTimeout(Message message, long unackTimeout) {
        return Mono.fromRunnable(() -> observableQueue.setUnackTimeout(message, unackTimeout))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Long> size() {
        return Mono.fromCallable(() -> observableQueue.size())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public String getType() {
        return observableQueue.getType();
    }

    @Override
    public String getName() {
        return observableQueue.getName();
    }

    @Override
    public String getURI() {
        return observableQueue.getURI();
    }

    @Override
    public boolean rePublishIfNoAck() {
        return observableQueue.rePublishIfNoAck();
    }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(
                        () -> {
                            observableQueue.start();
                            running = true;
                            LOGGER.info(
                                    "Started ReactiveQueue adapter for: {}",
                                    observableQueue.getName());
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(
                        () -> {
                            running = false;
                            observableQueue.stop();
                            LOGGER.info(
                                    "Stopped ReactiveQueue adapter for: {}",
                                    observableQueue.getName());
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> close() {
        return stop().then(Mono.fromRunnable(() -> observableQueue.close()));
    }
}
