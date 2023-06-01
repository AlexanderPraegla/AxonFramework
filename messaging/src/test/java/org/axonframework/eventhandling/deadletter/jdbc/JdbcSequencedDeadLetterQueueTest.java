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

package org.axonframework.eventhandling.deadletter.jdbc;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.axonframework.messaging.deadletter.WrongDeadLetterTypeException;
import org.axonframework.serialization.TestSerializer;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import javax.sql.DataSource;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Implementation of the {@link SequencedDeadLetterQueueTest}, validating the {@link JdbcSequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
class JdbcSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;
    private static final String TEST_PROCESSING_GROUP = "some-processing-group";

    private DataSource dataSource;
    private TransactionManager transactionManager;
    private JdbcSequencedDeadLetterQueue<EventMessage<?>> jdbcDeadLetterQueue;

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        dataSource = dataSource();
        transactionManager = transactionManager(dataSource);
        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.builder()
                                                          .processingGroup(TEST_PROCESSING_GROUP)
                                                          .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                                          .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                                          .connectionProvider(dataSource::getConnection)
                                                          .schema(schema)
                                                          .transactionManager(transactionManager)
                                                          .genericSerializer(TestSerializer.JACKSON.getSerializer())
                                                          .eventSerializer(TestSerializer.JACKSON.getSerializer())
                                                          .build();
        return jdbcDeadLetterQueue;
    }

    private DataSource dataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:axontest");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private TransactionManager transactionManager(DataSource dataSource) {
        PlatformTransactionManager platformTransactionManager = new DataSourceTransactionManager(dataSource);
        return () -> {
            TransactionStatus transaction =
                    platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
            return new Transaction() {
                @Override
                public void commit() {
                    platformTransactionManager.commit(transaction);
                }

                @Override
                public void rollback() {
                    platformTransactionManager.rollback(transaction);
                }
            };
        };
    }

    @BeforeEach
    void setUpJdbc() {
        transactionManager.executeInTransaction(() -> {
            // Clear current DLQ
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                //noinspection SqlDialectInspection,SqlNoDataSourceInspection
                connection.prepareStatement("DROP TABLE IF EXISTS " + schema.deadLetterTable())
                          .executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Enable to retrieve a Connection to drop the dead-letter queue", e);
            } finally {
                closeQuietly(connection);
            }
            // Construct new DLQ
            jdbcDeadLetterQueue.createSchema(new GenericDeadLetterTableFactory());
        });
    }

    @Test
    void invokingEvictWithNonJdbcDeadLetterThrowsWrongDeadLetterTypeException() {
        assertThrows(WrongDeadLetterTypeException.class, () -> jdbcDeadLetterQueue.evict(generateInitialLetter()));
    }

    @Override
    protected long maxSequences() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    protected long maxSequenceSize() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
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
        if (deadLetter instanceof org.axonframework.eventhandling.deadletter.jdbc.JdbcDeadLetter) {
            return deadLetter;
        }
        if (deadLetter instanceof GenericDeadLetter) {
            return new JdbcDeadLetter<>(
                    IdentifierFactory.getInstance().generateIdentifier(),
                    0L,
                    ((GenericDeadLetter<EventMessage<?>>) deadLetter).getSequenceIdentifier().toString(),
                    deadLetter.enqueuedAt(),
                    deadLetter.lastTouched(),
                    deadLetter.cause().orElse(null),
                    deadLetter.diagnostics(),
                    deadLetter.message()
            );
        }
        throw new IllegalArgumentException("Can not map dead letter of type " + deadLetter.getClass().getName());
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }

    @Override
    protected void assertLetter(DeadLetter<? extends EventMessage<?>> expected,
                                DeadLetter<? extends EventMessage<?>> actual) {
        EventMessage<?> expectedMessage = expected.message();
        EventMessage<?> actualMessage = actual.message();
        assertEquals(expectedMessage.getPayload(), actualMessage.getPayload());
        assertEquals(expectedMessage.getPayloadType(), actualMessage.getPayloadType());
        assertEquals(expectedMessage.getMetaData(), actualMessage.getMetaData());
        assertEquals(expectedMessage.getIdentifier(), actualMessage.getIdentifier());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(expected.enqueuedAt(), actual.enqueuedAt());
        assertEquals(expected.lastTouched(), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }
}
