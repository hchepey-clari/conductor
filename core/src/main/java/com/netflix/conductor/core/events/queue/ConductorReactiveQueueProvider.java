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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.ReactiveEventQueueProvider;
import com.netflix.conductor.dao.QueueDAO;

/**
 * Provider for Conductor's internal reactive queues. Creates {@link ConductorReactiveQueue}
 * instances backed by {@link QueueDAO}.
 */
@Component
@ConditionalOnProperty(name = "conductor.reactor-event-processing-enabled", havingValue = "true")
public class ConductorReactiveQueueProvider implements ReactiveEventQueueProvider {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ConductorReactiveQueueProvider.class);
    private static final String QUEUE_TYPE = "conductor";

    private final Map<String, ReactiveQueue> queues = new ConcurrentHashMap<>();
    private final QueueDAO queueDAO;
    private final ConductorProperties properties;

    public ConductorReactiveQueueProvider(QueueDAO queueDAO, ConductorProperties properties) {
        this.queueDAO = queueDAO;
        this.properties = properties;
        LOGGER.info("ConductorReactiveQueueProvider initialized");
    }

    @Override
    public String getQueueType() {
        return QUEUE_TYPE;
    }

    @Override
    @NonNull
    public ReactiveQueue getQueue(String queueURI) {
        return queues.computeIfAbsent(
                queueURI,
                uri -> {
                    LOGGER.info("Creating ConductorReactiveQueue for: {}", uri);
                    return new ConductorReactiveQueue(uri, queueDAO, properties);
                });
    }
}
