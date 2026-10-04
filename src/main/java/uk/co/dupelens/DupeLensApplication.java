package uk.co.dupelens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class DupeLensApplication {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("data"));
        Files.createDirectories(Path.of("data", "thumbs"));
        Files.createDirectories(Path.of("data", "tmp"));
        SpringApplication.run(DupeLensApplication.class, args);
    }
}
