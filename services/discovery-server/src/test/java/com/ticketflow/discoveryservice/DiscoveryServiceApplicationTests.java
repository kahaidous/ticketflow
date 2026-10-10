package com.ticketflow.discoveryservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServiceApplicationTests {
	@Value("${local.server.port}")
	int port;

	private final HttpClient client = HttpClient.newHttpClient();

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.header("Accept", "application/json")
				.GET()
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void healthEndpointIsUp() throws Exception {
		HttpResponse<String> response = get("/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("UP");
	}

	@Test
	void registryEndpointIsReachable() throws Exception {
		HttpResponse<String> response = get("/eureka/apps");

		assertThat(response.statusCode()).isEqualTo(200);
	}

}
