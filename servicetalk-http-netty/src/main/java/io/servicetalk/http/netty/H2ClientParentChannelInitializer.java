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
package io.servicetalk.http.netty;

import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;

import static io.netty.handler.codec.http2.Http2MultiplexCodecBuilder.forClient;
import static io.netty.handler.logging.LogLevel.TRACE;

final class H2ClientParentChannelInitializer implements ChannelInitializer {
    private final ReadOnlyH2ClientConfig config;

    H2ClientParentChannelInitializer(final ReadOnlyH2ClientConfig config) {
        this.config = config;
    }

    @Override
    public ConnectionContext init(final Channel channel, final ConnectionContext context) {
        Http2MultiplexCodecBuilder multiplexCodecBuilder = forClient(H2PushStreamHandler.INSTANCE)
                // The max concurrent streams is made available via a publisher and may be consumed asynchronously
                // (e.g. when offloading is enabled), so we manually control the SETTINGS ACK frames.
                .autoAckSettingsFrame(false)
                // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                // the user to apply their own timeout at the call site.
                .gracefulShutdownTimeoutMillis(-1);
        String h2FrameLogger = config.h2FrameLogger();
        if (h2FrameLogger != null) {
            multiplexCodecBuilder.frameLogger(new Http2FrameLogger(TRACE, h2FrameLogger));
        }

        // TODO(scott): more configuration. header validation, settings stream, etc...

        channel.pipeline().addLast(multiplexCodecBuilder.build());
        return context;
    }

    @ChannelHandler.Sharable
    private static final class H2PushStreamHandler extends ChannelInboundHandlerAdapter {
        static final ChannelInboundHandlerAdapter INSTANCE = new H2PushStreamHandler();

        private H2PushStreamHandler() {
            // singleton
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.close(); // push streams are not supported
        }
    }
}