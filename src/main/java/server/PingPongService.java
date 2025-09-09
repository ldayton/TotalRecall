package server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class PingPongService {
    private final ObjectMapper mapper;

    public PingPongService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String process(String requestJson) {
        try {
            JsonNode request = mapper.readTree(requestJson);
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", request.get("id"));

            String method = request.path("method").asText(null);
            if ("ping".equals(method)) {
                response.put("result", "pong");
            } else {
                ObjectNode error = mapper.createObjectNode();
                error.put("code", -32601);
                error.put("message", "Method not found");
                response.set("error", error);
            }
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }
}
