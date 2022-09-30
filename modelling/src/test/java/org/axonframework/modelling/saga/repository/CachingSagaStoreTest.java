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

package org.axonframework.modelling.saga.repository;

import org.axonframework.common.caching.Cache;
import org.axonframework.modelling.saga.AssociationValue;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Abstract test class for validating the {@link CachingSagaStore}. Expects implementations to construct the type of
 * {@link Cache} used during testing.
 *
 * @author Allard Buijze
 */
public abstract class CachingSagaStoreTest {

    private SagaStore<StubSaga> delegate;
    private Cache sagaCache;
    private Cache associationsCache;

    private CachingSagaStore<StubSaga> testSubject;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        //noinspection rawtypes
        delegate = spy((SagaStore) new InMemorySagaStore());
        sagaCache = spy(sagaCache());
        associationsCache = spy(associationCache());

        testSubject = CachingSagaStore.<StubSaga>builder()
                                      .delegateSagaStore(delegate)
                                      .sagaCache(sagaCache)
                                      .associationsCache(associationsCache)
                                      .build();
    }

    /**
     * Retrieve the saga {@link Cache} used for testing.
     *
     * @return The saga {@link Cache} used for testing.
     */
    abstract Cache sagaCache();

    /**
     * Retrieve the association value entry {@link Cache} used for testing.
     *
     * @return The association value entry {@link Cache} used for testing.
     */
    abstract Cache associationCache();

    private void clearCaches() {
        sagaCache.removeAll();
        associationsCache.removeAll();
    }

    @Test
    void sagaAddedToCacheOnAdd() {
        testSubject.insertSaga(StubSaga.class, "123", new StubSaga(), singleton(new AssociationValue("key", "value")));

        verify(sagaCache).put(eq("123"), any());
        verify(associationsCache, never()).put(any(), any());
    }

    @Test
    void associationsAddedToCacheOnLoad() {
        testSubject.insertSaga(StubSaga.class, "id", new StubSaga(), singleton(new AssociationValue("key", "value")));

        verify(associationsCache, never()).put(any(), any());

        clearCaches();
        reset(sagaCache, associationsCache);

        final AssociationValue associationValue = new AssociationValue("key", "value");

        Set<String> actual = testSubject.findSagas(StubSaga.class, associationValue);
        assertEquals(singleton("id"), actual);
        verify(associationsCache, atLeast(1)).get("org.axonframework.modelling.saga.repository.StubSaga/key=value");
        verify(associationsCache).putIfAbsent("org.axonframework.modelling.saga.repository.StubSaga/key=value",
                                              Collections.singleton("id"));
    }

    @Test
    void sagaAddedToCacheOnLoad() {
        StubSaga saga = new StubSaga();
        testSubject.insertSaga(StubSaga.class, "id", saga, singleton(new AssociationValue("key", "value")));

        clearCaches();
        reset(sagaCache, associationsCache);

        SagaStore.Entry<StubSaga> actual = testSubject.loadSaga(StubSaga.class, "id");
        assertSame(saga, actual.saga());

        verify(sagaCache).get("id");
        verify(sagaCache).put(eq("id"), any());
        verify(associationsCache, never()).put(any(), any());
    }

    @Test
    void sagaNotAddedToCacheWhenLoadReturnsNull() {
        clearCaches();
        reset(sagaCache, associationsCache);

        SagaStore.Entry<StubSaga> actual = testSubject.loadSaga(StubSaga.class, "id");
        assertNull(actual);

        verify(sagaCache).get("id");
        verify(sagaCache, never()).put(eq("id"), any());
        verify(associationsCache, never()).put(any(), any());
    }


    @Test
    void commitDelegatedAfterAddingToCache() {
        StubSaga saga = new StubSaga();
        AssociationValue associationValue = new AssociationValue("key", "value");
        testSubject.insertSaga(StubSaga.class, "123", saga, singleton(associationValue));

        verify(associationsCache, never()).put(any(), any());
        verify(delegate).insertSaga(StubSaga.class, "123", saga, singleton(associationValue));
    }

    @Test
    void sagaAndAssociationsRemovedFromCacheOnDelete() {
        String testSagaId = "123";
        AssociationValue testAssociationValue = new AssociationValue("key", "value");
        String expectedAssociationKey = "org.axonframework.modelling.saga.repository.StubSaga/key=value";

        testSubject.insertSaga(StubSaga.class, testSagaId, new StubSaga(), singleton(testAssociationValue));
        assertTrue(sagaCache.containsKey(testSagaId));

        testSubject.findSagas(StubSaga.class, testAssociationValue);
        assertTrue(associationsCache.containsKey(expectedAssociationKey));

        testSubject.deleteSaga(StubSaga.class, testSagaId, singleton(testAssociationValue));
        assertFalse(sagaCache.containsKey(testSagaId));
        assertFalse(associationsCache.containsKey(expectedAssociationKey));
    }
}
