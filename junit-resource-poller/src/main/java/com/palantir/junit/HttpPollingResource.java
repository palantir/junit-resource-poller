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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.rules.ExternalResource;

/**
 * A JUnit resource representing a list of remote services that can be polled for availability through a URL.
 */
public final class HttpPollingResource extends ExternalResource implements PollableResource {

    private static final int CONNECTION_TIMEOUT_MILLIS = 500;
    private static final int READ_TIMEOUT_MILLIS = 500;

    private final OkHttpClient client;
    private final List<Request> pollRequests;
    private final int numAttempts;
    private final long intervalMillis;

    public static final class SslParameters {
        private final SSLSocketFactory sslSocketFactory;
        private final Optional<X509TrustManager> x509TrustManager;

        private SslParameters(SSLSocketFactory sslSocketFactory, Optional<X509TrustManager> x509TrustManager) {
            this.sslSocketFactory = sslSocketFactory;
            this.x509TrustManager = x509TrustManager;
        }

        private static SslParameters of(SSLSocketFactory sslSocketFactory) {
            return new SslParameters(sslSocketFactory, Optional.empty());
        }

        public static SslParameters of(SSLSocketFactory socketFactory, X509TrustManager x509TrustManager) {
            return new SslParameters(socketFactory, Optional.of(x509TrustManager));
        }
    }

    /** Waits at most {@code timeout} until a GET request for {@code pollUrl} succeeds, polls every 100 milliseconds. */
    public static HttpPollingResource create(
            Optional<SslParameters> sslParameters, String pollUrl, int numAttempts) {
        return new HttpPollingResource(sslParameters, Collections.singletonList(pollUrl), numAttempts, 100,
                CONNECTION_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    /**
     * Like {@link HttpPollingResource#create(Optional, String, int)}, but waits for all of the given URLs.
     */
    public static HttpPollingResource create(
            Optional<SslParameters> sslParameters, Collection<String> pollUrls, int numAttempts) {
        return new HttpPollingResource(sslParameters, pollUrls, numAttempts, 100,
                CONNECTION_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    /**
     * Waits at most {@code timeout} until a GET request for {@code pollUrl} succeeds, polls every 100 milliseconds.
     *
     * @deprecated Use com.palantir.junit.HttpPollingResource#create(java.util.Optional, java.util.Collection, int)
     */
    @Deprecated
    public static HttpPollingResource of(
            Optional<SSLSocketFactory> sslSocketFactory, String pollUrl, int numAttempts) {
        return new HttpPollingResource(
                sslSocketFactory.map(SslParameters::of),
                Collections.singletonList(pollUrl),
                numAttempts,
                100,
                CONNECTION_TIMEOUT_MILLIS,
                READ_TIMEOUT_MILLIS);
    }

    /**
     * Like {@link HttpPollingResource#of(Optional, String, int)}, but waits for all of the given URLs.
     *
     * @deprecated Use com.palantir.junit.HttpPollingResource#create(java.util.Optional, java.util.Collection, int)
     */
    @Deprecated
    public static HttpPollingResource of(
            Optional<SSLSocketFactory> sslSocketFactory, Collection<String> pollUrls, int numAttempts) {
        return new HttpPollingResource(
                sslSocketFactory.map(SslParameters::of),
                pollUrls,
                numAttempts,
                100,
                CONNECTION_TIMEOUT_MILLIS,
                READ_TIMEOUT_MILLIS);
    }

    public HttpPollingResource(
            Optional<SslParameters> sslParameters,
            Collection<String> pollRequests,
            int numAttempts,
            long intervalMillis,
            int connectionTimeoutMillis,
            int readTimeoutMillis) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        clientBuilder.connectTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS);
        clientBuilder.readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS);
        sslParameters.ifPresent(ssl -> {
            if (ssl.x509TrustManager.isPresent()) {
                clientBuilder.sslSocketFactory(ssl.sslSocketFactory, ssl.x509TrustManager.get());
            } else {
                clientBuilder.sslSocketFactory(ssl.sslSocketFactory);
            }
        });
        this.client = clientBuilder.build();
        this.pollRequests = pollRequests.stream().map(url -> {
            try {
                return new Request.Builder().url(new URL(url)).build();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        this.numAttempts = numAttempts;
        this.intervalMillis = intervalMillis;
    }

    @Override
    public Optional<Exception> isReady() {
        for (Request request : pollRequests) {
            try {
                Response response = client.newCall(request).execute();
                response.body().close();
                if (!response.isSuccessful()) {
                    return Optional.of(new RuntimeException(String.format(
                            "Received non-success error code %s from resource %s", response.code(), request.url())));
                }
            } catch (IOException e) {
                return Optional.of(new RuntimeException(
                        "HTTP connection error for resource " + request.url(), e));
            }
        }
        return Optional.empty();
    }

    @Override
    protected void before() {
        try {
            ResourcePoller.poll(numAttempts, intervalMillis, this);
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                    "HTTP services was not ready within %d milliseconds: %s",
                    numAttempts * intervalMillis,
                    pollRequests.stream().map(Request::url).collect(Collectors.toList())), e);
        }
    }
}
