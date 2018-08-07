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
package io.servicetalk.concurrent.api;

import org.reactivestreams.Subscriber;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;

final class EmptyPublisher<T> extends AbstractSynchronousPublisher<T> {

    private static final EmptyPublisher EMPTY_PUBLISHER = new EmptyPublisher();

    private EmptyPublisher() {
        // singleton
    }

    @Override
    void doSubscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
        subscriber.onComplete();
    }

    @SuppressWarnings("unchecked")
    static <T> Publisher<T> emptyPublisher() {
        return (Publisher<T>) EMPTY_PUBLISHER;
    }
}
