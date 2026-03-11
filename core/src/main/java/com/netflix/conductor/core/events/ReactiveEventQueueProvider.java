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

import org.springframework.lang.NonNull;

import com.netflix.conductor.core.events.queue.ReactiveQueue;

/**
 * Provider interface for creating {@link ReactiveQueue} instances. This is the reactive equivalent
 * of {@link EventQueueProvider}.
 *
 * <p>Implementations should:
 *
 * <ul>
 *   <li>Create or retrieve reactive queues for the given queue URI
 *   <li>Cache queue instances to avoid creating duplicates
 *   <li>Handle queue lifecycle appropriately
 * </ul>
 */
public interface ReactiveEventQueueProvider {

    /**
     * Returns the queue type this provider handles. Examples: "sqs", "kafka", "conductor", "amqp",
     * "nats"
     *
     * @return The queue type identifier
     */
    String getQueueType();

    /**
     * Creates or retrieves a {@link ReactiveQueue} for the given queue URI.
     *
     * @param queueURI The URI of the queue (e.g., queue name, topic name)
     * @return The reactive queue instance
     * @throws IllegalArgumentException if the queue cannot be created for the given URI
     */
    @NonNull
    ReactiveQueue getQueue(String queueURI) throws IllegalArgumentException;
}
