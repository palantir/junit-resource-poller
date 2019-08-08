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

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class HttpPollingExtension implements Extension, BeforeAllCallback {
    private final HttpPollingResource delegate;

    private HttpPollingExtension(HttpPollingResource delegate) {
        this.delegate = delegate;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        delegate.before();
    }

    public static HttpPollingExtension.Builder builder() {
        return new HttpPollingExtension.Builder();
    }

    public static final class Builder extends HttpPollingBuilder<HttpPollingExtension> {
        @Override
        public HttpPollingExtension build() {
            return new HttpPollingExtension(new HttpPollingResource(
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
