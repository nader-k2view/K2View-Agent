package com.k2view.agent;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.k2view.agent.Utils.dynamicString;

/**
 * Represents an HTTP request that is sent to the server.
 */
public record Request(String taskId, String url, String method, Map<String, Object> header, String body) {

    public static class Adapter extends TypeAdapter<Request> {
        @Override
        public void write(JsonWriter jsonWriter, Request request) throws IOException {
            jsonWriter.beginObject();
            jsonWriter.name("taskId").value(request.taskId());
            jsonWriter.name("url").value(request.url());
            jsonWriter.name("method").value(request.method());
            jsonWriter.name("header").value(request.header().toString());
            jsonWriter.name("body").value(request.body());
            jsonWriter.endObject();
        }

        @Override
        public Request read(JsonReader jsonReader) throws IOException {
            jsonReader.beginObject();
            String taskId = null;
            String url = null;
            String method = null;
            Map<String, Object> header = new HashMap<>();
            String body = null;

            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                switch (name) {
                    case "taskId" -> taskId = jsonReader.nextString();
                    case "url" -> url = jsonReader.nextString();
                    case "method" -> method = jsonReader.nextString();
                    case "header" -> {
                        jsonReader.beginObject();
                        while (jsonReader.hasNext()) {
                            String key = jsonReader.nextName();
                            if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                                List<String> l = new ArrayList<>();
                                jsonReader.beginArray();
                                while (jsonReader.hasNext()) {
                                    l.add(jsonReader.nextString());
                                }
                                jsonReader.endArray();
                                header.put(key, l);
                            } else {
                                header.put(key, dynamicString(jsonReader.nextString()));
                            }
                        }
                        jsonReader.endObject();
                    }
                    case "body" -> body = jsonReader.nextString();
                    default -> jsonReader.skipValue();
                }
            }
            jsonReader.endObject();
            return new Request(taskId, dynamicString(url), method, header, dynamicString(body));
        }
    }
}
