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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.QueueDAO;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ConductorReactiveQueueTest {

    private QueueDAO queueDAO;
    private ConductorProperties properties;
    private ConductorReactiveQueue reactiveQueue;
    private static final String QUEUE_NAME = "test-queue";

    @Before
    public void setUp() {
        queueDAO = mock(QueueDAO.class);
        properties = new ConductorProperties();
        properties.setEventQueuePollInterval(Duration.ofMillis(100));
        properties.setEventQueuePollCount(10);
        properties.setEventQueueLongPollTimeout(Duration.ofMillis(100));

        reactiveQueue = new ConductorReactiveQueue(QUEUE_NAME, queueDAO, properties);
    }

    @Test
    public void testGetType() {
        assertEquals("conductor", reactiveQueue.getType());
    }

    @Test
    public void testGetName() {
        assertEquals(QUEUE_NAME, reactiveQueue.getName());
    }

    @Test
    public void testGetURI() {
        assertEquals(QUEUE_NAME, reactiveQueue.getURI());
    }

    @Test
    public void testPollReturnsMessages() {
        Message msg1 = new Message("id1", "payload1", null);
        Message msg2 = new Message("id2", "payload2", null);
        List<Message> messages = Arrays.asList(msg1, msg2);

        when(queueDAO.pollMessages(eq(QUEUE_NAME), anyInt(), anyInt())).thenReturn(messages);

        reactiveQueue.start().block();

        StepVerifier.create(reactiveQueue.poll()).expectNext(messages).verifyComplete();

        verify(queueDAO).pollMessages(eq(QUEUE_NAME), eq(10), eq(100));
    }

    @Test
    public void testPollReturnsEmptyWhenNotRunning() {
        StepVerifier.create(reactiveQueue.poll()).expectNextMatches(List::isEmpty).verifyComplete();

        verify(queueDAO, never()).pollMessages(anyString(), anyInt(), anyInt());
    }

    @Test
    public void testAckMessages() {
        Message msg = new Message("id1", "payload1", null);
        List<Message> messages = Collections.singletonList(msg);

        StepVerifier.create(reactiveQueue.ack(messages)).verifyComplete();

        verify(queueDAO).ack(QUEUE_NAME, "id1");
    }

    @Test
    public void testPublishMessages() {
        Message msg = new Message("id1", "payload1", null);
        List<Message> messages = Collections.singletonList(msg);

        StepVerifier.create(reactiveQueue.publish(messages)).verifyComplete();

        verify(queueDAO).push(QUEUE_NAME, messages);
    }

    @Test
    public void testSize() {
        when(queueDAO.getSize(QUEUE_NAME)).thenReturn(42L);

        StepVerifier.create(reactiveQueue.size()).expectNext(42L).verifyComplete();

        verify(queueDAO).getSize(QUEUE_NAME);
    }

    @Test
    public void testStartAndStop() {
        StepVerifier.create(reactiveQueue.start()).verifyComplete();

        StepVerifier.create(reactiveQueue.stop()).verifyComplete();
    }

    @Test
    public void testMessagesFluxEmitsMessages() {
        Message msg1 = new Message("id1", "payload1", null);
        Message msg2 = new Message("id2", "payload2", null);

        when(queueDAO.pollMessages(eq(QUEUE_NAME), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(msg1, msg2))
                .thenReturn(Collections.emptyList());

        reactiveQueue.start().block();

        Flux<Message> messages = reactiveQueue.messages().take(2);

        StepVerifier.create(messages).expectNext(msg1).expectNext(msg2).verifyComplete();
    }

    @Test
    public void testMessagesFluxHandlesErrors() {
        when(queueDAO.pollMessages(eq(QUEUE_NAME), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Test error"))
                .thenReturn(Collections.emptyList());

        reactiveQueue.start().block();

        // Should not fail, should continue polling
        Flux<Message> messages = reactiveQueue.messages().take(Duration.ofMillis(500));

        StepVerifier.create(messages).expectNextCount(0).verifyComplete();
    }
}
