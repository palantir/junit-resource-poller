/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import java.util.concurrent.atomic.AtomicReference;
import org.junit.rules.ExternalResource;

/**
 * A poller that stops after the first failure, and returns that failure repeatedly in subsequent
 * invocations.
 *
 * This is useful if you want to tolerate long waits on the first invocation (to allow resources to
 * come up), but not on subsequent ones - so that running a 100 tests when one resource will never come
 * up doesn't take 100 times as long before failing.
 */
public final class FailureCachingHttpPollingResource extends ExternalResource {
    private final HttpPollingResource poller;
    private final AtomicReference<Throwable> maybeError = new AtomicReference<>();

    public FailureCachingHttpPollingResource(HttpPollingResource poller) {
        this.poller = poller;
    }

    @Override
    protected void before() {
        Throwable previousError = maybeError.get();
        if (previousError == null) {
            try {
                poller.before();
            } catch (Throwable e) {
                // we don't care which error of multiple parallel invocations is registered,
                // so we don't need to compare-and-set
                maybeError.set(e);
                throw e;
            }
        } else {
            throw new IllegalStateException("Failing due to previous error", previousError);
        }
    }
}
