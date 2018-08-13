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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ContextFilter;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.ContextFilterSuccessful.COMPLETED;

abstract class AbstractContextFilterChannelHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContextFilterChannelHandler.class);

    private static final byte READ_SUPPRESSED = 0;
    private static final byte PENDING_READ = 1;
    private static final byte READ_ALLOWED = 2;

    private final ConnectionContext context;
    private final ContextFilter contextFilter;
    private final Executor executor;
    private byte state;
    @Nullable
    private SequentialCancellable sequentialCancellable;

    AbstractContextFilterChannelHandler(final ConnectionContext context, final ContextFilter contextFilter,
                                        final Executor executor) {
        this.context = context;
        this.contextFilter = contextFilter;
        this.executor = executor;
    }

    void executeContextFilter(final ChannelHandlerContext ctx) {
        assert ctx.channel().eventLoop().inEventLoop();

        sequentialCancellable = new SequentialCancellable();
        executor.execute(() -> runContextFilter(ctx));
    }

    abstract void onContextFilterSuccessful(ChannelHandlerContext ctx);

    private void runContextFilter(final ChannelHandlerContext ctx) {
        Single<Boolean> filterResult;
        try {
            filterResult = contextFilter.filter(context);
        } catch (Throwable t) {
            ctx.close();
            LOGGER.warn("Exception from context filter {} for context {}.", contextFilter, context, t);
            return;
        }
        filterResult.subscribe(new Single.Subscriber<Boolean>() {

            @Override
            public void onSubscribe(final Cancellable cancellable) {
                assert sequentialCancellable != null;

                sequentialCancellable.setNextCancellable(cancellable);
            }

            @Override
            public void onSuccess(@Nullable final Boolean result) {
                if (result != null && result) {
                    // handleSuccess makes multiple calls that will queue things on the event loop already, so we
                    // offload the whole method to the event loop. This also ensures handleSuccess is only run from
                    // the event loop, which means state doesn't need to be handled concurrently.
                    final EventLoop eventLoop = ctx.channel().eventLoop();
                    if (eventLoop.inEventLoop()) {
                        handleSuccess(ctx);
                    } else {
                        eventLoop.execute(() -> handleSuccess(ctx));
                    }
                } else {
                    // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Rejected connection from {}", context.getRemoteAddress());
                    }
                    ctx.close();
                }
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.warn("Error from context filter {} for context {}.", contextFilter, context, t);
                ctx.close();
            }
        });
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        assert sequentialCancellable != null;

        state = READ_SUPPRESSED;
        try {
            sequentialCancellable.cancel();
        } finally {
            ctx.fireChannelInactive();
        }
    }

    void handleSuccess(final ChannelHandlerContext ctx) {
        try {
            assert ctx.channel().eventLoop().inEventLoop();

            // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Accepted connection from {}", context.getRemoteAddress());
            }

            final byte oldState = state;
            if (ctx.channel().isActive()) {
                state = READ_ALLOWED;
            }
            if (oldState == PENDING_READ) {
                ctx.read();
            }

            onContextFilterSuccessful(ctx);
            ctx.fireUserEventTriggered(COMPLETED);

            ctx.pipeline().remove(this);
        } catch (Throwable t) {
            LOGGER.error("An error occurred while handling success of context filter {} for context {}",
                    contextFilter, ctx, t);
            ctx.close();
        }
    }

    @Override
    public void read(final ChannelHandlerContext ctx) {
        if (state != READ_ALLOWED) {
            state = PENDING_READ;
        } else {
            ctx.read();
        }
    }
}
