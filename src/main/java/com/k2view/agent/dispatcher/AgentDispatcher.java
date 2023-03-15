package com.k2view.agent.dispatcher;

import com.k2view.agent.Request;
import com.k2view.agent.Response;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Represents a dispatcher that is used to send and receive HTTP requests and responses.
 */
public interface AgentDispatcher extends AutoCloseable {

    /**
     * sends an HTTP request to the server.
     *
     * @param request the HTTP request to send to the server.
     */
    void send(Request request);

    /**
     * Returns a list of available HTTP responses.
     *
     * @param timeout   the maximum amount of time to wait for a response.
     * @param timeUnit  the unit of time for the timeout.
     * @return a list of available HTTP responses or an empty list if no responses are available.
     * @throws InterruptedException  if the thread is interrupted while waiting for a response.
     */
    List<Response> receive(long timeout, TimeUnit timeUnit) throws InterruptedException;
}
