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

import io.servicetalk.concurrent.api.Publisher;

/**
 * Factory method for creating {@link HttpResponse}s, for use by protocol decoders.
 */
public interface HttpResponseFactory extends HttpTrailersFactory {
    /**
     * Create a new instance.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param messageBody a {@link Publisher} of the message body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link HttpResponse}.
     */
    <O> HttpResponse<O> newResponse(HttpProtocolVersion version, HttpResponseStatus status, Publisher<O> messageBody);
}
