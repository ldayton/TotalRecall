package server;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {TotalRecallApplication.class, PingPongService.class})
class PingPongIT {

    @Autowired private PingPongService service;

    @Test
    void pingPong() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":1}";
        String resp = service.process(req);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(resp);
        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(1, json.get("id").asInt());
        assertEquals("pong", json.get("result").asText());
        assertFalse(json.has("error"));
    }

    @Test
    void unknownMethod() throws Exception {
        String req = "{\"jsonrpc\":\"2.0\",\"method\":\"unknown\",\"id\":42}";
        String resp = service.process(req);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(resp);
        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(42, json.get("id").asInt());
        assertTrue(json.has("error"));
        JsonNode err = json.get("error");
        assertEquals(-32601, err.get("code").asInt());
        assertTrue(err.has("message"));
    }
}
