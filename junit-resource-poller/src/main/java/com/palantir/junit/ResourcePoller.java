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

import com.google.common.base.Optional;


public abstract class ResourcePoller {

    /**
     * Returns when the given target resource is {@link PollableResource#isReady()} after {@code timeoutMillis}
     * milliseconds, or throws the last error returned from polling otherwise.
     */
    public static void poll(long timeoutMillis, long intervalMillis, PollableResource target) throws Exception {
        long startTime = System.currentTimeMillis();
        Optional<Exception> lastException;
        do {
            lastException = target.isReady();
            Thread.sleep(intervalMillis);
        } while (lastException.isPresent() && System.currentTimeMillis() - startTime < timeoutMillis);

        if (lastException.isPresent()) {
            throw lastException.get();
        }
    }
}
