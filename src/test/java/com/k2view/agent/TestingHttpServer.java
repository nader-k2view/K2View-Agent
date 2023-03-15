package com.k2view.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

class TestingHttpServer implements AutoCloseable {
    HttpServer server;
    AtomicInteger counter = new AtomicInteger(0);

    public void start() throws Exception {
        HttpServer server = HttpServer.create();
        server.bind(new InetSocketAddress(8080), 0);
        server.createContext("/", new MyHandler());
        server.start();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            counter.incrementAndGet();
            String response = "Hello World!";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

}
