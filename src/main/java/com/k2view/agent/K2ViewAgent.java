package com.k2view.agent;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

import static java.net.http.HttpRequest.BodyPublishers.ofString;

/**
 The K2ViewAgent class represents an agent that reads a list of URLs from a REST API and forwards the requests to external URLs via AgentSender object.
 This class uses the Gson library for JSON serialization and the HttpClient class for sending HTTP requests.
 */
public class K2ViewAgent {

    /**
     * A logger for the `K2ViewAgent` class.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(K2ViewAgent.class);

    /**
     * The polling interval in seconds for checking the inbox for new messages.
     */
    private int pollingInterval = 60;

    /**
     * The `AgentSender` instance used for sending requests and processing responses.
     */
    private AgentSender agentSender;

    /**
     * The ID of the mailbox used for receiving messages.
     */
    private String id;

    /**
     * The timestamp of the most recent inbox message received.
     */
    private long since;

    /**
     * An instance of the Google Gson library for JSON serialization/deserialization.
     */
    private Gson gson = new Gson();

    /**
     * An instance of the Java HTTP client for sending HTTP requests.
     */
    private HttpClient client = HttpClient.newBuilder().build();

    /**
     * Starts the agent by initializing the `agentSender`, `id`, and `since` fields,
     * and calling the `start()` method.
     */
    public void run() {
        // Start the first thread that reads a list of URLs
        agentSender = new AgentSender(10_000);
        id = System.getenv("K2_MAILBOX_ID");
        since = 0;
        start();
    }

    /**
     * Starts the manager thread that continuously checks for new inbox messages
     * and sends them to the `agentSender` for processing.
     */
    private void start() {
        Thread managerThread = new Thread(() -> {
            List<AgentSender.Response> responseList = new ArrayList<>();
            while (!Thread.interrupted()) {
                List<AgentSender.Request> requests = getInboxMessages(responseList);
                for (AgentSender.Request req : requests) {
                    try {
                        agentSender.send(req);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    //requestsQueue.add(req);
                    LOGGER.info("Added URL to the Queue:" + req);
                }

                try {
                    responseList = agentSender.receive(pollingInterval, TimeUnit.SECONDS);
                    responseList.forEach(System.out::println);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        managerThread.setName("MANAGER");
        managerThread.start();
    }

    /**
     * Retrieves a list of inbox messages from the REST API.
     *
     * @param responses a list of previous responses received from the server
     * @return a list of `AgentSender.Request` objects
     */
    private List<AgentSender.Request> getInboxMessages(List<AgentSender.Response> responses) {
        // Replace this code with the logic to read the URLs from the REST API
        String url = System.getenv("K2_MANAGER_URL");

        LOGGER.info("MANAGER URL: " + url);

        Map<String,Object> r = new HashMap<>();
        r.put("responses", responses);
        r.put("id", id);
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
    class ManagerMessage {
        List<AgentSender.Request> tasks;
    }

}
