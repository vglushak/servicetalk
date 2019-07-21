/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServiceFactory;
import io.servicetalk.grpc.api.GrpcServiceFactory.ServerBinder;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ConnectionAcceptorFactory;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;

import java.io.InputStream;
import java.net.SocketOption;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;

final class DefaultGrpcServerBuilder extends GrpcServerBuilder implements ServerBinder {

    private final HttpServerBuilder httpServerBuilder;
    private final ExecutionContextBuilder contextBuilder = new ExecutionContextBuilder();

    DefaultGrpcServerBuilder(final HttpServerBuilder httpServerBuilder) {
        this.httpServerBuilder = httpServerBuilder;
    }

    @Override
    public GrpcServerBuilder headersFactory(final HttpHeadersFactory headersFactory) {
        httpServerBuilder.headersFactory(headersFactory);
        return this;
    }

    @Override
    public GrpcServerBuilder clientCloseTimeout(final long clientCloseTimeoutMs) {
        httpServerBuilder.clientCloseTimeout(clientCloseTimeoutMs);
        return this;
    }

    @Override
    public GrpcServerBuilder maxInitialLineLength(final int maxInitialLineLength) {
        httpServerBuilder.maxHeaderSize(maxInitialLineLength);
        return this;
    }

    @Override
    public GrpcServerBuilder maxHeaderSize(final int maxHeaderSize) {
        httpServerBuilder.maxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public GrpcServerBuilder headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        httpServerBuilder.headersEncodedSizeEstimate(headersEncodedSizeEstimate);
        return this;
    }

    @Override
    public GrpcServerBuilder trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        httpServerBuilder.trailersEncodedSizeEstimate(trailersEncodedSizeEstimate);
        return this;
    }

    @Override
    public GrpcServerBuilder backlog(final int backlog) {
        httpServerBuilder.backlog(backlog);
        return this;
    }

    @Override
    public GrpcServerBuilder sniConfig(@Nullable final Map<String, SslConfig> mappings, final SslConfig defaultConfig) {
        throw new UnsupportedOperationException("SSL not yet supported.");
    }

    @Override
    public ServerSslConfigBuilder<GrpcServerBuilder> enableSsl(final KeyManagerFactory keyManagerFactory) {
        throw new UnsupportedOperationException("SSL not yet supported.");
    }

    @Override
    public ServerSslConfigBuilder<GrpcServerBuilder> enableSsl(final Supplier<InputStream> keyCertChainSupplier,
                                                               final Supplier<InputStream> keySupplier) {
        throw new UnsupportedOperationException("SSL not yet supported.");
    }

    @Override
    public ServerSslConfigBuilder<GrpcServerBuilder> enableSsl(final Supplier<InputStream> keyCertChainSupplier,
                                                               final Supplier<InputStream> keySupplier,
                                                               final String keyPassword) {
        throw new UnsupportedOperationException("SSL not yet supported.");
    }

    @Override
    public <T> GrpcServerBuilder socketOption(final SocketOption<T> option, final T value) {
        httpServerBuilder.socketOption(option, value);
        return this;
    }

    @Override
    public GrpcServerBuilder enableWireLogging(final String loggerName) {
        httpServerBuilder.enableWireLogging(loggerName);
        return this;
    }

    @Override
    public GrpcServerBuilder disableWireLogging() {
        httpServerBuilder.disableWireLogging();
        return this;
    }

    @Override
    public GrpcServerBuilder disableDrainingRequestPayloadBody() {
        httpServerBuilder.disableDrainingRequestPayloadBody();
        return this;
    }

    @Override
    public GrpcServerBuilder enableDrainingRequestPayloadBody() {
        httpServerBuilder.enableDrainingRequestPayloadBody();
        return this;
    }

    @Override
    public GrpcServerBuilder appendConnectionAcceptorFilter(final ConnectionAcceptorFactory factory) {
        httpServerBuilder.appendConnectionAcceptorFilter(factory);
        return this;
    }

    @Override
    public GrpcServerBuilder appendHttpServiceFilter(final StreamingHttpServiceFilterFactory factory) {
        httpServerBuilder.appendServiceFilter(factory);
        return this;
    }

    @Override
    public GrpcServerBuilder appendHttpServiceFilter(final Predicate<StreamingHttpRequest> predicate,
                                                     final StreamingHttpServiceFilterFactory factory) {
        httpServerBuilder.appendServiceFilter(predicate, factory);
        return this;
    }

    @Override
    public GrpcServerBuilder ioExecutor(final IoExecutor ioExecutor) {
        contextBuilder.ioExecutor(ioExecutor);
        httpServerBuilder.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public GrpcServerBuilder bufferAllocator(final BufferAllocator allocator) {
        contextBuilder.bufferAllocator(allocator);
        httpServerBuilder.bufferAllocator(allocator);
        return this;
    }

    @Override
    public GrpcServerBuilder executionStrategy(final HttpExecutionStrategy strategy) {
        contextBuilder.executionStrategy(strategy);
        httpServerBuilder.executionStrategy(strategy);
        return this;
    }

    @Override
    protected Single<ServerContext> doListen(final GrpcServiceFactory<?, ?, ?> serviceFactory) {
        ExecutionContext executionContext = contextBuilder.build();
        return serviceFactory.bind(this, executionContext);
    }

    @Override
    public Single<ServerContext> bind(final HttpService service) {
        return httpServerBuilder.listen(service);
    }

    @Override
    public Single<ServerContext> bindStreaming(final StreamingHttpService service) {
        return httpServerBuilder.listenStreaming(service);
    }

    @Override
    public Single<ServerContext> bindBlocking(final BlockingHttpService service) {
        return httpServerBuilder.listenBlocking(service);
    }

    @Override
    public Single<ServerContext> bindBlockingStreaming(final BlockingStreamingHttpService service) {
        return httpServerBuilder.listenBlockingStreaming(service);
    }
}