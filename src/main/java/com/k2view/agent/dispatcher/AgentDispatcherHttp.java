package com.k2view.agent.dispatcher;

import com.k2view.agent.Request;
import com.k2view.agent.Response;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.k2view.agent.Utils.dynamicString;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.net.http.HttpResponse.BodyHandlers.ofString;


/**
 * AgentSender Class provides an asynchronous mechanism for sending HTTP requests and receiving their responses.
 * The class has a public method named send which accepts an HTTP request, and adds it to an incoming LinkedBlockingQueue. The requests are handled by a worker thread that continuously retrieves the next request from the incoming queue and sends it as an HTTP request to the remote server. The response is then added to an outgoing LinkedBlockingQueue.
 * The class provides two methods for retrieving responses, receive and receiveAsync. The receive method blocks until a response is available in the outgoing queue, or until the specified timeout is reached, and then returns the available responses as a list. The receiveAsync method returns a CompletableFuture that completes when a response is available in the outgoing queue, or when the specified timeout is reached.
 * The class has an inner class Request that represents an HTTP request, and a Response record that represents an HTTP response received from the server.
 * The class uses HttpClient to send HTTP requests and handle outgoing responses. It also uses two ExecutorServices to handle incoming requests and outgoing responses.
 * The class implements the AutoCloseable interface and provides a close method to close the AgentSender. This method sets the running flag to false, shuts down the request and response executors, and joins the worker thread.
 */
public class AgentDispatcherHttp implements AgentDispatcher {


    /**
     * A blocking queue that stores outgoing responses.
     */
    private final BlockingQueue<Response> outgoing;

    /**
     * An `ExecutorService` that handles incoming requests.
     */
    private final ExecutorService requestExecutor;

    /**
     * An atomic boolean that determines if the `AgentSender` is running.
     */
    private final AtomicBoolean running;

    /**
     * An `HttpClient` used to send HTTP requests.
     */
    private final HttpClient httpClient;

    /**
     * Creates a new `AgentSender` instance with a specified maximum queue size.
     *
     * @param maxQueueSize the maximum size of the incoming and outgoing queues.
     */
    public AgentDispatcherHttp(int maxQueueSize) {
        outgoing = new LinkedBlockingQueue<>(maxQueueSize);
        requestExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "AgentSender-RequestExecutor"));
        running = new AtomicBoolean(true);
        httpClient = initHttpClient();
    }

    private HttpClient initHttpClient() {
        try {
            return HttpClient.newBuilder()
                    .sslContext(noCertificateCheckSSLContext())
                    .executor(requestExecutor)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize http client", e);
        }
    }

    /**
     * Adds a request to the `incoming` queue.
     *
     * @param request the HTTP request to send to the server.
     */
    public void send(Request request) {
        sendMail(request);
    }

    /**
     * Polls the `outgoing` queue for a specified amount of time to retrieve responses synchronously.
     *
     * @param timeout  the maximum amount of time to wait for a response.
     * @param timeUnit the unit of time for the timeout.
     * @return a list of available HTTP responses or an empty list if no responses are available.
     * @throws InterruptedException if the thread is interrupted while waiting for a response.
     */
    public List<Response> receive(long timeout, TimeUnit timeUnit) throws InterruptedException {
        // pull from outgoing queue
        List<Response> responses = new ArrayList<>();
        Response response = outgoing.poll(timeout, timeUnit);
        if (response == null) {
            return responses;
        }

        responses.add(response);
        outgoing.drainTo(responses);
        return responses;
    }

    /**
     * Sends an HTTP request to the given URL with the given method and header, and returns the response as a Response object.
     *
     * @param request the request to send
     * @throws RuntimeException if there was an error sending the HTTP request
     */
    private void sendMail(Request request) {
        try {
            httpClient.sendAsync(getHttpRequest(request), ofString())
                    .thenAccept(r -> sendResponse(request.taskId(), r))
                    .exceptionally(e -> {
                        System.out.printf("Failed to send mail[taskId:%s, url:%s, method:%s], error: %s%n",
                                request.taskId(), request.url(), request.method(), e.getMessage());
                        sendErrorResponse(request.taskId(), e);
                        return null;
                    });
        } catch (Exception e) {
            System.out.printf("Failed to send mail[taskId:%s, url:%s, method:%s], error: %s%n",
                    request.taskId(), request.url(), request.method(), e.getMessage());
            sendErrorResponse(request.taskId(), e);
        }
    }

    /**
     * Constructs an HttpRequest object from the given request object.
     *
     * @param request the request to convert
     * @return the resulting HttpRequest object
     */
    private static HttpRequest getHttpRequest(Request request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(dynamicString(request.url())))
                .method(request.method(), ofString(dynamicString(request.body())));

        Map<String, Object> header = request.header();
        for (Map.Entry<String, Object> entry : header.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List<?> l) {
                l.forEach(s -> builder.header(entry.getKey(), dynamicString(s.toString())));
            } else {
                builder.header(entry.getKey(), dynamicString(value.toString()));
            }
        }
        return builder.build();
    }

    /**
     * Adds a Response object to the outgoing queue.
     *
     * @param id       the ID of the request associated with the response
     * @param response the response to add to the outgoing queue
     */
    private void sendResponse(String id, HttpResponse<String> response) {
        outgoing.add(new Response(id, response.statusCode(), response.body()));
    }

    /**
     * Adds an error Response object to the outgoing queue.
     *
     * @param id the ID of the request associated with the error response
     * @param e  the exception that caused the error
     */
    private void sendErrorResponse(String id, Throwable e) {
        outgoing.add(new Response(id, 500, "K2View Agent Failed: " + e.getMessage()));
    }

    /**
     * Closes the AgentSender by setting the running flag to false, shutting down the request and response executors,
     * and joining the worker thread.
     */
    @Override
    public void close() {
        running.set(false);
        requestExecutor.shutdown();
    }

    private static SSLContext noCertificateCheckSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509ExtendedTrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
                        // trust all
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
                        // trust all
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                        // trust all
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                        // trust all
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // trust all
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // trust all
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }
}