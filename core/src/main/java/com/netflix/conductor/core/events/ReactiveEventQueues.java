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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.events.queue.ReactiveQueue;
import com.netflix.conductor.core.utils.ParametersUtils;

/**
 * Holder for reactive event queues. Provides access to {@link ReactiveQueue} instances based on
 * event type.
 *
 * <p>This is the reactive equivalent of {@link EventQueues}.
 */
@Component
@ConditionalOnProperty(name = "conductor.reactor-event-processing-enabled", havingValue = "true")
public class ReactiveEventQueues {

    public static final String REACTIVE_EVENT_QUEUE_PROVIDERS_QUALIFIER =
            "ReactiveEventQueueProviders";

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveEventQueues.class);

    private final ParametersUtils parametersUtils;
    private final Map<String, ReactiveEventQueueProvider> providers;

    public ReactiveEventQueues(
            @Qualifier(REACTIVE_EVENT_QUEUE_PROVIDERS_QUALIFIER)
                    Map<String, ReactiveEventQueueProvider> providers,
            ParametersUtils parametersUtils) {
        this.providers = providers;
        this.parametersUtils = parametersUtils;
        LOGGER.info(
                "ReactiveEventQueues initialized with providers: {}",
                providers.keySet().stream().collect(Collectors.joining(", ")));
    }

    /**
     * Gets the list of registered queue provider class names.
     *
     * @return List of provider class names
     */
    public List<String> getProviders() {
        return providers.values().stream()
                .map(p -> p.getClass().getName())
                .collect(Collectors.toList());
    }

    /**
     * Gets a reactive queue for the given event type. Event type format: "type:queueURI" (e.g.,
     * "sqs:my-queue", "kafka:my-topic")
     *
     * @param eventType The event type in format "type:queueURI"
     * @return The reactive queue for the event type
     * @throws IllegalArgumentException if event type is invalid or provider not found
     */
    @NonNull
    public ReactiveQueue getQueue(String eventType) {
        String event = parametersUtils.replace(eventType).toString();
        int index = event.indexOf(':');
        if (index == -1) {
            throw new IllegalArgumentException(
                    "Illegal event format: " + event + ". Expected format: 'type:queueURI'");
        }

        String type = event.substring(0, index);
        String queueURI = event.substring(index + 1);

        ReactiveEventQueueProvider provider = providers.get(type);
        if (provider != null) {
            LOGGER.debug("Getting reactive queue for type: {}, URI: {}", type, queueURI);
            return provider.getQueue(queueURI);
        } else {
            throw new IllegalArgumentException(
                    "Unknown queue type: " + type + ". Available types: " + providers.keySet());
        }
    }

    /**
     * Checks if a provider exists for the given queue type.
     *
     * @param type The queue type (e.g., "sqs", "kafka", "conductor")
     * @return true if provider exists
     */
    public boolean hasProvider(String type) {
        return providers.containsKey(type);
    }
}
