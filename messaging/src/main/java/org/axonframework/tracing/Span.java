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

package org.axonframework.tracing;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Represents a part of the application logic that will be traced. One or multiple spans together form a trace and are
 * often used to debug and monitor (distributed) applications.
 * <p>
 * The {@link Span} is an abstraction for Axon Framework to have tracing capabilities without knowing the specific
 * tracing provider.
 * <p>
 * Creating {@link Span spans} is the responsibility of the {@link SpanFactory} which should be implemented by the
 * tracing provider of choice.
 *
 * @author Mitchell Herrijgers
 * @see SpanFactory For more information about creating different kinds of traces.
 * @since 4.6.0
 */
public interface Span {

    /**
     * Starts the Span. This means setting this span to the current, taking over from any active span currently present
     * if so.
     *
     * @return The span for fluent interfacing.
     */
    Span start();

    /**
     * Ends the span. It restores the original context, often the parent trace, as the current context. No operation on
     * the span is possible after this method.
     */
    void end();

    /**
     * Records an exception to the span. This will be reported to the APM tooling, which can show more information about
     * the error in the trace. This method does not end the span.
     *
     * @param t The exception to record
     * @return The span for fluent interfacing.
     */
    Span recordException(Throwable t);

    /**
     * Runs a piece of code which will be traced. Exceptions will be caught automatically and added to the span, then
     * rethrown. The span will be started before the execution, and ended after execution. Note that the
     * {@link Runnable} will be invoked instantly and synchronously.
     *
     * @param runnable The {@link Runnable} to execute.
     */
    default void run(Runnable runnable) {
        wrapRunnable(runnable).run();
    }

    /**
     * Wraps a {@link Runnable}, propagating the current span context to the actual thread that runs the
     * {@link Runnable}. If you don't wrap a runnable before passing it to an {@link java.util.concurrent.Executor} the
     * context will be lost and a new trace will be started.
     *
     * @param runnable The {@link Runnable} to wrap
     * @return A wrapped runnable which propagates the span's context across threads.
     */
    Runnable wrapRunnable(Runnable runnable);

    /**
     * Runs a piece of code which will be traced. Exceptions will be caught automatically and added to the span, then
     * rethrown. The span will be started before the execution, and ended after execution. Note that the
     * {@link Callable} will be invoked instantly and synchronously.
     *
     * @param callable The {@link Callable} to execute.
     */
    default <T> T runCallable(Callable<T> callable) throws Exception {
        return wrapCallable(callable).call();
    }

    /**
     * Wraps a {@link Callable}, propagating the current span context to the actual thread that runs the
     * {@link Callable}. If you don't wrap a callable before passing it to an {@link java.util.concurrent.Executor} the
     * context will be lost and a new trace will be started.
     *
     * @param callable The {@link Callable} to wrap
     * @return A wrapped callable which propagates the span's context across threads.
     */
    <T> Callable<T> wrapCallable(Callable<T> callable);

    /**
     * Runs a piece of code that returns a value and which will be traced. Exceptions will be caught automatically and
     * added to the span, then rethrown. The span will be started before the execution, and ended after execution. Note
     * that the {@link Supplier} will be invoked instantly and synchronously.
     *
     * @param supplier The {@link Supplier} to execute.
     */
    <T> T runSupplier(Supplier<T> supplier);

    /**
     * Starts the span and adds a completion stage to the provided {@link CompletableFuture}. The {@link Span} will
     * automatically end when the provided {@code future} is completed, registering an exception if any occurred.
     *
     * @param future The {@link CompletableFuture} to instrument.
     * @param <T> The result type of the {@code future}.
     * @return The instrumented {@link CompletableFuture}
     */
    default <T> CompletableFuture<T> startForFuture(CompletableFuture<T> future) {
        this.start();
        return future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                this.recordException(throwable);
            }
            this.end();
        });
    }
}
