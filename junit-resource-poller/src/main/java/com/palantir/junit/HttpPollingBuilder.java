/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

@SuppressWarnings("VisibilityModifier")
abstract class HttpPollingBuilder<T> {
    protected Optional<SSLSocketFactory> sslSocketFactory = Optional.empty();
    protected Optional<X509TrustManager> x509TrustManager = Optional.empty();
    protected Collection<String> pollRequests;
    protected int numAttempts;
    protected long intervalMillis = 100;
    protected int connectionTimeoutMillis = 500;
    protected int readTimeoutMillis = 500;

    public HttpPollingBuilder<T> sslParameters(Optional<HttpPollingResource.SslParameters> value) {
        if (value.isPresent()) {
            sslSocketFactory(value.get().sslSocketFactory());
            x509TrustManager(value.get().x509TrustManager());
        }
        return this;
    }

    public HttpPollingBuilder<T> sslSocketFactory(SSLSocketFactory value) {
        this.sslSocketFactory = Optional.of(value);
        return this;
    }

    public HttpPollingBuilder<T> x509TrustManager(X509TrustManager value) {
        this.x509TrustManager = Optional.of(value);
        return this;
    }

    public HttpPollingBuilder<T> pollUrls(Collection<String> value) {
        this.pollRequests = new HashSet<>(value);
        return this;
    }

    public HttpPollingBuilder<T> numAttempts(int value) {
        this.numAttempts = value;
        return this;
    }

    public HttpPollingBuilder<T> intervalMillis(long value) {
        this.intervalMillis = value;
        return this;
    }

    public HttpPollingBuilder<T> connectionTimeoutMillis(int value) {
        this.connectionTimeoutMillis = value;
        return this;
    }

    public HttpPollingBuilder<T> readTimeoutMillis(int value) {
        this.readTimeoutMillis = value;
        return this;
    }

    /** Real Builders should override this to produce the right type of JUnit4/5 builder. */
    public abstract T build();
}
