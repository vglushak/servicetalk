/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableWithExecutor;
import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

public abstract class AbstractPublishAndSubscribeOnTest {
    static final int ORIGINAL_SUBSCRIBER_THREAD = 0;
    static final int OFFLOADED_SUBSCRIBER_THREAD = 1;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule originalSourceExecutorRule = new ExecutorRule();

    protected Thread[] setupAndSubscribe(Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        Thread[] capturedThreads = new Thread[2];

        Completable original = new CompletableWithExecutor(originalSourceExecutorRule.getExecutor(), completed())
                .doAfterComplete(() -> capturedThreads[0] = Thread.currentThread());

        Completable offloaded = offloadingFunction.apply(original);

        offloaded.doAfterFinally(allDone::countDown)
                .doBeforeComplete(() -> capturedThreads[1] = Thread.currentThread())
                .subscribe();
        allDone.await();

        verifyCapturedThreads(capturedThreads);
        return capturedThreads;
    }

    protected Thread[] setupForCancelAndSubscribe(Function<Completable, Completable> offloadingFunction)
            throws InterruptedException {
        CountDownLatch allDone = new CountDownLatch(1);
        Thread[] capturedThreads = new Thread[2];

        Completable original = new CompletableWithExecutor(originalSourceExecutorRule.getExecutor(), never())
                .doAfterCancel(() -> {
                    capturedThreads[0] = Thread.currentThread();
                    allDone.countDown();
                });

        Completable offloaded = offloadingFunction.apply(original);

        offloaded.doBeforeCancel(() -> capturedThreads[1] = Thread.currentThread())
                .subscribe().cancel();
        allDone.await();

        verifyCapturedThreads(capturedThreads);
        return capturedThreads;
    }

    private void verifyCapturedThreads(final Thread[] capturedThreads) {
        for (int i = 0; i < capturedThreads.length; i++) {
            final Thread capturedThread = capturedThreads[i];
            assertThat("Unexpected captured thread at index: " + i, capturedThread, notNullValue());
        }
    }
}