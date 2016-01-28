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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import org.junit.rules.ExternalResource;

/**
 * A JUnit resource representing a list of remote services that can be polled for availability through a URL.
 */
public final class HttpPollingResource extends ExternalResource implements PollableResource {

    private final OkHttpClient client;
    private final ImmutableList<Request> pollRequests;
    private final long timeoutMillis;
    private final long intervalMillis;

    /** Waits at most {@code timeout} until a GET request for {@code pollUrl} succeeds, polls every 100 milliseconds. */
    public static HttpPollingResource of(
            Optional<SSLSocketFactory> socketFactory, String pollUrl, long timeout, TimeUnit timeUnit) {
        return new HttpPollingResource(socketFactory, ImmutableList.of(pollUrl), timeUnit.toMillis(timeout), 100);
    }

    /**
     * Like {@link HttpPollingResource#of(Optional, String, long, TimeUnit)}, but waits for all of the given URLs.
     */
    public static HttpPollingResource of(
            Optional<SSLSocketFactory> socketFactory, Collection<String> pollUrls, long timeout, TimeUnit timeUnit) {
        return new HttpPollingResource(socketFactory, pollUrls, timeUnit.toMillis(timeout), 100);
    }

    public HttpPollingResource(Optional<SSLSocketFactory> socketFactory, Collection<String> pollRequests,
            long timeoutMillis, long intervalMillis) {
        this.client = new OkHttpClient();
        if (socketFactory.isPresent()) {
            this.client.setSslSocketFactory(socketFactory.get());
        }
        ImmutableList.Builder<Request> urls = ImmutableList.builder();
        try {
            for (String url : pollRequests) {
                urls.add(new Request.Builder().url(new URL(url)).build());
            }
        } catch (MalformedURLException e) {
            throw Throwables.propagate(e);
        }
        this.pollRequests = urls.build();
        this.timeoutMillis = timeoutMillis;
        this.intervalMillis = intervalMillis;
    }

    @Override
    public Optional<Exception> isReady() {
        for (Request request : pollRequests) {
            try {
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    return Optional.of((Exception) new RuntimeException(String.format(
                            "Received non-success error code %s from resource %s", response.code(), request.url())));
                }
            } catch (IOException e) {
                return Optional.of((Exception) new RuntimeException(
                        "HTTP connection error for resource " + request.url(), e));
            }
        }
        return Optional.absent();
    }

    @Override
    protected void before() {
        try {
            ResourcePoller.poll(timeoutMillis, intervalMillis, this);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("HTTP services was not ready within %d milliseconds: %s",
                    timeoutMillis, urlsFromRequests(pollRequests)), e);
        }
    }

    private static List<URL> urlsFromRequests(ImmutableList<Request> pollRequests) {
        return Lists.transform(pollRequests, new Function<Request, URL>() {
            @Override
            public URL apply(Request input) {
                return input.url();
            }
        });
    }
}
