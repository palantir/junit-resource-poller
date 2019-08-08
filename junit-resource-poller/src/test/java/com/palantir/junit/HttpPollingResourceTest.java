/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class HttpPollingResourceTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public MockWebServer server = new MockWebServer();

    private HttpPollingResource poller;

    @Before
    public void before() {
        poller = HttpPollingResource.of(Optional.empty(), "http://localhost:" + server.getPort(), 5);
    }


    @Test
    public void test_failsEventually() throws IOException {
        server.shutdown();
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("HTTP services was not ready within 500 milliseconds");
        poller.before();
    }

    @Test
    public void test_succeedsIfResourceBecomesAvailableInTime() throws IOException, InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(200));
        poller.before();
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), greaterThan(200L));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), lessThan(500L));
    }

    @Test
    public void test_connectionsTimeOutQuickly() throws IOException, InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            poller.before();
        } catch (IllegalStateException e) { /* expected */ }

        assertThat(server.getRequestCount(), is(5));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), lessThan(6 * 500L + 500));
    }

    @Test
    public void test_pollsAreServices() throws IOException, InterruptedException {
        MockWebServer server2 = new MockWebServer();
        server2.start();
        HttpPollingResource doublePoller = HttpPollingResource.of(Optional.empty(),
                ImmutableList.of("http://localhost:" + server.getPort(), "http://localhost:" + server2.getPort()), 2);

        server.enqueue(new MockResponse().setResponseCode(500));
        // server2 won't get called in the first iteration
        server.enqueue(new MockResponse().setResponseCode(200));
        server2.enqueue(new MockResponse().setResponseCode(200));

        doublePoller.before();
        assertThat(server.getRequestCount(), is(2));
        assertThat(server2.getRequestCount(), is(1));
    }

    @Test
    public void junit5_sanity_test() throws IOException, InterruptedException {
        MockWebServer server2 = new MockWebServer();
        server2.start();

        HttpPollingExtension junit5 = HttpPollingExtension.builder()
                .pollUrls(ImmutableList.of(
                        "http://localhost:" + server.getPort(),
                        "http://localhost:" + server2.getPort()))
                .numAttempts(2)
                .build();

        server.enqueue(new MockResponse().setResponseCode(500));
        // server2 won't get called in the first iteration
        server.enqueue(new MockResponse().setResponseCode(200));
        server2.enqueue(new MockResponse().setResponseCode(200));

        junit5.beforeAll(null);
        assertThat(server.getRequestCount(), is(2));
        assertThat(server2.getRequestCount(), is(1));
    }
}
