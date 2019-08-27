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

import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ResourcePoller {

    private ResourcePoller() {}

    /**
     * Calls {@link PollableResource#isReady()} at most {@code numAttempts} times and returns once the given target
     * resource returns {@link Optional#empty()} to indicate that it is ready. Throws the last exception returned by
     * {@link PollableResource#isReady()} otherwise.
     */
    public static void poll(int numAttempts, long intervalMillis, final PollableResource target) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Optional<Exception> lastException = Optional.empty();
        for (int i = 0; i < numAttempts; ++i) {
            lastException = scheduler.schedule(target::isReady, intervalMillis, TimeUnit.MILLISECONDS).get();
            if (!lastException.isPresent()) {
                return;
            }
        }

        throw lastException.orElseGet(() -> new SafeIllegalStateException("Internal error (numAttempts == 0?)"));
    }
}
