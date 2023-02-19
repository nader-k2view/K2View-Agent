package com.k2view.agent;

//package com.k2view.cdbms.usercode.lu.mailbox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.net.http.HttpResponse.BodyHandlers.ofString;

public class AgentSender implements AutoCloseable{
    public record Request(String id, String url, String method, String headers, String body) {}
    public record Response(String id, int code, String body) {}
    private final BlockingQueue<Request> incoming;
    private final BlockingQueue<Response> outgoing;
    private final ExecutorService requestExecutor;
    private final ExecutorService responseExecutor;
    private final AtomicBoolean running;
    private final HttpClient httpClient;
    private final Thread thread;
    public AgentSender(int maxQueueSize){
        incoming = new LinkedBlockingQueue<>(maxQueueSize);
        outgoing = new LinkedBlockingQueue<>(maxQueueSize);
        requestExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "AgentSender-RequestExecutor"));
        responseExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "AgentSender-ResponseExecutor"));
        running = new AtomicBoolean(true);
        httpClient = HttpClient.newBuilder()
                .executor(requestExecutor)
                .build();
        thread = new Thread(this::run);
        thread.start();
    }

    public void send(Request request) throws Exception {
        incoming.add(request);
    }

    public List<Response> receive(long timeout, TimeUnit timeUnit) throws InterruptedException {
        // pull from outgoing queue
        List<Response> responses = new ArrayList<>();
        Response response = outgoing.poll(timeout, timeUnit);
        if(response == null){
            return responses;
        }

        responses.add(response);
        outgoing.drainTo(responses);
        return responses;
    }

    /**
     * Asynchronously retrieves the responses from the outgoing queue.
     *
     * This method returns a CompletableFuture that completes when a response is available
     * in the outgoing queue or the timeout is reached. The CompletableFuture will contain
     * a list of the available responses or an empty list if no response is available.
     *
     * @param timeout   the maximum time to wait for a response
     * @param timeUnit  the time unit of the timeout
     * @return a CompletableFuture that completes when a response is available in the outgoing
     *         queue or the timeout is reached.
     */

    public CompletableFuture<List<Response>> receiveAsync(long timeout, TimeUnit timeUnit) {
        CompletableFuture<List<Response>> future = new CompletableFuture<>();
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            List<Response> responses = new ArrayList<>();
            Response response = outgoing.poll();
            if (response == null) {
                future.complete(responses);
                return;
            }
            responses.add(response);
            outgoing.drainTo(responses);
            future.complete(responses);
        }, timeout, timeUnit);
        return future;
    }


    public void submit(Request request) {
        incoming.add(request);
    }

    private void run() {
        while (running.get()) {
            Request request = getMail();
            if (request == null) {
                continue;
            }
            sendMail(request);
        }
    }

    private Request getMail() {
        Request request = null;
        try {
            request = incoming.take();
        } catch (InterruptedException e) {
            running.set(false);
        }
        return request;
    }

    private void sendMail(Request request) {
        try {
            httpClient.sendAsync(getHttpRequest(request), ofString())
                    .thenAcceptAsync(response -> sendResponse(request.id(), response), responseExecutor);
        } catch (Exception e) {
            System.out.printf("Failed to send mail[id:%s, url:%s, method:%s], error: %s%n",
                    request.id(), request.url(), request.method(), e.getMessage());
            sendErrorResponse(request.id(), e);
        }
    }

    private static HttpRequest getHttpRequest(Request request) {
//        System.out.println(request.toString());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .method(request.method(), ofString(request.body()));
        String[] headers = getHeaders(request);
        for(int i=0; i < headers.length; i+=2){
            builder.header(headers[i], headers[i+1]);
        }
        return builder.build();
    }

    private void sendResponse(String id, HttpResponse<String> response) {
        outgoing.add(new Response(id, response.statusCode(), response.body()));
    }

    private void sendErrorResponse(String id, Exception e) {
        outgoing.add(new Response(id, 500, "agent failed: " + e.getMessage()));
    }

    private static String[] getHeaders(Request request) {
        String headers = request.headers();
        if(headers == null || headers.isEmpty()){
            return new String[0];
        }

        return headers.split(",");
    }

    @Override
    public void close() throws InterruptedException {
        running.set(false);
        requestExecutor.shutdown();
        responseExecutor.shutdown();
        thread.join();
    }
}