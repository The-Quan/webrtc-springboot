package org.example.callvideogroupspring;

import org.example.callvideogroupspring.call.CallGroupWebSocketHandler;
import org.example.callvideogroupspring.chat.ChatGroupWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new CallGroupWebSocketHandler(), "/ws/call").setAllowedOrigins("*");
        registry.addHandler(new ChatGroupWebSocketHandler(), "/ws/chat").setAllowedOrigins("*");
    }
}