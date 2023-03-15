package com.k2view.agent.postman;

import com.k2view.agent.Requests;
import com.k2view.agent.Response;
import com.k2view.agent.Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.net.http.HttpRequest.BodyPublishers.ofString;

public class CloudManager implements Postman {

    /**
     * The ID of the mailbox used for receiving messages.
     */
    private final String mailboxId;

    private final URI uri;

    /**
     * An instance of the Java HTTP client for sending HTTP requests.
     */
    private final HttpClient client;

    public CloudManager(String mailboxId, String mailboxUrl) {
        this.mailboxId = mailboxId;
        this.uri = URI.create(mailboxUrl);
        this.client = HttpClient.newBuilder().build();
    }

    @Override
    public Requests getInboxMessages(List<Response> responses, String lastTaskId) {
        Utils.logMessage("INFO", "FETCHING MESSAGES FROM: " + uri.toString() + ", ID:" + mailboxId);
        Map<String, Object> r = new HashMap<>();
        r.put("responses", responses);
        r.put("id", mailboxId);
        r.put("since", lastTaskId);
        String body = Utils.gson.toJson(r);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(ofString(body))
                .uri(uri)
                .header("Content-Type", "application/json")
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String jsonArrayString = response.body();
            return Utils.gson.fromJson(jsonArrayString, Requests.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
