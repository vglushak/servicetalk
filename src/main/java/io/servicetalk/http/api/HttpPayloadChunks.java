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
package io.servicetalk.http.api;

import io.servicetalk.buffer.Buffer;

/**
 * Factory methods for creating {@link HttpPayloadChunk}s.
 */
public final class HttpPayloadChunks {

    private HttpPayloadChunks() {
        // No instances.
    }

    /**
     * Create an {@link HttpPayloadChunk} instance with the specified {@code content}.
     *
     * @param content the content.
     * @return a new {@link HttpPayloadChunk}.
     */
    public static HttpPayloadChunk newPayloadChunk(final Buffer content) {
        return new DefaultHttpPayloadChunk(content);
    }

    /**
     * Create an {@link HttpPayloadChunk} instance with the specified {@code content}.
     *
     * @param content the content.
     * @param trailers the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailer headers</a>.
     * @return a new {@link LastHttpPayloadChunk}.
     */
    public static LastHttpPayloadChunk newLastPayloadChunk(final Buffer content, final HttpHeaders trailers) {
        return new DefaultLastHttpPayloadChunk(content, trailers);
    }
}
