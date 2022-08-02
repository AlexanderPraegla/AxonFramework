package org.axonframework.messaging.deadletter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;

import java.beans.ConstructorProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Generic implementation of the {@link DeadLetter} allowing any type of {@link Message} to be dead lettered.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class GenericDeadLetter<M extends Message<?>> implements DeadLetter<M> {

    private static final long serialVersionUID = 8392088448720827776L;

    /**
     * {@link Clock} instance used to set the {@link DeadLetter#enqueuedAt()} and {@link DeadLetter#lastTouched()} times
     * on {@link DeadLetter dead letters}. Can be adjusted to alter the desired time(zone) of those fields.
     */
    public static Clock clock = Clock.systemUTC();

    private final String identifier;
    private final SequenceIdentifier sequenceIdentifier;
    private final M message;
    private final Cause cause;
    private final Instant enqueuedAt;
    private final Instant lastTouched;
    private final MetaData diagnostics;

    /**
     * Construct a {@link GenericDeadLetter} with the given {@code sequenceIdentifier} and {@code message}. The
     * {@link #cause()} is left empty in this case. This method is typically used to construct a dead-letter that's part
     * of a sequence.
     *
     * @param sequenceIdentifier The {@link SequenceIdentifier} of the {@link GenericDeadLetter} to build.
     * @param message            The {@link Message} of type {@code M} of the {@link GenericDeadLetter} to build.
     */
    public GenericDeadLetter(SequenceIdentifier sequenceIdentifier, M message) {
        this(sequenceIdentifier, message, null);
    }

    /**
     * Construct a {@link GenericDeadLetter} with the given {@code sequenceIdentifier}, {@code message}, and
     * {@code cause}. This method is typically used to construct the first dead-letter entry for the given
     * {@code queueIdentifier}.
     *
     * @param sequenceIdentifier The {@link SequenceIdentifier} of the {@link GenericDeadLetter} to build.
     * @param message            The {@link Message} of type {@code M} of the {@link GenericDeadLetter} to build.
     * @param cause              The cause for the {@code message} to be dead-lettered.
     */
    public GenericDeadLetter(SequenceIdentifier sequenceIdentifier, M message, Throwable cause) {
        this(sequenceIdentifier,
             message,
             cause != null ? new ThrowableCause(cause) : null,
             () -> clock.instant());
    }

    private GenericDeadLetter(SequenceIdentifier sequenceIdentifier,
                              M message,
                              Cause cause,
                              Supplier<Instant> timeSupplier) {
        this(IdentifierFactory.getInstance().generateIdentifier(),
             sequenceIdentifier,
             message,
             cause,
             timeSupplier.get(),
             timeSupplier.get(),
             MetaData.emptyInstance());
    }

    private GenericDeadLetter(GenericDeadLetter<M> delegate, MetaData diagnostics) {
        this(delegate.identifier(),
             delegate.sequenceIdentifier(),
             delegate.message(),
             delegate.cause().orElse(null),
             delegate.enqueuedAt(),
             clock.instant(),
             diagnostics);
    }

    private GenericDeadLetter(GenericDeadLetter<M> delegate, Throwable requeueCause) {
        this(delegate.identifier(),
             delegate.sequenceIdentifier(),
             delegate.message(),
             requeueCause != null ? new ThrowableCause(requeueCause) : delegate.cause,
             delegate.enqueuedAt(),
             clock.instant(),
             delegate.diagnostic());
    }

    /**
     * Construct a {@link GenericDeadLetter} defining all the fields.
     *
     * @param identifier         The identifier of the {@link GenericDeadLetter}.
     * @param sequenceIdentifier The {@link SequenceIdentifier} of the {@link GenericDeadLetter} to build.
     * @param message            The {@link Message} of type {@code M} of the {@link GenericDeadLetter} to build.
     * @param cause              The cause for the {@code message} to be dead-lettered.
     * @param enqueuedAt         The moment this dead-letter is enqueued.
     * @param lastTouched        The last time this dead-letter was touched.
     * @param diagnostics        The diagnostic {@link MetaData} of this dead-letter.
     */
    @JsonCreator
    @ConstructorProperties({
            "identifier", "sequenceIdentifier", "message", "cause", "enqueuedAt", "lastTouched", "diagnostics"
    })
    public GenericDeadLetter(@JsonProperty("identifier") String identifier,
                             @JsonProperty("sequenceIdentifier") SequenceIdentifier sequenceIdentifier,
                             @JsonProperty("message") M message,
                             @JsonProperty("cause") Cause cause,
                             @JsonProperty("enqueuedAt") Instant enqueuedAt,
                             @JsonProperty("lastTouched") Instant lastTouched,
                             @JsonProperty("diagnostics") MetaData diagnostics) {
        this.identifier = identifier;
        this.sequenceIdentifier = sequenceIdentifier;
        this.message = message;
        this.cause = cause;
        this.enqueuedAt = enqueuedAt;
        this.lastTouched = lastTouched;
        this.diagnostics = diagnostics;
    }

    @JsonGetter
    @Override
    public String identifier() {
        return identifier;
    }

    @JsonGetter
    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
    @Override
    public SequenceIdentifier sequenceIdentifier() {
        return sequenceIdentifier;
    }

    @JsonGetter
    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
    @Override
    public M message() {
        return message;
    }

    @JsonGetter
    @Override
    public Optional<Cause> cause() {
        return Optional.ofNullable(cause);
    }

    @JsonGetter
    @Override
    public Instant enqueuedAt() {
        return enqueuedAt;
    }

    @JsonGetter
    @Override
    public Instant lastTouched() {
        return lastTouched;
    }

    @JsonGetter
    @Override
    public MetaData diagnostic() {
        return diagnostics;
    }

    @Override
    public DeadLetter<M> withCause(@Nonnull Throwable requeueCause) {
        return new GenericDeadLetter<>(this, requeueCause);
    }

    @Override
    public DeadLetter<M> andDiagnostics(MetaData diagnostics) {
        return new GenericDeadLetter<>(this, this.diagnostics.mergedWith(diagnostics));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GenericDeadLetter<?> that = (GenericDeadLetter<?>) o;
        return Objects.equals(identifier, that.identifier)
                && Objects.equals(sequenceIdentifier, that.sequenceIdentifier)
                && Objects.equals(message, that.message)
                && Objects.equals(cause, that.cause)
                && Objects.equals(enqueuedAt, that.enqueuedAt)
                && Objects.equals(lastTouched, that.lastTouched)
                && Objects.equals(diagnostics, that.diagnostics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, sequenceIdentifier, message, cause, enqueuedAt, lastTouched, diagnostics);
    }

    @Override
    public String toString() {
        return "GenericDeadLetter{" +
                "identifier='" + identifier + '\'' +
                ", sequenceIdentifier=" + sequenceIdentifier +
                ", message=" + message +
                ", cause=" + cause +
                ", enqueuedAt=" + enqueuedAt +
                ", lastTouched=" + lastTouched +
                ", diagnostics=" + diagnostics +
                '}';
    }
}
