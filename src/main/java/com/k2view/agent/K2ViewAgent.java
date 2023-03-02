package com.k2view.agent;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import static java.net.http.HttpRequest.BodyPublishers.ofString;

/**
 The K2ViewAgent class represents an agent that reads a list of URLs from a REST API and forwards the requests to external URLs via AgentSender object.
 This class uses the Gson library for JSON serialization and the HttpClient class for sending HTTP requests.
 */
public class K2ViewAgent {

    /**
     * The polling interval in seconds for checking the inbox for new messages.
     */
    private final int pollingInterval = 60;

    /**
     * The `AgentSender` instance used for sending requests and processing responses.
     */
    private final AgentSender agentSender;

    /**
     * The ID of the mailbox used for receiving messages.
     */
    private final String mailboxId;

    /**
     * The timestamp of the most recent inbox message received.
     */
    private long since;

    /**
     * An instance of the Google Gson library for JSON serialization/deserialization.
     */
    private final Gson gson = new Gson();

    /**
     * An instance of the Java HTTP client for sending HTTP requests.
     */
    private final HttpClient client = HttpClient.newBuilder().build();

    public K2ViewAgent() {
        int maxQueueSize = 10_000;
        this.agentSender = new AgentSender(maxQueueSize);
        this.mailboxId = System.getenv("K2_MAILBOX_ID");
        this.since = 0;
    }


    /**
     * Starts the agent by initializing the `agentSender`, `id`, and `since` fields,
     * and calling the `start()` method.
     */
    public static void main(String[] args) throws InterruptedException {
        K2ViewAgent k2view = new K2ViewAgent();
        k2view.start();
    }

    /**
     * Starts the manager thread that continuously checks for new inbox messages
     * and sends them to the `agentSender` for processing.
     */
    private void start() throws InterruptedException {
        Thread managerThread = new Thread(() -> {
        try {
            List<AgentSender.Response> responseList = new ArrayList<>();
            while (!Thread.interrupted()) {
                List<AgentSender.Request> requests = getInboxMessages(responseList);
                if (requests != null) {
                    for (AgentSender.Request req : requests) {
                        agentSender.send(req);

                        logMessage("INFO", "Added URL to the Queue:" + req);
                    }
                }

                responseList = agentSender.receive(pollingInterval, TimeUnit.SECONDS);
                logMessage("INFO", responseList.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
//            logMessage("ERROR", e.printStackTrace(););
        }
        });
        managerThread.setName("MANAGER");
        managerThread.start();
        managerThread.join();
    }

    /**
     * Retrieves a list of inbox messages from the REST API.
     *
     * @param responses a list of previous responses received from the server
     * @return a list of `AgentSender.Request` objects
     */
    private List<AgentSender.Request> getInboxMessages(List<AgentSender.Response> responses) {
        // Replace this code with the logic to read the URLs from the REST API
        String url = System.getenv("K2_MANAGER_URL").trim();
        logMessage("INFO", "FETCHING MESSAGES FROM: " + url);

        Map<String,Object> r = new HashMap<>();
        r.put("responses", responses);
        r.put("id", mailboxId);
        r.put("since", since);
        String body = gson.toJson(r);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(ofString(body))
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonArrayString = response.body();
            since = System.currentTimeMillis();
            ManagerMessage mail = gson.fromJson(jsonArrayString, ManagerMessage.class);
            return mail.tasks;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * A nested class representing a manager message.
     */
    public void logMessage(String severity, String message) {
            LocalDateTime timestamp = LocalDateTime.now();
            String threadName = Thread.currentThread().getName();
            String callerMethodName = Thread.currentThread().getStackTrace()[2].getMethodName();
            String logMessage = String.format("%s %s %s %s  %s", timestamp, threadName, severity, callerMethodName, message);
            System.out.println(logMessage);
        }
        static class ManagerMessage {
        List<AgentSender.Request> tasks;
    }

}


