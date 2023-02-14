package com.k2view.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;


public class K2ViewAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(K2ViewAgent.class);
    private static final Map<String, Object> config = YamlToMap("k2view-agent.yaml");

    private static final int pollingInterval = 60;
    private static ConcurrentLinkedQueue<Map<String, Object>> requestsQueue = new ConcurrentLinkedQueue<>();
    private static ConcurrentLinkedQueue<JsonObject> responseQueue = new ConcurrentLinkedQueue<>();

    public static void main(String[] args) {

//        ConcurrentLinkedQueue<String> requestsQueue = new ConcurrentLinkedQueue<>();

        // Start the first thread that reads a list of URLs
        Thread managerThread = new Thread(() -> {
            while (true) {
                List<Map<String, Object>> requests = getRequests();
                ArrayList<String> urls = getUrls(requests);

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
            while (true) {
//                LOGGER.debug("Queue Length:" + requestsQueue.size());

                while (!requestsQueue.isEmpty()) {
                    Map<String, Object> req = requestsQueue.poll();
                    String url = req.get("url").toString().trim();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .method(req.get("method").toString().trim(), HttpRequest.BodyPublishers.noBody())
                            .build();

                    new Thread(() -> {
                        try {
                                HttpClient client = HttpClient.newBuilder().build();
                                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                                Map<String, Object> responseMap = new HashMap<>();

                                responseMap.put("id", req.get("id"));
                                responseMap.put("body", response.body().trim());
                                responseMap.put("status", Integer.toString(response.statusCode()));

                                JsonObject responseJson = new Gson().toJsonTree(responseMap).getAsJsonObject();

                                responseQueue.add(responseJson);

                                LOGGER.debug("Response: " + responseJson);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
                // Sleep for 1 Second - Reduce CPU usage
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        workerThread.setName("WORKER");
        workerThread.start();
    }

    private static List<Map<String, Object>> getRequests() {
        // Replace this code with the logic to read the URLs from the REST API
        String url = config.get("MANAGER_URL").toString();

        LOGGER.info("MANAGER URL: " + url);
        HttpClient client = HttpClient.newBuilder().build();
        ArrayList<String> urls = new ArrayList<>();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonArrayString = response.body();
            Gson gson = new Gson();
            List<Map<String, Object>> listOfMaps = gson.fromJson(jsonArrayString, new TypeToken<List<Map<String, Object>>>() {}.getType());
            return listOfMaps;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static ArrayList<String> getUrls(List<Map<String, Object>> requests) {
        // Replace this code with the logic to read the URLs from the REST API
        ArrayList<String> urls = new ArrayList<>();

        for (Map<String, Object> t: requests) {
            urls.add(t.get("url").toString());
        }
        return urls;
    }

    private static Map<String, Object> YamlToMap(String path) {
        Yaml yaml = new Yaml();
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(path);
            return yaml.load(inputStream);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
