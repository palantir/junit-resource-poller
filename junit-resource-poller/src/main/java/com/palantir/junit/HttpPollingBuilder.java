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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

@SuppressWarnings("VisibilityModifier")
abstract class HttpPollingBuilder<B> {
    protected Optional<SSLSocketFactory> sslSocketFactory = Optional.empty();
    protected Optional<X509TrustManager> x509TrustManager = Optional.empty();
    protected Collection<String> pollRequests;
    protected int numAttempts;
    protected long intervalMillis = 100;
    protected int connectionTimeoutMillis = 500;
    protected int readTimeoutMillis = 500;

    public B sslParameters(Optional<HttpPollingResource.SslParameters> value) {
        if (value.isPresent()) {
            sslSocketFactory(value.get().sslSocketFactory());
            x509TrustManager(value.get().x509TrustManager());
        }
        return (B) this;
    }

    public B sslSocketFactory(SSLSocketFactory value) {
        this.sslSocketFactory = Optional.of(value);
        return (B) this;
    }

    public B x509TrustManager(X509TrustManager value) {
        this.x509TrustManager = Optional.of(value);
        return (B) this;
    }

    public B pollUrls(Collection<String> value) {
        this.pollRequests = new ArrayList<>(value);
        return (B) this;
    }

    public B numAttempts(int value) {
        this.numAttempts = value;
        return (B) this;
    }

    public B intervalMillis(long value) {
        this.intervalMillis = value;
        return (B) this;
    }

    public B connectionTimeoutMillis(int value) {
        this.connectionTimeoutMillis = value;
        return (B) this;
    }

    public B readTimeoutMillis(int value) {
        this.readTimeoutMillis = value;
        return (B) this;
    }
}
