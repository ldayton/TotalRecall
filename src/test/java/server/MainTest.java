package server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void testPingPong() throws Exception {
        // Prepare input
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":1}";

        // Capture output
        ByteArrayInputStream input = new ByteArrayInputStream((request + "\n").getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setIn(input);
            System.setOut(new PrintStream(output));

            // Run main in a separate thread with timeout
            Thread mainThread =
                    new Thread(
                            () -> {
                                try {
                                    Main.main(new String[] {});
                                } catch (Exception e) {
                                    fail("Main threw exception: " + e.getMessage());
                                }
                            });
            mainThread.start();

            // Give it time to process
            Thread.sleep(100);
            mainThread.interrupt();

            // Parse response
            String response = output.toString().trim();
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

            // Verify response
            assertEquals("2.0", jsonResponse.get("jsonrpc").getAsString());
            assertEquals(1, jsonResponse.get("id").getAsInt());
            assertEquals("pong", jsonResponse.get("result").getAsString());
            assertFalse(jsonResponse.has("error"));

        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testUnknownMethod() throws Exception {
        // Prepare input
        String request = "{\"jsonrpc\":\"2.0\",\"method\":\"unknown\",\"id\":42}";

        // Capture output
        ByteArrayInputStream input = new ByteArrayInputStream((request + "\n").getBytes());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setIn(input);
            System.setOut(new PrintStream(output));

            // Run main in a separate thread with timeout
            Thread mainThread =
                    new Thread(
                            () -> {
                                try {
                                    Main.main(new String[] {});
                                } catch (Exception e) {
                                    fail("Main threw exception: " + e.getMessage());
                                }
                            });
            mainThread.start();

            // Give it time to process
            Thread.sleep(100);
            mainThread.interrupt();

            // Parse response
            String response = output.toString().trim();
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

            // Verify response
            assertEquals("2.0", jsonResponse.get("jsonrpc").getAsString());
            assertEquals(42, jsonResponse.get("id").getAsInt());
            assertFalse(jsonResponse.has("result"));

            // Verify error
            assertTrue(jsonResponse.has("error"));
            JsonObject error = jsonResponse.get("error").getAsJsonObject();
            assertEquals(-32601, error.get("code").getAsInt());
            assertTrue(error.has("message"));

        } finally {
            System.setOut(originalOut);
        }
    }
}
