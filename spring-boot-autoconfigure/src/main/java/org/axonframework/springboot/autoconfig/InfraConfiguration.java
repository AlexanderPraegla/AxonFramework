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

package org.axonframework.springboot.autoconfig;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.ModuleConfiguration;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.spring.config.MessageHandlerLookup;
import org.axonframework.spring.config.SpringAggregateLookup;
import org.axonframework.spring.config.SpringAxonConfiguration;
import org.axonframework.spring.config.SpringConfigurer;
import org.axonframework.spring.config.SpringSagaLookup;
import org.axonframework.spring.config.annotation.HandlerDefinitionFactoryBean;
import org.axonframework.spring.config.annotation.SpringParameterResolverFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;

import java.util.List;

@ConditionalOnClass(SpringConfigurer.class)
@AutoConfigureAfter({
        AxonAutoConfiguration.class,
        JpaAutoConfiguration.class,
        JpaEventStoreAutoConfiguration.class,
        NoOpTransactionAutoConfiguration.class,
        TransactionAutoConfiguration.class})
@Configuration
public class InfraConfiguration {

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static MessageHandlerLookup messageHandlerLookup() {
        return new MessageHandlerLookup();
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringAggregateLookup springAggregateLookup() {
        return new SpringAggregateLookup();
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringSagaLookup springSagaLookup() {
        return new SpringSagaLookup();
    }

    @Bean
    public SpringAxonConfiguration configuration(Configurer configurer) {
        return new SpringAxonConfiguration(configurer);
    }

    @Bean
    public SpringConfigurer springAxonConfigurer(ConfigurableListableBeanFactory beanFactory,
                                                 List<ConfigurerModule> configurerModules,
                                                 List<ModuleConfiguration> moduleConfigurations) {
        SpringConfigurer configurer = new SpringConfigurer(beanFactory);
        moduleConfigurations.forEach(configurer::registerModule);
        configurerModules.forEach(c -> c.configureModule(configurer));
        return configurer;
    }

    @Bean
    public QueryUpdateEmitter queryUpdateEmitter(org.axonframework.config.Configuration configuration) {
        return SimpleQueryUpdateEmitter.builder()
                                       .updateMessageMonitor(configuration.messageMonitor(QueryUpdateEmitter.class, "queryUpdateEmitter"))
                                       .build();
    }

    @Primary
    @Bean
    public HandlerDefinitionFactoryBean handlerDefinition(List<HandlerDefinition> handlerDefinitions,
                                                          List<HandlerEnhancerDefinition> handlerEnhancerDefinitions) {
        return new HandlerDefinitionFactoryBean(handlerDefinitions, handlerEnhancerDefinitions);
    }

    @Primary
    @Bean
    public SpringParameterResolverFactoryBean parameterResolverFactory(List<ParameterResolverFactory> parameterResolverFactories) {
        SpringParameterResolverFactoryBean springParameterResolverFactoryBean = new SpringParameterResolverFactoryBean();
        springParameterResolverFactoryBean.setAdditionalFactories(parameterResolverFactories);
        return springParameterResolverFactoryBean;
    }

    @ConditionalOnClass(CorrelationDataProvider.class)
    @Bean
    public ConfigurerModule correlationDataProvidersConfigurer(List<CorrelationDataProvider> correlationDataProviders) {
        return configurer -> configurer.configureCorrelationDataProviders(c -> correlationDataProviders);
    }

    @ConditionalOnClass(EventUpcaster.class)
    @Bean
    public ConfigurerModule eventUpcastersConfigurer(List<EventUpcaster> upcasters) {
        return configurer -> upcasters.forEach(u -> configurer.registerEventUpcaster(c -> u));
    }
}

