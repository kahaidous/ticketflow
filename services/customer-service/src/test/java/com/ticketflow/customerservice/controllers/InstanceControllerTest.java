package com.ticketflow.customerservice.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstanceControllerTest {

    @Test
    void whoamiReportsTheServiceAndItsPort() {
        InstanceController controller = new InstanceController("customer-service", "8083");

        assertThat(controller.whoami())
                .containsEntry("service", "customer-service")
                .containsEntry("port", "8083");
    }
}