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

package org.axonframework.messaging.deadletter;

import org.axonframework.common.AxonException;

/**
 * Exception signaling a {@link DeadLetterQueue} is overflowing.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetterQueueOverflowException extends AxonException {

    /**
     * Constructs an exception based on the given {@code message}.
     *
     * @param message The description of this {@link DeadLetterQueueOverflowException}.
     */
    public DeadLetterQueueOverflowException(String message) {
        super(message);
    }

    /**
     * Constructs an exception based on the given {@code message} and {@code cause}.
     *
     * @param message The description of this {@link DeadLetterQueueOverflowException}.
     * @param cause   The reason for this exception to be constructed.
     */
    public DeadLetterQueueOverflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
