package com.zoho.catalyst_plugin.util; // or util

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for sending HTTP responses using Netty.
 */
public final class ResponseSender {

    private ResponseSender() {} // Private constructor for utility class

    public static void sendResponse(@NotNull FullHttpRequest request,
                                    @NotNull ChannelHandlerContext context,
                                    @NotNull HttpResponseStatus status,
                                    @Nullable String content,
                                    @NotNull String contentType) {
        FullHttpResponse response = createResponse(status, content, contentType);
        sendResponse(request, context.channel(), response);
    }

    public static FullHttpResponse createResponse(@NotNull HttpResponseStatus status,
                                                  @Nullable String content,
                                                  @NotNull String contentType) {
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        response.headers().set(HttpHeaderNames.PRAGMA, "no-cache");
        response.headers().set(HttpHeaderNames.EXPIRES, "0");

        return response;
    }


    public static void sendResponse(@NotNull HttpRequest request, @NotNull Channel channel, @NotNull FullHttpResponse response) {
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        HttpUtil.setKeepAlive(response, keepAlive);

        // Send the response and close the connection if not keep-alive
        if (channel.isActive()) { // Check if channel is still active before writing
            channel.writeAndFlush(response).addListener(keepAlive ? ChannelFutureListener.CLOSE_ON_FAILURE : ChannelFutureListener.CLOSE);
        }
    }
}