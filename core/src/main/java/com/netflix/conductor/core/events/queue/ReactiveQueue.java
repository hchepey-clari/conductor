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

import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive queue interface using Project Reactor for scalable event processing. This interface
 * provides a thread-count independent alternative to {@link ObservableQueue}.
 *
 * <p>Key advantages over ObservableQueue:
 *
 * <ul>
 *   <li>Thread-count independent - can handle unlimited queues with fixed thread pool
 *   <li>Built-in backpressure handling
 *   <li>Non-blocking I/O operations
 *   <li>Better resource utilization through elastic scheduling
 * </ul>
 */
public interface ReactiveQueue {

    /**
     * Returns a Flux that continuously polls and emits messages from the queue. The Flux is hot and
     * should be shared among multiple subscribers.
     *
     * <p>Implementation should:
     *
     * <ul>
     *   <li>Poll the queue at regular intervals
     *   <li>Handle backpressure appropriately
     *   <li>Emit messages as they become available
     *   <li>Handle errors gracefully and continue polling
     * </ul>
     *
     * @return A Flux of messages from the queue
     */
    Flux<Message> messages();

    /**
     * Polls the queue once and returns available messages. This is a non-blocking operation that
     * returns immediately.
     *
     * @return A Mono containing a list of messages, or empty list if no messages available
     */
    Mono<List<Message>> poll();

    /**
     * Acknowledges that messages have been successfully processed. This is a non-blocking
     * operation.
     *
     * @param messages Messages to acknowledge
     * @return A Mono that completes when acknowledgment is done
     */
    Mono<Void> ack(List<Message> messages);

    /**
     * Negatively acknowledges messages (requeue for retry). This is a non-blocking operation.
     *
     * @param messages Messages to negatively acknowledge
     * @return A Mono that completes when nack is done
     */
    default Mono<Void> nack(List<Message> messages) {
        return Mono.empty();
    }

    /**
     * Publishes messages to the queue. This is a non-blocking operation.
     *
     * @param messages Messages to publish
     * @return A Mono that completes when publishing is done
     */
    Mono<Void> publish(List<Message> messages);

    /**
     * Extends the visibility timeout for a message. This is a non-blocking operation.
     *
     * @param message Message to extend timeout for
     * @param unackTimeout New timeout in milliseconds
     * @return A Mono that completes when timeout is extended
     */
    default Mono<Void> setUnackTimeout(Message message, long unackTimeout) {
        return Mono.empty();
    }

    /**
     * Returns the approximate size of the queue. This is a non-blocking operation.
     *
     * @return A Mono containing the queue size
     */
    Mono<Long> size();

    /**
     * @return Type of the queue (e.g., "sqs", "kafka", "conductor")
     */
    String getType();

    /**
     * @return Name of the queue
     */
    String getName();

    /**
     * @return URI identifier for the queue
     */
    String getURI();

    /**
     * Determines if the queue supports visibility timeout. If false, messages must be explicitly
     * re-published for retry.
     *
     * @return true if queue needs explicit re-publishing for retry
     */
    default boolean rePublishIfNoAck() {
        return false;
    }

    /**
     * Starts the queue (lifecycle method). Should be called before using the queue.
     *
     * @return A Mono that completes when queue is started
     */
    default Mono<Void> start() {
        return Mono.empty();
    }

    /**
     * Stops the queue (lifecycle method). Should be called when queue is no longer needed.
     *
     * @return A Mono that completes when queue is stopped
     */
    default Mono<Void> stop() {
        return Mono.empty();
    }

    /**
     * Closes the queue and releases resources.
     *
     * @return A Mono that completes when queue is closed
     */
    default Mono<Void> close() {
        return Mono.empty();
    }
}
