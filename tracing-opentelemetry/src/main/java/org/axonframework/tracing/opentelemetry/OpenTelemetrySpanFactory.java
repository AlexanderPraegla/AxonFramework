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

package org.axonframework.tracing.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.axonframework.common.BuilderUtils;
import org.axonframework.messaging.Message;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.SpanFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import static org.axonframework.tracing.SpanUtils.determineMessageName;

/**
 * Creates {@link Span} implementations that are compatible with OpenTelemetry java agent instrumentation. OpenTelemetry
 * is a standard to collect logging, tracing and metrics from applications. This {@link SpanFactory} focuses on
 * supporting the tracing part of the standard.
 * <p>
 * To get started with OpenTelemetry, <a href="https://opentelemetry.io/docs/">check out their documentation</a>. Note
 * that, even after configuring the correct dependencies, you still need to run the application using the OpenTelemetry
 * java agent to export data. Without this, it will have the same effect as the
 * {@link org.axonframework.tracing.NoOpSpanFactory} since the data is not sent anywhere.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class OpenTelemetrySpanFactory implements SpanFactory {

    private static final TextMapPropagator PROPAGATOR = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

    private final Tracer tracer;
    private final List<SpanAttributesProvider> spanAttributesProviders;

    /**
     * Instantiate a {@link OpenTelemetrySpanFactory} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link OpenTelemetrySpanFactory} instance.
     */
    public OpenTelemetrySpanFactory(Builder builder) {
        this.spanAttributesProviders = builder.spanAttributesProviders;
        this.tracer = builder.tracer;
    }

    /**
     * Instantiate a Builder to create a {@link OpenTelemetrySpanFactory}.
     * <p>
     * The {@link SpanAttributesProvider SpanAttributeProvieders} are defaulted to an empty list, and the {@link Tracer}
     * is defaulted to the tracer defined by {@link GlobalOpenTelemetry}.
     *
     * @return a Builder able to create a {@link OpenTelemetrySpanFactory}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Message<?>> M propagateContext(M message) {
        HashMap<String, String> additionalMetadataProperties = new HashMap<>();
        PROPAGATOR.inject(Context.current(), additionalMetadataProperties, MetadataContextSetter.INSTANCE);
        return (M) message.andMetaData(additionalMetadataProperties);
    }

    @Override
    public Span createRootTrace(String operationName) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                                        .setSpanKind(SpanKind.INTERNAL);
        spanBuilder.addLink(io.opentelemetry.api.trace.Span.current().getSpanContext()).setNoParent();
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createHandlerSpan(String operationName, Message<?> parentMessage, boolean isChildTrace,
                                  Message<?>... linkedParents) {
        Context parentContext = PROPAGATOR.extract(Context.current(),
                                                   parentMessage,
                                                   MetadataContextGetter.INSTANCE);
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, parentMessage))
                                        .setSpanKind(SpanKind.CONSUMER);
        if (isChildTrace) {
            spanBuilder.setParent(parentContext);
        } else {
            spanBuilder.addLink(io.opentelemetry.api.trace.Span.fromContext(parentContext).getSpanContext())
                       .setNoParent();
        }
        addLinks(spanBuilder, linkedParents);
        addMessageAttributes(spanBuilder, parentMessage);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createDispatchSpan(String operationName, Message<?> parentMessage, Message<?>... linkedSiblings) {
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, parentMessage))
                                        .setSpanKind(SpanKind.PRODUCER);
        addLinks(spanBuilder, linkedSiblings);
        addMessageAttributes(spanBuilder, parentMessage);
        return new OpenTelemetrySpan(spanBuilder);
    }

    private void addLinks(SpanBuilder spanBuilder, Message<?>[] linkedMessages) {
        for (Message<?> message : linkedMessages) {
            Context linkedContext = PROPAGATOR.extract(Context.current(),
                                                       message,
                                                       MetadataContextGetter.INSTANCE);
            spanBuilder.addLink(io.opentelemetry.api.trace.Span.fromContext(linkedContext).getSpanContext());
        }
    }

    @Override
    public Span createInternalSpan(String operationName) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                                        .setSpanKind(SpanKind.INTERNAL);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public Span createInternalSpan(String operationName, Message<?> message) {
        SpanBuilder spanBuilder = tracer.spanBuilder(formatName(operationName, message))
                                        .setSpanKind(SpanKind.INTERNAL);
        addMessageAttributes(spanBuilder, message);
        return new OpenTelemetrySpan(spanBuilder);
    }

    @Override
    public void registerTagProvider(SpanAttributesProvider provider) {
        spanAttributesProviders.add(provider);
    }

    private String formatName(String operationName, Message<?> message) {
        if (message == null) {
            return operationName;
        }
        return String.format("%s(%s)",
                             operationName,
                             determineMessageName(message));
    }

    private void addMessageAttributes(SpanBuilder spanBuilder, Message<?> message) {
        if (message == null) {
            return;
        }
        spanAttributesProviders.forEach(supplier -> {
            Map<String, String> attributes = supplier.provideForMessage(message);
            attributes.forEach(spanBuilder::setAttribute);
        });
    }

    /**
     * Builder class to instantiate a {@link OpenTelemetrySpanFactory}.
     * <p>
     * The {@link SpanAttributesProvider SpanAttributeProvieders} are defaulted to an empty list, and the {@link Tracer}
     * is defaulted to the tracer defined by {@link GlobalOpenTelemetry}.
     */
    public static class Builder {

        private Tracer tracer = GlobalOpenTelemetry.getTracer("AxonFramework-OpenTelemetry");
        private final List<SpanAttributesProvider> spanAttributesProviders = new LinkedList<>();

        /**
         * Adds all provided {@link SpanAttributesProvider}s to the {@link SpanFactory}.
         *
         * @param attributesProviders The {@link SpanAttributesProvider}s to add.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder addSpanAttributeProviders(@Nonnull List<SpanAttributesProvider> attributesProviders) {
            BuilderUtils.assertNonNull(attributesProviders, "The attributesProviders should not be null");
            spanAttributesProviders.addAll(attributesProviders);
            return this;
        }

        /**
         * Defines the {@link Tracer} from OpenTelemetry to use.
         *
         * @param tracer The {@link Tracer} to configure for use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder tracer(@Nonnull Tracer tracer) {
            BuilderUtils.assertNonNull(tracer, "The Tracer should not be null");
            this.tracer = tracer;
            return this;
        }

        /**
         * Initializes the {@link OpenTelemetrySpanFactory}.
         *
         * @return The created {@link OpenTelemetrySpanFactory} with the provided configuration.
         */
        public OpenTelemetrySpanFactory build() {
            return new OpenTelemetrySpanFactory(this);
        }
    }
}
