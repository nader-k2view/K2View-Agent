package com.k2view.agent;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/*
 the MultiThreadedHttpClient class uses a fixed thread pool with 10 threads to handle asynchronous HTTP requests.
 The sendAsync method sends an HTTP request and returns a CompletableFuture representing the response.
 The response is handled by the whenCompleteAsync method, which is executed asynchronously by the executor.
 */
public class MultiThreadedHttpClient {
    private final HttpClient httpClient;
    private final Executor executor;

    public MultiThreadedHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.executor = Executors.newFixedThreadPool(10);
    }

    public CompletableFuture<HttpResponse<String>> sendAsync(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenCompleteAsync((response, throwable) -> {
                    if (throwable != null) {
                        throwable.printStackTrace();
                    }
                }, executor);
    }
}
