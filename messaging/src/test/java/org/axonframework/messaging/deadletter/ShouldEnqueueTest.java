package org.axonframework.messaging.deadletter;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.deadletter.EventSequenceIdentifier;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating {@link ShouldEnqueue}. Either constructed through the constructors or through the
 * {@link Decisions} utility class.
 *
 * @author Steven van Beelen
 */
class ShouldEnqueueTest {

    private DeadLetter<EventMessage<String>> testLetter;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        testLetter = new GenericDeadLetter<>(
                new EventSequenceIdentifier("seqId", "group"),
                GenericEventMessage.asEventMessage("payload")
        );
    }

    @Test
    void testDefaultShouldEnqueue() {
        ShouldEnqueue<DeadLetter<? extends Message<?>>> testSubject = new ShouldEnqueue<>();

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void testDecisionsEnqueue() {
        ShouldEnqueue<DeadLetter<? extends Message<?>>> testSubject = new ShouldEnqueue<>();

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        assertFalse(testSubject.enqueueCause().isPresent());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void testShouldEnqueueWithCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<DeadLetter<? extends Message<?>>> testSubject = new ShouldEnqueue<>(testCause);

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void testDecisionsEnqueueWithCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<DeadLetter<? extends Message<?>>> testSubject = Decisions.enqueue(testCause);

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void testDecisionsRequeueWithCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<DeadLetter<? extends Message<?>>> testSubject = Decisions.requeue(testCause);

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter, result);
    }

    @Test
    void testShouldEnqueueWithCauseAndDiagnostics() {
        Throwable testCause = new RuntimeException("just because");
        MetaData testMetaData = MetaData.with("key", "value");

        ShouldEnqueue<DeadLetter<? extends Message<?>>> testSubject =
                new ShouldEnqueue<>(testCause, letter -> testMetaData);

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter.identifier(), result.identifier());
        assertEquals(testLetter.sequenceIdentifier(), result.sequenceIdentifier());
        assertEquals(testLetter.message(), result.message());
        assertEquals(testLetter.cause(), result.cause());
        assertEquals(testLetter.enqueuedAt(), result.enqueuedAt());
        assertEquals(testLetter.lastTouched(), result.lastTouched());
        assertEquals(testMetaData, result.diagnostic());
    }

    @Test
    void testDecisionsRequeueWithCauseAndDiagnostics() {
        Throwable testCause = new RuntimeException("just because");
        MetaData testMetaData = MetaData.with("key", "value");

        ShouldEnqueue<DeadLetter<? extends Message<?>>> testSubject =
                Decisions.requeue(testCause, letter -> testMetaData);

        assertFalse(testSubject.shouldEvict());
        assertTrue(testSubject.shouldEnqueue());
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertTrue(resultCause.isPresent());
        assertEquals(testCause, resultCause.get());

        DeadLetter<? extends Message<?>> result = testSubject.addDiagnostics(testLetter);
        assertEquals(testLetter.identifier(), result.identifier());
        assertEquals(testLetter.sequenceIdentifier(), result.sequenceIdentifier());
        assertEquals(testLetter.message(), result.message());
        assertEquals(testLetter.cause(), result.cause());
        assertEquals(testLetter.enqueuedAt(), result.enqueuedAt());
        assertEquals(testLetter.lastTouched(), result.lastTouched());
        assertEquals(testMetaData, result.diagnostic());
    }
}