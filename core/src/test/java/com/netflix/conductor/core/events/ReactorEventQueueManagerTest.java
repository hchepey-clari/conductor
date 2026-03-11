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

import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.queue.ReactiveQueue;
import com.netflix.conductor.dao.EventHandlerDAO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReactorEventQueueManagerTest {

    private EventHandlerDAO eventHandlerDAO;
    private ReactiveEventQueues reactiveEventQueues;
    private DefaultEventProcessor defaultEventProcessor;
    private ConductorProperties properties;
    private ReactorEventQueueManager manager;

    @Before
    public void setUp() {
        eventHandlerDAO = mock(EventHandlerDAO.class);
        reactiveEventQueues = mock(ReactiveEventQueues.class);
        defaultEventProcessor = mock(DefaultEventProcessor.class);

        properties = new ConductorProperties();
        properties.setReactorEventQueueMaxConcurrency(4);
        properties.setReactorEventQueueBufferSize(256);

        manager =
                new ReactorEventQueueManager(
                        eventHandlerDAO, reactiveEventQueues, defaultEventProcessor, properties);
    }

    @Test
    public void testGetQueuesReturnsEmptyInitially() {
        Map<String, String> queues = manager.getQueues();
        assertTrue(queues.isEmpty());
    }

    @Test
    public void testRefreshEventQueuesAddsNewQueues() {
        EventHandler handler = new EventHandler();
        handler.setName("test-handler");
        handler.setEvent("conductor:test-queue");
        handler.setActive(true);

        when(eventHandlerDAO.getAllEventHandlers()).thenReturn(Collections.singletonList(handler));

        ReactiveQueue mockQueue = createMockQueue("test-queue");
        when(reactiveEventQueues.getQueue("conductor:test-queue")).thenReturn(mockQueue);

        manager.refreshEventQueues();

        Map<String, String> queues = manager.getQueues();
        assertEquals(1, queues.size());
        assertTrue(queues.containsKey("conductor:test-queue"));

        verify(mockQueue).start();
    }

    @Test
    public void testRefreshEventQueuesRemovesInactiveQueues() {
        // First add a queue
        EventHandler handler = new EventHandler();
        handler.setName("test-handler");
        handler.setEvent("conductor:test-queue");
        handler.setActive(true);

        when(eventHandlerDAO.getAllEventHandlers()).thenReturn(Collections.singletonList(handler));

        ReactiveQueue mockQueue = createMockQueue("test-queue");
        when(reactiveEventQueues.getQueue("conductor:test-queue")).thenReturn(mockQueue);

        manager.refreshEventQueues();
        assertEquals(1, manager.getQueues().size());

        // Now remove it
        when(eventHandlerDAO.getAllEventHandlers()).thenReturn(Collections.emptyList());

        manager.refreshEventQueues();

        Map<String, String> queues = manager.getQueues();
        assertTrue(queues.isEmpty());

        verify(mockQueue).stop();
    }

    @Test
    public void testStartAndStopManager() {
        EventHandler handler = new EventHandler();
        handler.setName("test-handler");
        handler.setEvent("conductor:test-queue");
        handler.setActive(true);

        when(eventHandlerDAO.getAllEventHandlers()).thenReturn(Collections.singletonList(handler));

        ReactiveQueue mockQueue = createMockQueue("test-queue");
        when(reactiveEventQueues.getQueue("conductor:test-queue")).thenReturn(mockQueue);

        manager.refreshEventQueues();

        manager.doStart();
        verify(mockQueue, atLeastOnce()).start();

        manager.doStop();
        // Stop is called during refresh and doStop
        verify(mockQueue, atLeast(1)).stop();
    }

    @Test
    public void testGetQueueSizes() {
        EventHandler handler = new EventHandler();
        handler.setName("test-handler");
        handler.setEvent("conductor:test-queue");
        handler.setActive(true);

        when(eventHandlerDAO.getAllEventHandlers()).thenReturn(Collections.singletonList(handler));

        ReactiveQueue mockQueue = createMockQueue("test-queue");
        when(mockQueue.size()).thenReturn(Mono.just(42L));
        when(reactiveEventQueues.getQueue("conductor:test-queue")).thenReturn(mockQueue);

        manager.refreshEventQueues();

        Map<String, Map<String, Long>> sizes = manager.getQueueSizes();
        assertEquals(1, sizes.size());
        assertTrue(sizes.containsKey("conductor:test-queue"));
        assertEquals(Long.valueOf(42L), sizes.get("conductor:test-queue").get("test-queue"));
    }

    private ReactiveQueue createMockQueue(String name) {
        ReactiveQueue queue = mock(ReactiveQueue.class);
        when(queue.getName()).thenReturn(name);
        when(queue.getType()).thenReturn("conductor");
        when(queue.getURI()).thenReturn(name);
        when(queue.start()).thenReturn(Mono.empty());
        when(queue.stop()).thenReturn(Mono.empty());
        when(queue.size()).thenReturn(Mono.just(0L));
        when(queue.messages()).thenReturn(Flux.empty());
        when(queue.rePublishIfNoAck()).thenReturn(false);
        return queue;
    }
}
