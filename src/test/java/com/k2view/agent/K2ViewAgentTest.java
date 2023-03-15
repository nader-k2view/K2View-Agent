package com.k2view.agent;

import com.k2view.agent.dispatcher.AgentDispatcher;
import com.k2view.agent.postman.Postman;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class K2ViewAgentTest {

    static TestingHttpServer server;
    @BeforeAll
    public static void setup() throws Exception {
        server = new TestingHttpServer();
        server.start();
    }

    @AfterAll
    public static void teardown() throws Exception {
        server.close();
    }

    class MockPostMan implements Postman {

        long interval = 10;

        Supplier<Requests> requests = ()->new Requests(Collections.singletonList(new Request("test", "http://localhost:8080", "GET", Map.of("Content-Type", "application/json"), "body")),
                interval);
        @Override
        public Requests getInboxMessages(List<Response> responses, String lastTaskId) {
            return requests.get();
        }
    }

    class MockDispatcher implements AgentDispatcher {

        long interval = 10;
        @Override
        public void send(Request request) {
            assertEquals("test", request.taskId());
            assertEquals("http://localhost:8080", request.url());
            assertEquals("GET", request.method());
            assertEquals("body", request.body());
            assertEquals(Map.of("Content-Type", "application/json"), request.header());
        }

        @Override
        public List<Response> receive(long timeout, TimeUnit unit) throws InterruptedException {
            assertEquals(interval, timeout);
            Thread.sleep(1000);
            return Collections.emptyList();
        }

        @Override
        public void close() throws Exception {
            System.out.println("close");
        }
    }

    @Test
    public void test_agent() throws InterruptedException {
        K2ViewAgent agent = new K2ViewAgent(new MockPostMan(), 60, new MockDispatcher(), Runnable::run);
        Thread thread = new Thread(()-> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                agent.stop();
            }
        });
        thread.start();
        agent.run();
    }

    @Test
    public void test_no_interval() throws InterruptedException {
        MockPostMan postman = new MockPostMan();
        postman.interval = 0;
        MockDispatcher agentSender = new MockDispatcher();
        agentSender.interval = 60;
        var agent = new K2ViewAgent(postman, 60, agentSender, Runnable::run);
        Thread thread = new Thread(()-> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                agent.stop();
            }
        });
        thread.start();
        agent.run();
    }

    @Test
    public void test_no_requests() throws InterruptedException {
        MockPostMan postman = new MockPostMan();
        postman.requests = ()->null;
        MockDispatcher agentSender = new MockDispatcher();
        agentSender.interval = 60;
        var agent = new K2ViewAgent(postman, 60, agentSender, Runnable::run);
        Thread thread = new Thread(()-> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                agent.stop();
            }
        });
        thread.start();
        agent.run();
    }

}