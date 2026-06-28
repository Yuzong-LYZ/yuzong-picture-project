package com.yuzong.yuzongpicturebackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket 全局配置类（定义连接）
 * 作用：开启 WebSocket 支持，并将 URL 路径、拦截器（安检门）和处理器（业务大厅）绑定在一起
 */
@Configuration
@EnableWebSocket // 【核心开关】：告诉 Spring Boot 开启 WebSocket 功能，底层会自动配置相关的 Bean
public class WebSocketConfig implements WebSocketConfigurer {

    // “业务处理器”（负责处理连接建立、接收消息、断开连接）
    @Resource
    private PictureEditHandler pictureEditHandler;

    // “握手拦截器”（负责在连接建立前校验登录、权限等）
    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    /**
     * 注册 WebSocket 处理器
     * 作用：配置 WebSocket 的路由映射、拦截器链和跨域策略
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 1. 绑定路由与处理器：
        // 告诉 Spring：当前端请求 "/ws/picture/edit" 这个路径时，由 pictureEditHandler 来处理
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                // 挂载拦截器：
                // 告诉 Spring：在交给 Handler 处理之前，必须先经过 wsHandshakeInterceptor 的 beforeHandshake 校验
                .addInterceptors(wsHandshakeInterceptor)
                // 配置跨域策略：
                // "*" 表示允许任何域名、任何端口的前端页面发起 WebSocket 连接。
                .setAllowedOrigins("*");
    }
}