/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.Cause;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * @param <E> An implementation of {@link EventMessage} todo...
 * @author Steven van Beelen
 * @since 4.8.0
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public class DefaultDeadLetterStatementFactory<E extends EventMessage<?>> implements DeadLetterStatementFactory<E> {

    private final DeadLetterSchema schema;
    private final Serializer genericSerializer;
    private final Serializer eventSerializer;

    protected DefaultDeadLetterStatementFactory(Builder<E> builder) {
        builder.validate();
        this.schema = builder.schema;
        this.genericSerializer = builder.genericSerializer;
        this.eventSerializer = builder.eventSerializer;
    }

    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public PreparedStatement enqueueStatement(@Nonnull Connection connection,
                                              @Nonnull String processingGroup,
                                              @Nonnull String sequenceIdentifier,
                                              @Nonnull DeadLetter<? extends E> letter,
                                              long sequenceIndex) throws SQLException {
        String sql = "INSERT INTO " + schema.deadLetterTable() + " "
                + "(" + schema.deadLetterFields() + ") "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);
        AtomicInteger fieldIndex = new AtomicInteger(1);
        E eventMessage = letter.message();

        setIdFields(statement, fieldIndex, processingGroup, sequenceIdentifier, sequenceIndex);
        setEventFields(statement, fieldIndex, eventMessage);
        setDomainEventFields(statement, fieldIndex, eventMessage);
        setTrackedEventFields(statement, fieldIndex, eventMessage);
        setDeadLetterFields(statement, fieldIndex, letter);

        return statement;
    }

    private void setIdFields(PreparedStatement statement,
                             AtomicInteger fieldIndex,
                             String processingGroup,
                             String sequenceIdentifier,
                             long sequenceIndex) throws SQLException {
        String deadLetterId = IdentifierFactory.getInstance().generateIdentifier();
        statement.setString(fieldIndex.getAndIncrement(), deadLetterId);
        statement.setString(fieldIndex.getAndIncrement(), processingGroup);
        statement.setString(fieldIndex.getAndIncrement(), sequenceIdentifier);
        statement.setLong(fieldIndex.getAndIncrement(), sequenceIndex);
    }

    private void setEventFields(PreparedStatement statement,
                                AtomicInteger fieldIndex,
                                E eventMessage) throws SQLException {
        SerializedObject<byte[]> serializedPayload =
                eventSerializer.serialize(eventMessage.getPayload(), byte[].class);
        SerializedObject<byte[]> serializedMetaData =
                eventSerializer.serialize(eventMessage.getMetaData(), byte[].class);
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getClass().getName());
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getIdentifier());
        // TODO Timestamp converter!
        statement.setString(fieldIndex.getAndIncrement(), eventMessage.getTimestamp().toString());
        statement.setString(fieldIndex.getAndIncrement(), serializedPayload.getType().getName());
        statement.setString(fieldIndex.getAndIncrement(), serializedPayload.getType().getRevision());
        statement.setBytes(fieldIndex.getAndIncrement(), serializedPayload.getData());
        statement.setBytes(fieldIndex.getAndIncrement(), serializedMetaData.getData());
    }

    private void setDomainEventFields(PreparedStatement statement,
                                      AtomicInteger fieldIndex,
                                      EventMessage<?> eventMessage) throws SQLException {
        boolean isDomainEvent = eventMessage instanceof DomainEventMessage;
        setDomainEventFields(statement, fieldIndex, isDomainEvent ? (DomainEventMessage<?>) eventMessage : null);
    }

    private void setDomainEventFields(PreparedStatement statement,
                                      AtomicInteger fieldIndex,
                                      DomainEventMessage<?> eventMessage) throws SQLException {
        statement.setString(fieldIndex.getAndIncrement(),
                            getOrDefault(eventMessage, DomainEventMessage::getType, null));
        statement.setString(fieldIndex.getAndIncrement(),
                            getOrDefault(eventMessage, DomainEventMessage::getAggregateIdentifier, null));
        statement.setLong(fieldIndex.getAndIncrement(),
                          getOrDefault(eventMessage, DomainEventMessage::getSequenceNumber, -1L));
    }

    private void setTrackedEventFields(PreparedStatement statement,
                                       AtomicInteger fieldIndex,
                                       EventMessage<?> eventMessage) throws SQLException {
        boolean isTrackedEvent = eventMessage instanceof TrackedEventMessage;
        setTrackedEventFields(statement,
                              fieldIndex,
                              isTrackedEvent ? ((TrackedEventMessage<?>) eventMessage).trackingToken() : null);
    }

    private void setTrackedEventFields(PreparedStatement statement,
                                       AtomicInteger fieldIndex,
                                       TrackingToken token) throws SQLException {
        if (token != null) {
            SerializedObject<byte[]> serializedToken = genericSerializer.serialize(token, byte[].class);
            statement.setString(fieldIndex.getAndIncrement(), serializedToken.getType().getName());
            statement.setBytes(fieldIndex.getAndIncrement(), serializedToken.getData());
        } else {
            statement.setString(fieldIndex.getAndIncrement(), null);
            statement.setBytes(fieldIndex.getAndIncrement(), null);
        }
    }

    private void setDeadLetterFields(PreparedStatement statement,
                                     AtomicInteger fieldIndex,
                                     DeadLetter<? extends E> letter) throws SQLException {
        statement.setString(fieldIndex.getAndIncrement(), letter.enqueuedAt().toString());
        statement.setString(fieldIndex.getAndIncrement(), letter.lastTouched().toString());
        Optional<Cause> cause = letter.cause();
        statement.setString(fieldIndex.getAndIncrement(), cause.map(Cause::type).orElse(null));
        statement.setString(fieldIndex.getAndIncrement(), cause.map(Cause::message).orElse(null));
        SerializedObject<byte[]> serializedDiagnostics = eventSerializer.serialize(letter.diagnostics(), byte[].class);
        statement.setBytes(fieldIndex.getAndIncrement(), serializedDiagnostics.getData());
    }

    @Override
    public PreparedStatement evictStatement(@Nonnull Connection connection,
                                            @Nonnull String letterIdentifier) throws SQLException {
        String sql = "DELETE "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.deadLetterIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, letterIdentifier);
        return statement;
    }

    @Override
    public PreparedStatement requeueStatement(@Nonnull Connection connection,
                                              @Nonnull String letterIdentifier,
                                              Cause cause,
                                              @Nonnull Instant lastTouched,
                                              MetaData diagnostics) throws SQLException {
        String sql = "UPDATE " + schema.deadLetterTable() + " SET "
                + schema.causeTypeColumn() + "=?, "
                + schema.causeMessageColumn() + "=?, "
                + schema.lastTouchedColumn() + "=?, "
                + schema.diagnosticsColumn() + "=?, "
                + schema.processingStartedColumn() + "=NULL "
                + "WHERE " + schema.deadLetterIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, getOrDefault(cause, Cause::type, null));
        statement.setString(2, getOrDefault(cause, Cause::message, null));
        statement.setString(3, lastTouched.toString());
        SerializedObject<byte[]> serializedDiagnostics = eventSerializer.serialize(diagnostics, byte[].class);
        statement.setBytes(4, serializedDiagnostics.getData());
        statement.setString(5, letterIdentifier);
        return statement;
    }

    @Override
    public PreparedStatement containsStatement(@Nonnull Connection connection,
                                               @Nonnull String processingGroup,
                                               @Nonnull String sequenceId) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    @Override
    public PreparedStatement letterSequenceStatement(@Nonnull Connection connection,
                                                     @Nonnull String processingGroup,
                                                     @Nonnull String sequenceId,
                                                     int firstResult,
                                                     int maxSize) throws SQLException {
        String sql = "SELECT * "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=? "
                + "AND " + schema.sequenceIndexColumn() + ">=" + firstResult + " "
                + "LIMIT " + maxSize;

        PreparedStatement statement =
                connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    @Override
    public PreparedStatement sequenceIdentifiersStatement(@Nonnull Connection connection,
                                                          @Nonnull String processingGroup) throws SQLException {
        String sql = "SELECT dl." + schema.sequenceIdentifierColumn() + " "
                + "FROM " + schema.deadLetterTable() + " dl "
                + "WHERE dl." + schema.processingGroupColumn() + "=? "
                + "AND dl." + schema.sequenceIndexColumn() + "=("
                + "SELECT MIN(dl2." + schema.sequenceIndexColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " dl2 "
                + "WHERE dl2." + schema.processingGroupColumn() + "=dl." + schema.processingGroupColumn() + " "
                + "AND dl2." + schema.sequenceIdentifierColumn() + "=dl." + schema.sequenceIdentifierColumn() + ") "
                + "ORDER BY dl." + schema.lastTouchedColumn() + " "
                + "ASC";

        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement sizeStatement(@Nonnull Connection connection,
                                           @Nonnull String processingGroup) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement sequenceSizeStatement(@Nonnull Connection connection,
                                                   @Nonnull String processingGroup,
                                                   @Nonnull String sequenceId) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    @Override
    public PreparedStatement amountOfSequencesStatement(@Nonnull Connection connection,
                                                        @Nonnull String processingGroup) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT " + schema.sequenceIdentifierColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement clearStatement(@Nonnull Connection connection,
                                            @Nonnull String processingGroup) throws SQLException {
        String sql = "DELETE "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        return statement;
    }

    @Override
    public PreparedStatement maxIndexStatement(@Nonnull Connection connection,
                                               @Nonnull String processingGroup,
                                               @Nonnull String sequenceId) throws SQLException {
        String sql = "SELECT MAX(" + schema.sequenceIndexColumn() + ") "
                + "FROM " + schema.deadLetterTable() + " "
                + "WHERE " + schema.processingGroupColumn() + "=? "
                + "AND " + schema.sequenceIdentifierColumn() + "=?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, processingGroup);
        statement.setString(2, sequenceId);
        return statement;
    }

    protected static class Builder<M extends EventMessage<?>> {

        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private Serializer genericSerializer;
        private Serializer eventSerializer;

        /**
         * @param schema
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<M> schema(DeadLetterSchema schema) {
            assertNonNull(schema, "DeadLetterSchema may not be null");
            this.schema = schema;
            return this;
        }


        /**
         * Sets the {@link Serializer} to (de)serialize the {@link org.axonframework.eventhandling.TrackingToken} (if
         * present) of the event in the {@link DeadLetter} when storing it to the database.
         *
         * @param genericSerializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> genericSerializer(Serializer genericSerializer) {

            assertNonNull(genericSerializer, "The generic serializer may not be null");
            this.genericSerializer = genericSerializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the events, metadata and diagnostics of the {@link DeadLetter}
         * when storing it to a database.
         *
         * @param eventSerializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<M> eventSerializer(Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The event serializer may not be null");
            this.eventSerializer = eventSerializer;
            return this;
        }

        /**
         * Initializes a {@link DefaultDeadLetterStatementFactory} as specified through this Builder.
         *
         * @return A {@link DefaultDeadLetterStatementFactory} as specified through this Builder.
         */
        public DefaultDeadLetterStatementFactory<M> build() {
            return new DefaultDeadLetterStatementFactory<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(genericSerializer, "The generic Serializer is a hard requirement and should be provided");
            assertNonNull(eventSerializer, "The event Serializer is a hard requirement and should be provided");
        }
    }
}
