package kr.ktb.zura.needu.aichat.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiChatClientConfigTest {

    @Test
    void aiChatRestClient_usesHttp11WithoutUpgradeHeader() throws IOException {
        AtomicReference<String> protocol = new AtomicReference<>();
        AtomicReference<String> upgrade = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            protocol.set(exchange.getProtocol());
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            RestClient client = new AiChatClientConfig().aiChatRestClient(
                    RestClient.builder(), "http://localhost:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1), Duration.ofSeconds(1));

            client.get().uri("/health").retrieve().toBodilessEntity();

            assertEquals("HTTP/1.1", protocol.get());
            assertNull(upgrade.get());
        } finally {
            server.stop(0);
        }
    }
}
