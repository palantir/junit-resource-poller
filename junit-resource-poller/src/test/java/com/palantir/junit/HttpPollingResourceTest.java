/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
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
        poller = HttpPollingResource.of(Optional.<SSLSocketFactory>absent(), "http://localhost:" + server.getPort(), 5);
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
        assertTrue(stopwatch.elapsed(TimeUnit.MILLISECONDS) > 200);
        assertTrue(stopwatch.elapsed(TimeUnit.MILLISECONDS) < 500);
    }
}
