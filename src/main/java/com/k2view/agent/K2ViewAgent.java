package com.k2view.agent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.k2view.agent.AgentSender;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class K2ViewAgent {
    public static void main(String[] args) {
            try (AgentSender agentSender = new AgentSender(10)) {
                AgentSender.Request request = new AgentSender.Request(
                        "1",
                        "http://127.0.0.1:5000/",
                        "GET",
                        "",
                        ""
                );
                agentSender.send(request);

                List<AgentSender.Response> responses = agentSender.receive(10, TimeUnit.SECONDS);
                if (!responses.isEmpty()) {
                    AgentSender.Response response = responses.get(0);
                    String jsonArrayString = response.body();
                    Gson gson = new Gson();
                    List<Map<String, Object>> requestsMap = gson.fromJson(jsonArrayString, new TypeToken<List<Map<String, Object>>>() {}.getType());
                    System.out.printf("Received response: [id=%s, code=%d, body=%s]%n",
                            response.id(), response.code(), response.body());
                    System.out.println(requestsMap.toString());
                    for (Map<String, Object> requestItem : requestsMap) {
                        AgentSender.Request agentRequest = new AgentSender.Request(
                                requestItem.get("id").toString(),
                                "http://127.0.0.1:5000/",
                                "GET",
                                "",
                                ""
                        );
//                        requestsQueue.add(req);
                    }



                } else {
                    System.out.println("No response received within timeout");
                }
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for response: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Error while sending request: " + e.getMessage());
            }
        }
}