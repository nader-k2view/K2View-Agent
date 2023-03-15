package com.k2view.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestTest {
    @Test
    void test_requests_adapter(){
        String s = """
                {
                    "requests": [
                        {
                            "taskId": "test",
                            "url": "http://localhost:8080",
                            "method": "GET",
                            "header": {
                                "Content-Type": "application/json",
                                "Accept": "application/json"
                            },
                            "body": "test"
                        }
                    ],
                    "pollInterval": 1000
                }
                """;
        Requests requests = Utils.gson.fromJson(s, Requests.class);
        assertEquals(1, requests.requests().size());
        Request request = requests.requests().get(0);
        assertNotNull(request);
        assertEquals("test", request.taskId());
        assertEquals("http://localhost:8080", request.url());
        assertEquals("GET", request.method());
        assertEquals("test", request.body());
        assertEquals(2, request.header().size());
        assertEquals("application/json", request.header().get("Content-Type"));
        assertEquals("application/json", request.header().get("Accept"));
        assertEquals(1000, requests.pollInterval());
    }

    @Test
    public void test_request_adapter(){
        String s = """
                {
                    "taskId": "test",
                    "url": "http://localhost:8080",
                    "method": "GET",
                    "header": {
                        "Content-Type": "application/json",
                        "Accept": "application/json"
                    },
                    "body": "test"
                }
                """;
        Request request = Utils.gson.fromJson(s, Request.class);
        assertNotNull(request);
        assertEquals("test", request.taskId());
        assertEquals("http://localhost:8080", request.url());
        assertEquals("GET", request.method());
        assertEquals("test", request.body());
        assertEquals(2, request.header().size());
        assertEquals("application/json", request.header().get("Content-Type"));
        assertEquals("application/json", request.header().get("Accept"));
    }

    @Test
    public void test_request_adapter_with_dynamic_string(){
        String s = """
                {
                    "taskId": "test",
                    "url": "http://${host}:${port}",
                    "method": "GET",
                    "header": {
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                        "Authorization": "Bearer ${token}"
                    },
                    "body": "test ${body}"
                }
                """;

        //set env variables
        System.setProperty("host", "localhost");
        System.setProperty("port", "8080");
        System.setProperty("token", "123456");
        System.setProperty("body", "body");

        Request request = Utils.gson.fromJson(s, Request.class);
        assertNotNull(request);
        assertEquals("test", request.taskId());
        assertEquals("http://localhost:8080", request.url());
        assertEquals("GET", request.method());
        assertEquals("test body", request.body());
        assertEquals(3, request.header().size());
        assertEquals("application/json", request.header().get("Content-Type"));
        assertEquals("application/json", request.header().get("Accept"));
        assertEquals("Bearer 123456", request.header().get("Authorization"));

    }

}