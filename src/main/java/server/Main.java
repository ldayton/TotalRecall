package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;

            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.add("id", request.get("id"));

            String method = request.get("method").getAsString();

            switch (method) {
                case "ping":
                    response.addProperty("result", "pong");
                    break;
                default:
                    JsonObject error = new JsonObject();
                    error.addProperty("code", -32601);
                    error.addProperty("message", "Method not found");
                    response.add("error", error);
            }

            System.out.println(gson.toJson(response));
            System.out.flush();
        }
    }
}
