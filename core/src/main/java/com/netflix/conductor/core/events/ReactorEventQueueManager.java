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
package com.netflix.conductor.core.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ReactiveQueue;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.metrics.Monitors;

import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

/**
 * Reactor-based event queue manager that provides scalable, thread-count independent event
 * processing.
 *
 * <p>Key improvements over {@link DefaultEventQueueManager}:
 *
 * <ul>
 *   <li><b>Thread-count independent</b>: Can handle unlimited queues with fixed thread pool
 *   <li><b>Elastic scheduling</b>: Threads created/destroyed on demand
 *   <li><b>Backpressure handling</b>: Prevents memory overflow under high load
 *   <li><b>Non-blocking I/O</b>: Better resource utilization
 * </ul>
 *
 * <p>This manager uses Project Reactor's elastic scheduler which creates threads on-demand and
 * releases them when idle, making it suitable for handling hundreds or thousands of event queues
 * without requiring a proportional number of threads.
 */
@Component
@ConditionalOnProperty(name = "conductor.reactor-event-processing-enabled", havingValue = "true")
public class ReactorEventQueueManager extends LifecycleAwareComponent implements EventQueueManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactorEventQueueManager.class);

    private final EventHandlerDAO eventHandlerDAO;
    private final ReactiveEventQueues reactiveEventQueues;
    private final DefaultEventProcessor defaultEventProcessor;
    private final ConductorProperties properties;

    private final Map<String, ReactiveQueue> eventToQueueMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> queueSubscriptions = new ConcurrentHashMap<>();

    public ReactorEventQueueManager(
            EventHandlerDAO eventHandlerDAO,
            ReactiveEventQueues reactiveEventQueues,
            DefaultEventProcessor defaultEventProcessor,
            ConductorProperties properties) {
        this.eventHandlerDAO = eventHandlerDAO;
        this.reactiveEventQueues = reactiveEventQueues;
        this.defaultEventProcessor = defaultEventProcessor;
        this.properties = properties;

        LOGGER.info(
                "ReactorEventQueueManager initialized with max concurrency: {}, buffer size: {}",
                properties.getReactorEventQueueMaxConcurrency(),
                properties.getReactorEventQueueBufferSize());
    }

    @Override
    public Map<String, String> getQueues() {
        Map<String, String> queues = new HashMap<>();
        eventToQueueMap.forEach((key, value) -> queues.put(key, value.getName()));
        return queues;
    }

    @Override
    public Map<String, Map<String, Long>> getQueueSizes() {
        Map<String, Map<String, Long>> queues = new HashMap<>();
        eventToQueueMap.forEach(
                (key, value) -> {
                    // Get size asynchronously and block for result
                    Long size = value.size().block();
                    Map<String, Long> sizeMap = new HashMap<>();
                    sizeMap.put(value.getName(), size != null ? size : 0L);
                    queues.put(key, sizeMap);
                });
        return queues;
    }

    @Override
    public void doStart() {
        LOGGER.info("Starting ReactorEventQueueManager");
        eventToQueueMap.forEach(
                (event, queue) -> {
                    LOGGER.info("Starting reactive queue for event: {}", event);
                    queue.start().subscribe();
                    subscribeToQueue(event, queue);
                });
    }

    @Override
    public void doStop() {
        LOGGER.info("Stopping ReactorEventQueueManager");

        // Dispose all subscriptions
        queueSubscriptions.forEach(
                (event, disposable) -> {
                    LOGGER.info("Disposing subscription for event: {}", event);
                    disposable.dispose();
                });
        queueSubscriptions.clear();

        // Stop all queues
        eventToQueueMap.forEach(
                (event, queue) -> {
                    LOGGER.info("Stopping reactive queue for event: {}", event);
                    queue.stop().subscribe();
                });
    }

    /**
     * Periodically refreshes the event queues based on registered event handlers. This runs every
     * 60 seconds to pick up new event handlers or remove inactive ones.
     */
    @Scheduled(fixedDelay = 60_000)
    public void refreshEventQueues() {
        try {
            Set<String> events =
                    eventHandlerDAO.getAllEventHandlers().stream()
                            .filter(EventHandler::isActive)
                            .map(EventHandler::getEvent)
                            .collect(Collectors.toSet());

            // Add new queues
            List<String> newQueues = new ArrayList<>();
            events.forEach(
                    event -> {
                        if (!eventToQueueMap.containsKey(event)) {
                            try {
                                ReactiveQueue queue = reactiveEventQueues.getQueue(event);
                                eventToQueueMap.put(event, queue);
                                newQueues.add(event);
                            } catch (Exception e) {
                                LOGGER.error("Failed to create queue for event: {}", event, e);
                            }
                        }
                    });

            // Start and subscribe to new queues
            newQueues.forEach(
                    event -> {
                        ReactiveQueue queue = eventToQueueMap.get(event);
                        queue.start().subscribe();
                        subscribeToQueue(event, queue);
                        LOGGER.info("Added and started new reactive queue for event: {}", event);
                    });

            // Remove inactive queues
            Set<String> removed = new HashSet<>(eventToQueueMap.keySet());
            removed.removeAll(events);
            removed.forEach(
                    event -> {
                        LOGGER.info("Removing inactive queue for event: {}", event);

                        // Dispose subscription
                        Disposable disposable = queueSubscriptions.remove(event);
                        if (disposable != null) {
                            disposable.dispose();
                        }

                        // Stop and remove queue
                        ReactiveQueue queue = eventToQueueMap.remove(event);
                        if (queue != null) {
                            queue.stop().subscribe();
                        }
                    });

            // Record metrics
            Map<String, Map<String, Long>> eventToQueueSize = getQueueSizes();
            eventToQueueSize.forEach(
                    (event, queueMap) -> {
                        Map.Entry<String, Long> queueSize = queueMap.entrySet().iterator().next();
                        Monitors.recordEventQueueDepth(queueSize.getKey(), queueSize.getValue());
                    });

            LOGGER.debug("Active event queues: {}", eventToQueueMap.keySet());
            LOGGER.debug("New queues added: {}", newQueues);
            LOGGER.debug("Queues removed: {}", removed);

        } catch (Exception e) {
            Monitors.error(getClass().getSimpleName(), "refresh");
            LOGGER.error("Failed to refresh event queues", e);
        }
    }

    /**
     * Subscribes to a reactive queue and processes messages. Uses Reactor's parallel processing
     * with configurable concurrency.
     *
     * @param event The event name
     * @param queue The reactive queue to subscribe to
     */
    private void subscribeToQueue(String event, ReactiveQueue queue) {
        Disposable subscription =
                queue.messages()
                        .parallel(properties.getReactorEventQueueMaxConcurrency())
                        .runOn(Schedulers.boundedElastic())
                        .doOnNext(
                                msg -> {
                                    LOGGER.debug(
                                            "Processing message: {} from queue: {}",
                                            msg.getId(),
                                            queue.getName());
                                    processMessage(queue, msg);
                                })
                        .doOnError(
                                error -> {
                                    LOGGER.error(
                                            "Error processing message from queue: {}",
                                            queue.getName(),
                                            error);
                                    Monitors.recordEventQueueMessagesError(
                                            queue.getType(), queue.getName());
                                })
                        .sequential()
                        .subscribe();

        queueSubscriptions.put(event, subscription);
        LOGGER.info("Subscribed to reactive queue for event: {}", event);
    }

    /**
     * Processes a single message from the queue. Delegates to DefaultEventProcessor for actual
     * event handling.
     *
     * @param queue The queue the message came from
     * @param msg The message to process
     */
    private void processMessage(ReactiveQueue queue, Message msg) {
        try {
            // Use a wrapper to make ReactiveQueue compatible with DefaultEventProcessor
            ObservableQueueWrapper wrapper = new ObservableQueueWrapper(queue);
            defaultEventProcessor.handle(wrapper, msg);
        } catch (Exception e) {
            LOGGER.error(
                    "Error processing message: {} from queue: {}", msg.getId(), queue.getName(), e);
            Monitors.recordEventQueueMessagesError(queue.getType(), queue.getName());
        }
    }

    /**
     * Wrapper to make ReactiveQueue compatible with DefaultEventProcessor. This is a temporary
     * adapter until DefaultEventProcessor is updated to work with ReactiveQueue directly.
     */
    private static class ObservableQueueWrapper
            implements com.netflix.conductor.core.events.queue.ObservableQueue {
        private final ReactiveQueue reactiveQueue;

        ObservableQueueWrapper(ReactiveQueue reactiveQueue) {
            this.reactiveQueue = reactiveQueue;
        }

        @Override
        public rx.Observable<Message> observe() {
            throw new UnsupportedOperationException("Not supported in wrapper");
        }

        @Override
        public String getType() {
            return reactiveQueue.getType();
        }

        @Override
        public String getName() {
            return reactiveQueue.getName();
        }

        @Override
        public String getURI() {
            return reactiveQueue.getURI();
        }

        @Override
        public List<String> ack(List<Message> messages) {
            reactiveQueue.ack(messages).block();
            return messages.stream().map(Message::getId).collect(Collectors.toList());
        }

        @Override
        public void nack(List<Message> messages) {
            reactiveQueue.nack(messages).block();
        }

        @Override
        public void publish(List<Message> messages) {
            reactiveQueue.publish(messages).block();
        }

        @Override
        public void setUnackTimeout(Message message, long unackTimeout) {
            reactiveQueue.setUnackTimeout(message, unackTimeout).block();
        }

        @Override
        public long size() {
            Long size = reactiveQueue.size().block();
            return size != null ? size : 0L;
        }

        @Override
        public boolean rePublishIfNoAck() {
            return reactiveQueue.rePublishIfNoAck();
        }

        @Override
        public void start() {
            reactiveQueue.start().block();
        }

        @Override
        public void stop() {
            reactiveQueue.stop().block();
        }

        @Override
        public boolean isRunning() {
            return true;
        }
    }
}
