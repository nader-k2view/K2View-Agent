package com.k2view.agent;

/**
 * Represents an HTTP response received from the server.
 */
public record Response(String taskId, int code, String body) {
}
