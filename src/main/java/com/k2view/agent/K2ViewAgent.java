package com.k2view.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

import static java.net.http.HttpRequest.BodyPublishers.ofString;

/**
 * The main class for the K2ViewAgent program.
 */
public class K2ViewAgent {

    /**
     * The logger used for the K2ViewAgent program.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(K2ViewAgent.class);

    /**
     * The polling interval in seconds for the K2ViewAgent program.
     */
    private static final int pollingInterval = 60;
    private static AgentSender agentSender;

    static String id;
    static long since;

    static Gson gson = new Gson();
    private static HttpClient client = HttpClient.newBuilder().build();

    /**
     * The queue that holds the requests to be processed by the K2ViewAgent program.
     */
    //private static ConcurrentLinkedQueue<Map<String, Object>> requestsQueue = new ConcurrentLinkedQueue<>();
    /**
     * The main method that runs the K2ViewAgent program.
     *
     * @param args The command line arguments for the program.
     */
    public static void main(String[] args) {
        // Start the first thread that reads a list of URLs
        agentSender = new AgentSender(10_000);
        id = System.getenv("K2_MAILBOX_ID");
        since = 0;
        start();
    }

    private static void start() {
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
     * Gets a list of requests from the manager's inbox via REST API.
     *
     * @return A list of requests as a list of maps, or null if an error occurred.
     */
    private static List<AgentSender.Request> getInboxMessages(List<AgentSender.Response> responses) {
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

    class ManagerMessage {
        List<AgentSender.Request> tasks;
    }

}
