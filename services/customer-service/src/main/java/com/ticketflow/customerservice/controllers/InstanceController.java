package com.ticketflow.customerservice.controllers;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tells which instance answered. Used to SEE the load balancing: start two
 * instances on different ports and call this endpoint through the gateway.
 */
@RestController
@RequestMapping("/api/customers")
public class InstanceController {
    private final String serviceName;
    private final String port;

    public InstanceController(@Value("${spring.application.name}") String serviceName,
                              @Value("${server.port}") String port) {
        this.serviceName = serviceName;
        this.port = port;
    }

    @GetMapping("/whoami")
    public Map<String, String> whoami() {
        return Map.of("service", serviceName, "port", port);
    }
}
