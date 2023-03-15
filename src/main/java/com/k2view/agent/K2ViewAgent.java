package com.k2view.agent;

import com.k2view.agent.dispatcher.AgentDispatcher;
import com.k2view.agent.dispatcher.AgentDispatcherHttp;
import com.k2view.agent.postman.CloudManager;
import com.k2view.agent.postman.Postman;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.k2view.agent.Utils.def;
import static com.k2view.agent.Utils.env;
import static java.lang.Integer.parseInt;

/**
 * The K2ViewAgent class represents an agent that reads a list of URLs from a REST API and forwards the requests to external URLs via AgentSender object.
 * This class uses the Gson library for JSON serialization and the HttpClient class for sending HTTP requests.
 */
public class K2ViewAgent {

    /**
     * The polling interval in seconds for checking the inbox for new messages.
     */
    private final int pollingInterval;

    /**
     * The `AgentSender` instance used for sending requests and processing responses.
     */
    private final AgentDispatcher dispatcher;

    /**
     * The `Postman` instance used for fetching messages from the REST API.
     */
    private final Postman postman;

    private final Executor executor;

    /**
     * An atomic boolean that determines if the `K2ViewAgent` is running.
     */
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final CountDownLatch latch = new CountDownLatch(1);

    public K2ViewAgent(Postman postman, int pollingInterval, AgentDispatcher agentSender, Executor executor) {
        this.postman = postman;
        this.dispatcher = agentSender;
        this.pollingInterval = pollingInterval;
        this.executor = executor;
    }


    /**
     * Starts the agent by initializing the `agentSender`, `id`, and `since` fields,
     * and calling the `start()` method.
     */
    public static void main(String[] args) throws InterruptedException {
        AgentDispatcherHttp sender = new AgentDispatcherHttp(10_000);
        Postman postman = new CloudManager(env("K2_MAILBOX_ID"), env("K2_MANAGER_URL"));
        int interval = parseInt(def(env("K2_POLLING_INTERVAL"), "10"));
        Executor executor = (Runnable r) -> new Thread(r, "MANAGER").start();
        new K2ViewAgent(postman, interval, sender, executor).run();
    }

    /**
     * Starts the manager thread that continuously checks for new inbox messages
     * and sends them to the `agentSender` for processing.
     */
    public void run() throws InterruptedException {
        executor.execute(() -> {
            try {
                List<Response> responseList = new ArrayList<>();
                long interval = pollingInterval;
                String lastTaskId = "";
                while (running.get()) {
                    Requests inboxMessages = postman.getInboxMessages(responseList, lastTaskId);
                    if (inboxMessages != null) {
                        interval = inboxMessages.pollInterval() > 0 ? inboxMessages.pollInterval() : pollingInterval;
                        for (Request req : inboxMessages.requests()) {
                            lastTaskId = req.taskId();
                            dispatcher.send(req);
                            Utils.logMessage("INFO", "Added URL to the Queue:" + req);
                        }
                    }
                    responseList = dispatcher.receive(interval, TimeUnit.SECONDS);
                    Utils.logMessage("INFO", responseList.toString());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
    }

    /**
     * Stops the agent by setting the `running` flag to `false`.
     */
    public void stop() {
        running.set(false);
    }
}


