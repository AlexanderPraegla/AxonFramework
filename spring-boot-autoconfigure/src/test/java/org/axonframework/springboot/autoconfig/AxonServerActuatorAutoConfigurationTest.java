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

import org.axonframework.actuator.AxonServerHealthIndicator;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link AxonServerActuatorAutoConfiguration}.
 *
 * @author Steven van Beelen
 */
class AxonServerActuatorAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner();
    }

    @Test
    void testAxonServerHealthIndicatorIsNotCreatedForAxonServerDisabled() {
        testApplicationContext.withUserConfiguration(TestContext.class)
                              .withPropertyValues("axon.axonserver.enabled:false")
                              .run(context -> assertThat(context).doesNotHaveBean(AxonServerHealthIndicator.class));
    }

    @Test
    void testAxonServerHealthIndicatorIsCreated() {
        testApplicationContext.withUserConfiguration(TestContext.class)
                              .withPropertyValues("axon.axonserver.enabled:true")
                              .run(context -> assertThat(context).hasSingleBean(AxonServerHealthIndicator.class));
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContext {

    }
}