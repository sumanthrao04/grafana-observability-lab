package com.observability.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "observability-demo-app"
        );
    }

    @GetMapping("/slow")
    public Map<String, String> slow() throws InterruptedException {

        Thread.sleep(3000);

        return Map.of(
                "status", "success",
                "message", "Response intentionally delayed by 3 seconds"
        );
    }

    @GetMapping("/error")
    public Map<String, String> error() {

        throw new RuntimeException(
                "Intentional error generated for observability testing"
        );
    }
}