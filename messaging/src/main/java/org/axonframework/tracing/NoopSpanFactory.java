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

import org.axonframework.messaging.Message;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class NoopSpanFactory implements AxonSpanFactory {

    public static final NoopSpanFactory INSTANCE = new NoopSpanFactory();

    @Override
    public AxonSpan create(String operationName) {
        return new NoopAxonSpan();
    }

    @Override
    public AxonSpan create(String operationName, Message<?> messageForOperationName) {
        return new NoopAxonSpan();
    }

    @Override
    public void registerTagProvider(TagProvider supplier) {
        // Do nothing
    }

    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        return null;
    }

    static class NoopAxonSpan implements AxonSpan {

        @Override
        public AxonSpan withMessageAsParent(Message<?> message) {
            return this;
        }

        @Override
        public AxonSpan withSpanKind(AxonSpanKind spanKind) {
            return this;
        }

        @Override
        public AxonSpan withMessageAttributes(Message<?> message) {
            return this;
        }

        @Override
        public AxonSpan start() {
            return this;
        }

        @Override
        public void end() {
            // Do nothing
        }

        @Override
        public AxonSpan recordException(Throwable t) {
            return this;
        }

        @Override
        public <T> T wrap(Supplier<T> supplier) {
            return supplier.get();
        }

        @Override
        public void run(Runnable runnable) {
            runnable.run();
        }

        @Override
        public Runnable wrap(Runnable runnable) {
            return runnable;
        }

        @Override
        public <T> T wrapCallable(Callable<T> callable) throws Exception {
            return callable.call();
        }
    }
}
