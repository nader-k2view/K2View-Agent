package com.k2view.agent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K2ViewAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(K2ViewAgent.class);


    public static void main(String[] args) {
        LOGGER.
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
        thread2.start();
    }


    private static ArrayList<String> getUrls() {
        // Replace this code with the logic to read the URLs from the REST API
        final String url = "http://127.0.0.1:5000/";
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
//                System.out.println(t);
            }
            return urls;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
