/*
 * Copyright (c) 2010-2024. AxonIQ
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
package org.axonframework.springboot.autoconfig;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.concurrent.ScheduledExecutorService;

import static io.axoniq.axonserver.connector.impl.ObjectUtils.nonNullOrDefault;

public class PersistentStreamMessageSourceRegistrar implements BeanDefinitionRegistryPostProcessor {

    private final AxonServerConfiguration axonServerConfiguration;
    private final ScheduledExecutorService scheduledExecutorService;

    public PersistentStreamMessageSourceRegistrar(Environment environment, ScheduledExecutorService scheduledExecutorService) {
        Binder binder = Binder.get(environment);
        this.axonServerConfiguration = binder.bind("axon.axonserver", Bindable.of(AxonServerConfiguration.class)).get();
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        axonServerConfiguration.getPersistentStreams()
                               .forEach((name, settings) -> {
                                   BeanDefinitionBuilder beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(
                                           PersistentStreamMessageSourceDefinition.class);
                                   beanDefinition.addConstructorArgValue(name);

                                   BeanDefinitionBuilder streamProperties = BeanDefinitionBuilder.genericBeanDefinition(
                                           PersistentStreamProperties.class);
                                   streamProperties.addConstructorArgValue(nonNullOrDefault(settings.getName(),name));
                                   streamProperties.addConstructorArgValue(settings.getInitialSegmentCount());
                                   streamProperties.addConstructorArgValue(settings.getSequencingPolicy());
                                   streamProperties.addConstructorArgValue(settings.getSequencingPolicyParameters());
                                   streamProperties.addConstructorArgValue(settings.getInitialPosition());
                                   streamProperties.addConstructorArgValue(settings.getFilter());
                                   beanDefinition.addConstructorArgValue(streamProperties.getBeanDefinition());
                                   beanDefinition.addConstructorArgValue(scheduledExecutorService);
                                   beanDefinition.addConstructorArgValue(settings.getBatchSize());
                                   beanDefinition.addConstructorArgValue(null);
                                   beanDefinitionRegistry.registerBeanDefinition(name, beanDefinition.getBeanDefinition());
                               });

    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory)
            throws BeansException {
        // no actions needed here
    }
}
