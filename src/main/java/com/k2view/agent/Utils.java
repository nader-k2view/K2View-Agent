package com.k2view.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;
import java.util.function.UnaryOperator;

public class Utils {

    /**
     * An instance of the Google Gson library for JSON serialization/deserialization.
     */
    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Request.class, new Request.Adapter())
            .registerTypeAdapter(Requests.class, new Requests.Adapter())
            .create();

    /**
     * Get a value from a map, if not exists, return the default value
     */
    public static <T> T def(T val, T def) {
        return val == null ? def : val;
    }

    /**
     * Build a dyamicString based on the env() function
     */
    public static String dynamicString(String s) {
        return dynamicString(s, r -> def(env(r), ""));
    }
    /**
     * Get an environment variable from the Property (-D) and if not exists, from the environment
     */
    public static String env(String key) {
        return def(System.getProperty(key), System.getenv(key));
    }

    /*
     * Parse a statement that is either $REPLACE_ME or contains ${REPLACE_ME} tokens inside it
     * The content of ${} can contain a balanced set of '{' '}'. Escaping {} inside the REPLACE_ME area is not supported.
     * Call the replace with function passing the content and expecting a replacement.
     */
    public static String dynamicString(String s, UnaryOperator<String> replaceWith) {
        if (s.length() >= 2 && s.charAt(0) == '$' && Character.isJavaIdentifierPart(s.charAt(1))) {
            return replaceWith.apply(s.substring(1));
        }

        StringBuilder variable = new StringBuilder();
        StringBuilder result = new StringBuilder();
        int state = 0;
        // 0 - regular, 1 found $ waiting for {, 2 and up inside the variable counting { depth
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '$' -> {
                    if (state == 0) {
                        state = 1;
                    }
                }
                case '{' -> {
                    if (state > 0) {
                        state++;
                    }
                }
                case '}' -> {
                    if (state >= 2 && --state == 1) {
                        state = 0;
                        // we remove the $ and the variable content (variable starts at first { // NO SONAR
                        result.setLength(result.length() - 1 - variable.length());

                        // We remove the first { , use the lambda to get the value and reset the variable
                        String v = variable.substring(1);
                        result.append(replaceWith.apply(v));
                        variable.setLength(0);
                        continue;
                    }
                }
                default -> {
                    if (state == 1) {
                        state = 0;
                    }
                }
            }
            result.append(c);
            if (state >= 2) {
                variable.append(c);
            }
        }
        return result.toString();
    }

    /**
     * A nested class representing a manager message.
     */
    public static void logMessage(String severity, String message) {
        LocalDateTime timestamp = LocalDateTime.now();
        String threadName = Thread.currentThread().getName();
        String callerMethodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        String logMessage = String.format("%s %s %s %s  %s", timestamp, threadName, severity, callerMethodName, message);
        System.out.println(logMessage);
    }
}
