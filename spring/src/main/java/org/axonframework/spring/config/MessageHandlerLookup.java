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

package org.axonframework.spring.config;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * A {@link BeanDefinitionRegistryPostProcessor} implementation that detects beans with Axon Message handlers and
 * registers an {@link MessageHandlerConfigurer} to have these handlers registered in the Axon {@link
 * org.axonframework.config.Configuration}.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class MessageHandlerLookup implements BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerLookup.class);

    /**
     * Returns a list of beans found in the given {@code register} that contain a handler for the given {@code
     * messageType}.
     *
     * @param messageType The type of message to find handlers for.
     * @param registry    The registry to find these handlers in.
     * @return A list of bean names with message handlers.
     */
    public static List<String> messageHandlerBeans(Class<? extends Message<?>> messageType,
                                                   ConfigurableListableBeanFactory registry) {
        List<String> found = new ArrayList<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(beanName);
            if (bd.isSingleton() && !bd.isAbstract()) {
                Class<?> beanType = registry.getType(beanName);
                if (beanType != null && hasMessageHandler(messageType, beanType)) {
                    found.add(beanName);
                }
            }
        }
        return found;
    }

    private static boolean hasMessageHandler(Class<? extends Message<?>> messageType, Class<?> beanType) {
        for (Method m : ReflectionUtils.methodsOf(beanType)) {
            Optional<Map<String, Object>> attr = AnnotationUtils.findAnnotationAttributes(m, MessageHandler.class);
            if (attr.isPresent() && messageType.isAssignableFrom((Class<?>) attr.get().get("messageType"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry)) {
            logger.warn("Given bean factory is not a BeanDefinitionRegistry. Cannot auto-configure message handlers");
            return;
        }

        for (MessageHandlerConfigurer.Type value : MessageHandlerConfigurer.Type.values()) {
            String configurerBeanName = "MessageHandlerConfigurer$$Axon$$" + value.name();
            if (beanFactory.containsBeanDefinition(configurerBeanName)) {
                logger.info("Message handler configurer already available. Skipping configuration");
                break;
            }

            List<String> found = messageHandlerBeans(value.getMessageType(), beanFactory);
            if (!found.isEmpty()) {
                AbstractBeanDefinition beanDefinition =
                        BeanDefinitionBuilder.genericBeanDefinition(MessageHandlerConfigurer.class)
                                             .addConstructorArgValue(value.name())
                                             .addConstructorArgValue(found)
                                             .getBeanDefinition();

                ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(configurerBeanName,
                                                                           beanDefinition);
            }
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        // No action required.
    }
}
