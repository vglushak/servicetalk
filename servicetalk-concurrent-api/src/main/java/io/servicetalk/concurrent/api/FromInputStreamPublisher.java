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
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Math.min;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Publisher} created from an {@link InputStream} such that any data requested from the {@link Publisher} is
 * read from the {@link InputStream} until it terminates.
 *
 * Given that {@link InputStream} is a blocking API, requesting data from the {@link Publisher} can block on {@link
 * Subscription#request(long)} until there is sufficient data available. The implementation attempts to minimize
 * blocking, however by reading data faster than the writer is sending, blocking is inevitable.
 */
final class FromInputStreamPublisher extends Publisher<byte[]> {

    private static final AtomicIntegerFieldUpdater<FromInputStreamPublisher> subscribedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FromInputStreamPublisher.class, "subscribed");

    /**
     * A field together with {@link InputStreamPublisherSubscription#requested} that contains the application state:
     * <ul>
     *      <li>{@code 0} - waiting for subscriber</li>
     *      <li>{@code 1} - subscribed, waiting for items to be emitted or stream termination</li>
     *      <li>({@code requested == -1}) - see below - when {@link InputStream} and {@link Subscription} are terminated
     * </ul>
     * @see InputStreamPublisherSubscription#requested
     * @see InputStreamPublisherSubscription#TERMINAL_SENT
     */
    @SuppressWarnings("unused")
    private volatile int subscribed;

    private final InputStream stream;

    /**
     * A new instance.
     *
     * @param stream the {@link InputStream} to expose as a {@link Publisher}
     */
    FromInputStreamPublisher(final InputStream stream) {
        this.stream = requireNonNull(stream);
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super byte[]> subscriber) {
        if (subscribedUpdater.compareAndSet(this, 0, 1)) {
            subscriber.onSubscribe(new InputStreamPublisherSubscription(stream, subscriber));
        } else {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Publisher already subscribed to"));
        }
    }

    private static final class InputStreamPublisherSubscription implements Subscription {

        private static final int END_OF_FILE = -1;
        /**
         * Value assigned to {@link #requested} when terminal event was sent.
         */
        private static final int TERMINAL_SENT = -1;

        private final InputStream stream;
        private final Subscriber<? super byte[]> subscriber;
        /**
         * Contains the outstanding demand or {@link #TERMINAL_SENT} indicating when {@link InputStream} and {@link
         * Subscription} are terminated.
         */
        private long requested;
        @Nullable
        private byte[] buffer;
        private int writeIdx;
        private boolean ignoreRequests;

        InputStreamPublisherSubscription(final InputStream stream, final Subscriber<? super byte[]> subscriber) {
            this.stream = stream;
            this.subscriber = subscriber;
        }

        @Override
        public void request(final long n) {
            // No need to protect against concurrency between request and cancel
            // https://github.com/reactive-streams/reactive-streams-jvm#2.7
            if (requested == TERMINAL_SENT) {
                return;
            }
            if (!isRequestNValid(n)) {
                sendOnError(subscriber, closeStreamOnError(newExceptionForInvalidRequestN(n)));
                return;
            }
            requested = addWithOverflowProtection(requested, n);
            if (ignoreRequests) {
                return;
            }
            ignoreRequests = true;
            readAndDeliver(subscriber);
            if (requested != TERMINAL_SENT) {
                ignoreRequests = false;
            }
        }

        @Override
        public void cancel() {
            if (trySetTerminalSent()) {
                closeStream(subscriber);
            }
        }

        private void readAndDeliver(final Subscriber<? super byte[]> subscriber) {
            try {
                do {
                    // Can't fully trust available(), but it's a reasonable hint to mitigate blocking on read().
                    int available = stream.available();
                    if (available == 0) {
                        // Work around InputStreams that don't strictly honor the 0 == EOF contract.
                        available = buffer != null ? buffer.length : 1;
                    }
                    available = fillBufferAvoidingBlocking(available);
                    emitSingleBuffer(subscriber);
                    if (available == END_OF_FILE) {
                        sendOnComplete(subscriber);
                        return;
                    }
                } while (requested > 0);
            } catch (Throwable t) {
                sendOnError(subscriber, closeStreamOnError(t));
            }
        }

        // This method honors the estimated available bytes that can be read without blocking
        private int fillBufferAvoidingBlocking(int available) throws IOException {
            if (buffer == null) {
                buffer = new byte[available];
            }
            while (writeIdx != buffer.length && available > 0) {
                int len = min(buffer.length - writeIdx, available);
                int readActual = stream.read(buffer, writeIdx, len); // may block if len > available
                if (readActual == END_OF_FILE) {
                    return END_OF_FILE;
                }
                available -= readActual;
                writeIdx += readActual;
            }
            return available;
        }

        private void emitSingleBuffer(final Subscriber<? super byte[]> subscriber) {
            if (writeIdx < 1) {
                return;
            }
            assert buffer != null : "should have a buffer when writeIdx > 0";
            final byte[] b;
            if (writeIdx == buffer.length) {
                b = buffer;
                buffer = null;
            } else {
                b = new byte[writeIdx];
                arraycopy(buffer, 0, b, 0, writeIdx);
            }
            requested--;
            writeIdx = 0;
            subscriber.onNext(b);
        }

        private void sendOnComplete(final Subscriber<? super byte[]> subscriber) {
            closeStream(subscriber);
            if (trySetTerminalSent()) {
                subscriber.onComplete();
            }
        }

        private <T extends Throwable> void sendOnError(final Subscriber<? super byte[]> subscriber, final T t) {
            if (trySetTerminalSent()) {
                subscriber.onError(t);
            }
        }

        private <T extends Throwable> T closeStreamOnError(T t) {
            try {
                stream.close();
            } catch (IOException e) {
                t.addSuppressed(e);
            }
            return t;
        }

        private void closeStream(final Subscriber<? super byte[]> subscriber) {
            try {
                stream.close();
            } catch (IOException e) {
                if (trySetTerminalSent()) {
                    sendOnError(subscriber, e);
                }
            }
        }

        /**
         * @return {@code true} if terminal event wasn't sent and marks the state as sent, {@code false} otherwise
         */
        private boolean trySetTerminalSent() {
            if (requested == TERMINAL_SENT) {
                return false;
            }
            requested = TERMINAL_SENT;
            return true;
        }
    }
}