package org.example;

import lombok.RequiredArgsConstructor;
import org.example.services.Terminal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy
@SpringBootApplication
@RequiredArgsConstructor
public class App implements CommandLineRunner {
    private final Terminal terminal;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) {
        terminal.run();
    }
}
