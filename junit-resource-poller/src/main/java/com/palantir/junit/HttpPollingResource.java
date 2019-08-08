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
import java.util.HashSet;
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
 * A JUnit4 resource representing a list of remote services that can be polled for availability through a URL.
 */
public final class HttpPollingResource extends ExternalResource implements PollableResource {

    private static final int CONNECTION_TIMEOUT_MILLIS = 500;
    private static final int READ_TIMEOUT_MILLIS = 500;

    private final OkHttpClient client;
    private final List<Request> pollRequests;
    private final int numAttempts;
    private final long intervalMillis;

    public static HttpPollingResource.Builder builder() {
        return new Builder();
    }

    /** Waits at most {@code timeout} until a GET request for {@code pollUrl} succeeds, polls every 100 milliseconds. */
    public static HttpPollingResource create(
            Optional<SslParameters> sslParameters, String pollUrl, int numAttempts) {
        return builder()
                .sslParameters(sslParameters)
                .pollUrls(Collections.singletonList(pollUrl))
                .numAttempts(numAttempts)
                .build();
    }

    /**
     * Like {@link HttpPollingResource#create(Optional, String, int)}, but waits for all of the given URLs.
     */
    public static HttpPollingResource create(
            Optional<SslParameters> sslParameters, Collection<String> pollUrls, int numAttempts) {
        return builder()
                .sslParameters(sslParameters)
                .pollUrls(pollUrls)
                .numAttempts(numAttempts)
                .build();
    }

    /**
     * Like {@link HttpPollingResource#create(Optional, Collection, int)}, but additionally set timeouts and polling
     * interval.
     */
    public static HttpPollingResource create(
            Optional<SSLSocketFactory> sslSocketFactory,
            Collection<String> pollUrls,
            int numAttempts,
            long intervalMillis,
            int connectionTimeoutMillis,
            int readTimeoutMillis) {
        Builder builder = builder();
        sslSocketFactory.ifPresent(builder::sslSocketFactory);

        return builder
                .pollUrls(pollUrls)
                .numAttempts(numAttempts)
                .intervalMillis(intervalMillis)
                .connectionTimeoutMillis(connectionTimeoutMillis)
                .readTimeoutMillis(readTimeoutMillis)
                .build();
    }

    /**
     * Waits at most {@code timeout} until a GET request for {@code pollUrl} succeeds, polls every 100 milliseconds.
     *
     * @deprecated Use {@link #builder()}
     */
    @Deprecated
    public static HttpPollingResource of(
            Optional<SSLSocketFactory> sslSocketFactory, String pollUrl, int numAttempts) {
        Builder builder = builder();
        sslSocketFactory.ifPresent(builder::sslSocketFactory);

        return builder()
                .pollUrls(Collections.singletonList(pollUrl))
                .numAttempts(numAttempts)
                .build();
    }

    /**
     * Like {@link HttpPollingResource#of(Optional, String, int)}, but waits for all of the given URLs.
     *
     * @deprecated Use {@link #builder()}
     */
    @Deprecated
    public static HttpPollingResource of(
            Optional<SSLSocketFactory> sslSocketFactory, Collection<String> pollUrls, int numAttempts) {
        Builder builder = builder();
        sslSocketFactory.ifPresent(builder::sslSocketFactory);

        return builder()
                .pollUrls(pollUrls)
                .numAttempts(numAttempts)
                .build();
    }

    /**
     * @deprecated Use {@link #builder()}
     */
    @Deprecated
    public HttpPollingResource(Optional<SSLSocketFactory> socketFactory, Collection<String> pollRequests,
            int numAttempts, long intervalMillis, int connectionTimeoutMillis, int readTimeoutMillis) {
        this(
                socketFactory,
                Optional.empty(),
                pollRequests,
                numAttempts,
                intervalMillis,
                connectionTimeoutMillis,
                readTimeoutMillis);
    }

    private HttpPollingResource(
            Optional<SSLSocketFactory> sslSocketFactory,
            Optional<X509TrustManager> x509TrustManager,
            Collection<String> pollRequests,
            int numAttempts,
            long intervalMillis,
            int connectionTimeoutMillis,
            int readTimeoutMillis) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        clientBuilder.connectTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS);
        clientBuilder.readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS);
        if (sslSocketFactory.isPresent()) {
            if (x509TrustManager.isPresent()) {
                clientBuilder.sslSocketFactory(sslSocketFactory.get(), x509TrustManager.get());
            } else {
                clientBuilder.sslSocketFactory(sslSocketFactory.get());
            }
        }
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

    public static final class SslParameters {
        private final SSLSocketFactory sslSocketFactory;
        private final X509TrustManager x509TrustManager;

        private SslParameters(SSLSocketFactory sslSocketFactory, X509TrustManager x509TrustManager) {
            this.sslSocketFactory = sslSocketFactory;
            this.x509TrustManager = x509TrustManager;
        }

        public static SslParameters of(SSLSocketFactory socketFactory, X509TrustManager x509TrustManager) {
            return new SslParameters(socketFactory, x509TrustManager);
        }
    }

    public static final class Builder {
        private Optional<SSLSocketFactory> sslSocketFactory = Optional.empty();
        private Optional<X509TrustManager> x509TrustManager = Optional.empty();
        private Collection<String> pollRequests;
        private int numAttempts;
        private long intervalMillis = 100;
        private int connectionTimeoutMillis = CONNECTION_TIMEOUT_MILLIS;
        private int readTimeoutMillis = READ_TIMEOUT_MILLIS;

        public Builder sslParameters(Optional<HttpPollingResource.SslParameters> value) {
            if (value.isPresent()) {
                sslSocketFactory(value.get().sslSocketFactory);
                x509TrustManager(value.get().x509TrustManager);
            }
            return this;
        }

        public Builder sslSocketFactory(SSLSocketFactory value) {
            this.sslSocketFactory = Optional.of(value);
            return this;
        }

        public Builder x509TrustManager(X509TrustManager value) {
            this.x509TrustManager = Optional.of(value);
            return this;
        }

        public Builder pollUrls(Collection<String> value) {
            this.pollRequests = new HashSet<>(value);
            return this;
        }

        public Builder numAttempts(int value) {
            this.numAttempts = value;
            return this;
        }

        public Builder intervalMillis(long value) {
            this.intervalMillis = value;
            return this;
        }

        public Builder connectionTimeoutMillis(int value) {
            this.connectionTimeoutMillis = value;
            return this;
        }

        public Builder readTimeoutMillis(int value) {
            this.readTimeoutMillis = value;
            return this;
        }

        public HttpPollingResource build() {
            return new HttpPollingResource(
                    sslSocketFactory,
                    x509TrustManager,
                    pollRequests,
                    numAttempts,
                    intervalMillis,
                    connectionTimeoutMillis,
                    readTimeoutMillis);
        }

        public FailureCachingHttpPollingResource buildFailureCaching() {
            return new FailureCachingHttpPollingResource(new HttpPollingResource(
                    sslSocketFactory,
                    x509TrustManager,
                    pollRequests,
                    numAttempts,
                    intervalMillis,
                    connectionTimeoutMillis,
                    readTimeoutMillis));
        }
    }
}
