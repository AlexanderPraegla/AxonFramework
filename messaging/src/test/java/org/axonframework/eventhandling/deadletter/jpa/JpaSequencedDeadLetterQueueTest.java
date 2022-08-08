/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JpaSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    private final TransactionManager transactionManager = spy(new NoOpTransactionManager());
    EntityManagerFactory emf = Persistence.createEntityManagerFactory("dlq");
    EntityManager entityManager = emf.createEntityManager();
    private EntityTransaction transaction;

    @BeforeEach
    public void setUpJpa() throws SQLException {
        transaction = entityManager.getTransaction();
        transaction.begin();
    }

    @AfterEach
    public void rollback() {
        transaction.rollback();
    }

    @Override
    protected void setClock(Clock clock) {
        JpaSequencedDeadLetterQueue.clock = clock;
    }

    @Override
    public DeadLetter<EventMessage<?>> generateInitialLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateFollowUpLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent());
    }

    @Override
    protected DeadLetter<EventMessage<?>> mapToQueueImplementation(DeadLetter<EventMessage<?>> deadLetter) {
        if (deadLetter instanceof JpaDeadLetter) {
            return deadLetter;
        }
        if (deadLetter instanceof GenericDeadLetter) {
            return new JpaDeadLetter<>(IdentifierFactory.getInstance().generateIdentifier(),
                                       0L,
                                       ((GenericDeadLetter<EventMessage<?>>) deadLetter).getSequenceIdentifier()
                                                                                        .toString(),
                                       JpaSequencedDeadLetterQueue.clock.instant(),
                                       JpaSequencedDeadLetterQueue.clock.instant(),
                                       deadLetter.cause().orElse(null),
                                       deadLetter.diagnostics(),
                                       deadLetter.message());
        }
        throw new IllegalArgumentException("Can not map dead letter of type " + deadLetter.getClass().getName());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateRequeuedLetter(DeadLetter<EventMessage<?>> original,
                                                                 Instant lastTouched,
                                                                 Throwable requeueCause,
                                                                 MetaData diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause).withDiagnostics(diagnostics).markTouched();
    }

    @Override
    protected void assertLetter(DeadLetter<? extends EventMessage<?>> expected,
                                DeadLetter<? extends EventMessage<?>> actual) {

        assertEquals(expected.message().getPayload(), actual.message().getPayload());
        assertEquals(expected.message().getPayloadType(), actual.message().getPayloadType());
        assertEquals(expected.message().getMetaData(), actual.message().getMetaData());
        assertEquals(expected.message().getIdentifier(), actual.message().getIdentifier());
        assertEquals(expected.cause(), actual.cause());
        // Database rounding/parse differences
        assertTrue(ChronoUnit.MILLIS.between(expected.enqueuedAt(), actual.enqueuedAt()) <= 100);
        assertTrue(ChronoUnit.MILLIS.between(expected.lastTouched(), actual.lastTouched()) <= 100);
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }

    @Override
    public SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
        return JpaSequencedDeadLetterQueue
                .builder()
                .transactionManager(transactionManager)
                .entityManagerProvider(entityManagerProvider)
                .maxSequences(128)
                .processingGroup("my_processing_group")
                .build();
    }

    @Test
    void testMaxSequences() {
        int expectedMaxQueues = 128;

        JpaSequencedDeadLetterQueue<EventMessage<?>> testSubject = JpaSequencedDeadLetterQueue.builder()
                                                                                              .maxSequences(
                                                                                                      expectedMaxQueues)
                                                                                              .processingGroup(
                                                                                                      "my_processing_group")
                                                                                              .build();

        assertEquals(expectedMaxQueues, testSubject.maxSequences());
    }

    @Test
    void testMaxSequenceSize() {
        int expectedMaxQueueSize = 128;

        JpaSequencedDeadLetterQueue<EventMessage<?>> testSubject = JpaSequencedDeadLetterQueue.builder()
                                                                                              .maxSequenceSize(
                                                                                                      expectedMaxQueueSize)
                                                                                              .processingGroup(
                                                                                                      "my_processing_group")
                                                                                              .build();

        assertEquals(expectedMaxQueueSize, testSubject.maxSequenceSize());
    }

    @Test
    void testBuildWithNegativeMaxQueuesThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(-1));
    }

    @Test
    void testBuildWithValueLowerThanMinimumMaxQueuesThrowsAxonConfigurationException() {
        IntStream.range(0, 127).forEach(i -> {
            JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(i));
        });
    }

    @Test
    void testBuildWithNegativeMaxQueueSizeThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(-1));
    }

    @Test
    void testBuildWithValueLowerThanMinimumMaxQueueSizeThrowsAxonConfigurationException() {
        IntStream.range(0, 127).forEach(i -> {
            JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

            assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(i));
        });
    }

    /**
     * A non-final {@link TransactionManager} implementation, so that it can be spied upon through Mockito.
     */
    private static class NoOpTransactionManager implements TransactionManager {

        @Override
        public Transaction startTransaction() {
            return new Transaction() {
                @Override
                public void commit() {
                    // No-op
                }

                @Override
                public void rollback() {
                    // No-op
                }
            };
        }
    }
}
