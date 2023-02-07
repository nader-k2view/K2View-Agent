package com.k2view.agent;


import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiThreadedHttpClientTest {

    @Test
    public void testSendAsync() throws Exception {
        MultiThreadedHttpClient client = new MultiThreadedHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.google.com"))
                .GET()
                .build();
        CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(request);

        // Wait for the response
        HttpResponse<String> response = responseFuture.get();

        // Assert that the response status code is 200 (OK)
        assertEquals(200, response.statusCode());
    }
}
