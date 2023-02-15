import com.k2view.agent.AgentSender;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class K2ViewAgent {
    public static void main(String[] args) {
        try (AgentSender agentSender = new AgentSender(10)) {
            AgentSender.Request request = new AgentSender.Request(
                    "1",
                    "http://127.0.0.1:5000/",
                    "GET",
                    "{}",
                    "{1:2, 2:3}"
            );
            agentSender.send(request);

            List<AgentSender.Response> responses = agentSender.receive(10, TimeUnit.SECONDS);
            if (!responses.isEmpty()) {
                AgentSender.Response response = responses.get(0);
                System.out.printf("Received response: [id=%s, code=%d, body=%s]%n",
                        response.id(), response.code(), response.body());
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