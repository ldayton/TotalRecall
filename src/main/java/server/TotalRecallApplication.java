package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {"server", "audio"},
        proxyBeanMethods = false)
public class TotalRecallApplication {
    public static void main(String[] args) throws Exception {
        var ctx = SpringApplication.run(TotalRecallApplication.class, args);
        var pingPongService = ctx.getBean(PingPongService.class);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String response = pingPongService.process(line);
                System.out.println(response);
                System.out.flush();
            }
        }
    }
}
