package com.k2view.agent;

import com.google.gson.Gson;
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

    public static void main(String[] args) {

        int pollingInterval = 60;
        Queue<String> queue = new ConcurrentLinkedQueue<>();

        // Start the first thread that reads a list of URLs
        Thread thread1 = new Thread(() -> {
            while (true) {
                ArrayList<String> urls = getUrls();
                if (urls != null) {
                    for (String url : urls) {
                        queue.offer(url);
                        LOGGER.debug("Added URL to the Queue:" + url);
                    }
                }
                try {
                    TimeUnit.SECONDS.sleep(pollingInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread1.setName("MANAGER");
        thread1.start();

        // Start the second thread that polls the queue and GETs the URLs
        Thread thread2 = new Thread(() -> {
            HttpClient client = HttpClient.newBuilder().build();
            while (true) {
                String url = queue.poll();
                if (url != null) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(url.trim()))
                            .build();
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        LOGGER.debug("GET " + url + ": " + response.body().trim());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread2.setName("WORKER");
        thread2.start();
    }


    private static ArrayList<String> getUrls() {
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

            for (Map<String, Object> t: listOfMaps) {
                urls.add(t.get("url").toString());
            }
            return urls;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
