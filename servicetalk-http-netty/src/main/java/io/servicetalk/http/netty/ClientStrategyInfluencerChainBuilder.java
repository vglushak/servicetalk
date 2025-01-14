/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;

final class ClientStrategyInfluencerChainBuilder {

    private HttpExecutionStrategy connFactoryChain;
    private HttpExecutionStrategy connFilterChain;
    private HttpExecutionStrategy clientChain;

    ClientStrategyInfluencerChainBuilder() {
        connFactoryChain = HttpExecutionStrategies.anyStrategy();
        connFilterChain = HttpExecutionStrategies.anyStrategy();
        clientChain = HttpExecutionStrategies.anyStrategy();
    }

    private ClientStrategyInfluencerChainBuilder(ClientStrategyInfluencerChainBuilder from) {
        connFactoryChain = from.connFactoryChain;
        connFilterChain = from.connFilterChain;
        clientChain = from.clientChain;
    }

    void add(StreamingHttpClientFilterFactory clientFilter) {
        clientChain = clientChain.merge(clientFilter.requiredOffloads());
    }

    void add(HttpLoadBalancerFactory<?> lb) {
        clientChain = clientChain.merge(lb.requiredOffloads());
    }

    void add(ConnectionFactoryFilter<?, FilterableStreamingHttpConnection> connectionFactoryFilter) {
        connFactoryChain =
                connFactoryChain.merge(HttpExecutionStrategy.from(connectionFactoryFilter.requiredOffloads()));
    }

    void add(StreamingHttpConnectionFilterFactory connectionFilter) {
        connFilterChain =
                connFilterChain.merge(HttpExecutionStrategy.from(connectionFilter.requiredOffloads()));
    }

    HttpExecutionStrategy buildForClient(HttpExecutionStrategy transportStrategy) {
        return clientChain.merge(buildForConnectionFactory(transportStrategy));
    }

    HttpExecutionStrategy buildForConnectionFactory(HttpExecutionStrategy transportStrategy) {
        return connFilterChain.merge(connFactoryChain.merge(transportStrategy));
    }

    ClientStrategyInfluencerChainBuilder copy() {
        return new ClientStrategyInfluencerChainBuilder(this);
    }
}
