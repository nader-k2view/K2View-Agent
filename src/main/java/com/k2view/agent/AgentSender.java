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


/**
 * The `AgentSender` class provides an asynchronous mechanism for sending HTTP requests and receiving their responses.
 *
 * It uses a `LinkedBlockingQueue` to store incoming requests and an `ExecutorService` to handle outgoing requests.
 * Responses are added to an `outgoing` queue, which can be polled to retrieve responses synchronously or asynchronously.
 */
public class AgentSender implements AutoCloseable{
    /**
     * Represents an HTTP request that is sent to the server.
     */

    public record Request(String id, String url, String method, String headers, String body) {}
    /**
     * Represents an HTTP response received from the server.
     */

    public record Response(String id, int code, String body) {}

    /**
     * A blocking queue that stores incoming requests.
     */
    private final BlockingQueue<Request> incoming;

    /**
     * A blocking queue that stores outgoing responses.
     */
    private final BlockingQueue<Response> outgoing;

    /**
     * An `ExecutorService` that handles incoming requests.
     */
    private final ExecutorService requestExecutor;

    /**
     * An `ExecutorService` that handles outgoing responses.
     */
    private final ExecutorService responseExecutor;

    /**
     * An atomic boolean that determines if the `AgentSender` is running.
     */
    private final AtomicBoolean running;

    /**
     * An `HttpClient` used to send HTTP requests.
     */
    private final HttpClient httpClient;

    /**
     * The thread that the `AgentSender` runs on.
     */
    private final Thread thread;

    /**
     * Creates a new `AgentSender` instance with a specified maximum queue size.
     *
     * @param maxQueueSize  the maximum size of the incoming and outgoing queues.
     */
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

    /**
     * Adds a request to the `incoming` queue.
     *
     * @param request  the HTTP request to send to the server.
     * @throws Exception  if there is an error adding the request to the queue.
     */
    public void send(Request request) throws Exception {
        incoming.add(request);
    }

    /**
     * Polls the `outgoing` queue for a specified amount of time to retrieve responses synchronously.
     *
     * @param timeout   the maximum amount of time to wait for a response.
     * @param timeUnit  the unit of time for the timeout.
     * @return a list of available HTTP responses or an empty list if no responses are available.
     * @throws InterruptedException  if the thread is interrupted while waiting for a response.
     */
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


    /**

     Continuously retrieves the next request from the incoming queue and sends it
     as an HTTP request to the remote server. The response is sent to the outgoing queue.
     This method is called by the worker thread when it is started.
     */
    private void run() {
        while (running.get()) {
            Request request = getMail();
            if (request == null) {
                continue;
            }
            sendMail(request);
        }
    }

    /**
     * Retrieves the next mail request from the incoming queue. This method blocks until a
     * request is available in the queue or the thread is interrupted.
     *
     * @return the next mail request or null if the thread is interrupted
     */
    private Request getMail() {
        Request request = null;
        try {
            request = incoming.take();
        } catch (InterruptedException e) {
            running.set(false);
        }
        return request;
    }

    /**
     * Sends an HTTP request to the given URL with the given method and headers, and returns the response as a Response object.
     *
     * @param request the request to send
     * @throws RuntimeException if there was an error sending the HTTP request
     */
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

    /**
     * Constructs an HttpRequest object from the given request object.
     *
     * @param request the request to convert
     * @return the resulting HttpRequest object
     */
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

    /**
     * Adds a Response object to the outgoing queue.
     *
     * @param id the ID of the request associated with the response
     * @param response the response to add to the outgoing queue
     */
    private void sendResponse(String id, HttpResponse<String> response) {
        outgoing.add(new Response(id, response.statusCode(), response.body()));
    }

    /**
     * Adds an error Response object to the outgoing queue.
     *
     * @param id the ID of the request associated with the error response
     * @param e the exception that caused the error
     */
    private void sendErrorResponse(String id, Exception e) {
        outgoing.add(new Response(id, 500, "agent failed: " + e.getMessage()));
    }

    /**
     * Extracts the headers from the given request object and returns them as an array of strings.
     *
     * @param request the request to extract headers from
     * @return the headers as an array of strings
     */
    private static String[] getHeaders(Request request) {
        String headers = request.headers();
        if(headers == null || headers.isEmpty()){
            return new String[0];
        }

        return headers.split(",");
    }

    /**
     Closes the AgentSender by setting the running flag to false, shutting down the request and response executors,
     and joining the worker thread.
     @throws InterruptedException if the thread is interrupted while waiting for the worker thread to complete.
     */
    @Override
    public void close() throws InterruptedException {
        running.set(false);
        requestExecutor.shutdown();
        responseExecutor.shutdown();
        thread.join();
    }
}