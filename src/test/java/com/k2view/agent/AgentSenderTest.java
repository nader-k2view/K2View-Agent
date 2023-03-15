package com.k2view.agent;

import com.k2view.agent.dispatcher.AgentDispatcherHttp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AgentSenderTest {

    static TestingHttpServer server;
    @BeforeAll
    public static void setup() throws Exception {
        server = new TestingHttpServer();
        server.start();
    }

    @AfterAll
    public static void teardown() {
        server.close();
    }

    @Test
    public void test_hello_world() throws Exception {
        try (AgentDispatcherHttp sender = new AgentDispatcherHttp(10)) {
            server.counter.set(0);
            sender.send(new Request("test", "http://localhost:8080", "GET", Collections.emptyMap(), "body"));
            List<Response> receive = sender.receive(1, TimeUnit.SECONDS);
            assertEquals(1, receive.size());
            Response response = receive.get(0);
            assertEquals("test", response.taskId());
            assertEquals(200, response.code());
            assertEquals("Hello World!", response.body());
            assertEquals(1, server.counter.get());
        }
    }

    @Test
    public void test_multiple_requests() throws Exception {
        try (AgentDispatcherHttp sender = new AgentDispatcherHttp(10)) {
            server.counter.set(0);
            sender.send(new Request("test1", "http://localhost:8080", "GET", Collections.emptyMap(), "body"));
            sender.send(new Request("test2", "http://localhost:8080", "GET", Collections.emptyMap(), "body"));
            sender.send(new Request("test3", "http://localhost:8080", "GET", Collections.emptyMap(), "body"));
            Thread.sleep(1000);
            List<Response> receive = sender.receive(1, TimeUnit.SECONDS);
            assertEquals(3, receive.size());
            String collect = receive.stream().map(Response::taskId).sorted().collect(Collectors.joining(","));
            assertEquals("test1,test2,test3", collect);
            assertEquals(3, server.counter.get());
        }
    }

    @Test
    public void test_hello_world_with_header() throws InterruptedException {
        try (AgentDispatcherHttp sender = new AgentDispatcherHttp(10)) {
            server.counter.set(0);
            sender.send(new Request("test", "http://localhost:8080", "GET", Collections.singletonMap("Content-Type", "application/json"), "body"));
            List<Response> receive = sender.receive(1, TimeUnit.SECONDS);
            assertEquals(1, receive.size());
            Response response = receive.get(0);
            assertEquals("test", response.taskId());
            assertEquals(200, response.code());
            assertEquals("Hello World!", response.body());
            assertEquals(1, server.counter.get());
        }
    }

    @Test
    public void test_hello_world_with_header_list() throws InterruptedException {
        try (AgentDispatcherHttp sender = new AgentDispatcherHttp(10)) {
            server.counter.set(0);
            sender.send(new Request("test", "http://localhost:8080", "GET", Collections.singletonMap("a", List.of("a", "b")), "body"));
            List<Response> receive = sender.receive(1, TimeUnit.SECONDS);
            assertEquals(1, receive.size());
            Response response = receive.get(0);
            assertEquals("test", response.taskId());
            assertEquals(200, response.code());
            assertEquals("Hello World!", response.body());
            assertEquals(1, server.counter.get());
        }
    }

    @Test
    public void test_hello_world_error_url() throws InterruptedException {
        try (AgentDispatcherHttp sender = new AgentDispatcherHttp(10)) {
            server.counter.set(0);
            sender.send(new Request("test", "localhost:8081", "GET", Collections.emptyMap(), "body"));
            List<Response> receive = sender.receive(1, TimeUnit.SECONDS);
            assertEquals(1, receive.size());
            Response response = receive.get(0);
            assertEquals("test", response.taskId());
            assertEquals(500, response.code());
            assertEquals("K2View Agent Failed: invalid URI scheme localhost", response.body());
            assertEquals(0, server.counter.get());
        }
    }
}