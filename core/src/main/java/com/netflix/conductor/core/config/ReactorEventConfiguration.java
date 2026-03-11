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
package com.netflix.conductor.core.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.events.ReactiveEventQueueProvider;
import com.netflix.conductor.core.events.ReactiveEventQueues;

/**
 * Configuration for Reactor-based event processing. This configuration is activated when
 * conductor.reactor-event-processing-enabled=true.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.reactor-event-processing-enabled", havingValue = "true")
public class ReactorEventConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactorEventConfiguration.class);

    /**
     * Creates a map of reactive event queue providers. This bean collects all {@link
     * ReactiveEventQueueProvider} implementations and makes them available to {@link
     * ReactiveEventQueues}.
     *
     * @param providers List of all reactive event queue providers
     * @return Map of queue type to provider
     */
    @Bean
    @Qualifier(ReactiveEventQueues.REACTIVE_EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public Map<String, ReactiveEventQueueProvider> reactiveEventQueueProviders(
            List<ReactiveEventQueueProvider> providers) {

        Map<String, ReactiveEventQueueProvider> providerMap = new HashMap<>();

        for (ReactiveEventQueueProvider provider : providers) {
            String queueType = provider.getQueueType();
            if (providerMap.containsKey(queueType)) {
                LOGGER.warn(
                        "Duplicate reactive event queue provider for type: {}. "
                                + "Existing: {}, New: {}. Using existing provider.",
                        queueType,
                        providerMap.get(queueType).getClass().getName(),
                        provider.getClass().getName());
            } else {
                providerMap.put(queueType, provider);
                LOGGER.info(
                        "Registered reactive event queue provider: {} for type: {}",
                        provider.getClass().getSimpleName(),
                        queueType);
            }
        }

        if (providerMap.isEmpty()) {
            LOGGER.warn(
                    "No reactive event queue providers found. "
                            + "Event processing may not work correctly.");
        } else {
            LOGGER.info(
                    "Registered {} reactive event queue provider(s): {}",
                    providerMap.size(),
                    providerMap.keySet());
        }

        return providerMap;
    }
}
