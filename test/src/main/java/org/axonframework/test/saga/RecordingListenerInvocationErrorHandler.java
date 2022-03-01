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

package org.axonframework.test.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;

import java.util.Optional;

/**
 * A wrapper around a {@link ListenerInvocationErrorHandler} that in itself also implements {@link ListenerInvocationErrorHandler}. Any Exception encountered
 * will be stored, after which the rest of the error handling will be handed off to the wrapped ListenerInvocationErrorHandler.
 *
 * @author Christian Vermorken
 */
public class RecordingListenerInvocationErrorHandler implements ListenerInvocationErrorHandler {

    private ListenerInvocationErrorHandler listenerInvocationErrorHandler;

    private Exception exception;

    public RecordingListenerInvocationErrorHandler(ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        this.listenerInvocationErrorHandler = listenerInvocationErrorHandler;
    }

    @Override
    public void onError(Exception exception, EventMessage<?> event, EventMessageHandler eventHandler) throws Exception {
        this.exception = exception;
        listenerInvocationErrorHandler.onError(exception, event, eventHandler);
    }

    /**
     * Clear any current Exception.
     */
    public void startRecording() {
        exception = null;
    }

    public void setListenerInvocationErrorHandler(ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
        this.listenerInvocationErrorHandler = listenerInvocationErrorHandler;
    }

    /**
     * Return the last encountered Exception after the startRecording method has been invoked, or an empty Optional of no Exception occurred.
     *
     * @return an Optional of the last encountered Exception
     */
    public Optional<Exception> getException() {
        return Optional.ofNullable(exception);
    }
}
