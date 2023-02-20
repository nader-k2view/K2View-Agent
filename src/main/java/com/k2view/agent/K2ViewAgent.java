package com.k2view.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

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

    /**
     * The queue that holds the requests to be processed by the K2ViewAgent program.
     */
    private static ConcurrentLinkedQueue<Map<String, Object>> requestsQueue = new ConcurrentLinkedQueue<>();

    /**
     * The main method that runs the K2ViewAgent program.
     *
     * @param args The command line arguments for the program.
     */
    public static void main(String[] args) {
        // Start the first thread that reads a list of URLs
        Thread managerThread = new Thread(() -> {
            while (true) {
                List<Map<String, Object>> requests = getInboxMessages();

                for (Map<String, Object> req : requests) {
                    requestsQueue.add(req);
                    LOGGER.info("Added URL to the Queue:" + req);
                }

                try {
                    TimeUnit.SECONDS.sleep(pollingInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        managerThread.setName("MANAGER");
        managerThread.start();

        // Start the second thread that polls the queue and GETs the URLs
        Thread workerThread = new Thread(() -> {
          AgentSender agentSender = new AgentSender(10);
           while (true) {
                int requestsCounter = 0;
                while (!requestsQueue.isEmpty()) {
                    requestsCounter ++;
                    Map<String, Object> req = requestsQueue.poll();
                    String url = req.get("url").toString().trim();
                    ///
                    AgentSender.Request request = new AgentSender.Request(
                            req.get("id").toString(),
                            url,
                            req.get("method").toString(),
                            "",
                            ""
                    );
                    try {
                        agentSender.send(request);

                    } catch (InterruptedException e) {
                        System.err.println("Interrupted while waiting for response: " + e.getMessage());
                    }catch (Exception e) {
                        System.err.println("Error while sending request: " + e.getMessage());
                    }
                }
                ///
               CompletableFuture<List<AgentSender.Response>> responses = agentSender.receiveAsync(1, TimeUnit.SECONDS);
               List<AgentSender.Response> mailResponses = null;
               try {
                   mailResponses = responses.get();
                   int numberOfResponses = 0;
                   if (! mailResponses.isEmpty()) {
                       Map<String, Object> responseMap = new HashMap<>();
                       for (AgentSender.Response response: mailResponses) {
                           responseMap.put("id", (int)Double.parseDouble(response.id()));
                           responseMap.put("body", response.body().trim());
                           responseMap.put("status", response.code());
                           JsonObject responseJson = new Gson().toJsonTree(responseMap).getAsJsonObject();
                           System.out.println(responseJson);
                           numberOfResponses++;
                           if (numberOfResponses == requestsCounter) agentSender.close();

                       }
                   }

               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               } catch (ExecutionException e) {
                   throw new RuntimeException(e);
               }
           }

        });
        workerThread.setName("WORKER");
        workerThread.start();

    }

    /**
     * Gets a list of requests from the manager's inbox via REST API.
     *
     * @return A list of requests as a list of maps, or null if an error occurred.
     */
    private static List<Map<String, Object>> getInboxMessages() {
        // Replace this code with the logic to read the URLs from the REST API
        String url = System.getenv("K2VIEW_MANAGER_URL");

        LOGGER.info("MANAGER URL: " + url);
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonArrayString = response.body();
            Gson gson = new Gson();
            List<Map<String, Object>> bodyToJson = gson.fromJson(jsonArrayString, new TypeToken<List<Map<String, Object>>>() {}.getType());
            return bodyToJson;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}
