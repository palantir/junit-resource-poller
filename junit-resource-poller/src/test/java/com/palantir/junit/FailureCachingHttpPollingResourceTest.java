/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.junit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public final class FailureCachingHttpPollingResourceTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Mock
    private HttpPollingResource delegate;

    private FailureCachingHttpPollingResource resource;

    @Before
    public void before() {
        resource = new FailureCachingHttpPollingResource(delegate);
    }

    @Test
    public void test_succeedsIfDelegateSucceeds() {
        resource.before();
    }

    @Test
    public void test_failsIfDelegateFails() {
        IllegalStateException delegateException = new IllegalStateException();
        doThrow(delegateException).when(delegate).before();

        expectedException.expect(IllegalStateException.class);

        resource.before();
    }

    @Test
    public void test_failsOnSubsequentCalls() {
        IllegalStateException delegateException = new IllegalStateException();
        doThrow(delegateException).when(delegate).before();

        try {
            resource.before();
        } catch (IllegalStateException e) {
            assertThat(e, is(equalTo(delegateException)));
        }

        // but resource should remember the failure
        try {
            resource.before();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is(equalTo("Failing due to previous error")));
            assertThat(e.getCause(), is(equalTo(delegateException)));
        }

        // make sure we only called the delegate once
        verify(delegate, times(1)).before();
    }

    @Test
    public void test_remembersFailureOfParallelRequests()
            throws InterruptedException, TimeoutException, BrokenBarrierException {
        CyclicBarrier delegateEntries = new CyclicBarrier(3);
        CyclicBarrier failingDelegate = new CyclicBarrier(2);
        CyclicBarrier successfulDelegate = new CyclicBarrier(2);

        IllegalStateException delegateException = new IllegalStateException();

        Answer<Void> failingAnswer = (inv) -> {
            delegateEntries.await();
            failingDelegate.await();
            throw delegateException;
        };

        Answer<Void> successfulAnswer = (inv) -> {
            delegateEntries.await();
            successfulDelegate.await();
            return null;
        };

        /* first invocation fails*/
        Mockito.doAnswer(failingAnswer)
                .doAnswer(successfulAnswer)
                .when(delegate).before();

        // run two parallel befores
        new Thread(() -> {
            try {
                resource.before();
            } catch (IllegalStateException e) { /* expected */ }
        }).start();

        new Thread(() -> {
            try {
                resource.before();
            } catch (IllegalStateException e) { /* expected */ }
        }).start();

        // wait up to 100 ms until both invocations entered the delegate
        delegateEntries.await(100, TimeUnit.MILLISECONDS);
        // then release the failure first, ensuring it 'loses' the race should there be a race
        failingDelegate.await();
        // lastly release the success
        successfulDelegate.await();

        // and ensure any following calls still fail
        expectedException.expect(IllegalStateException.class);

        resource.before();
    }
}
