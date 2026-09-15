package com.lpdg.sentinel.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test verifying that the Phase 7 Operator Decision Console is
 * correctly served at root "/" and "/index.html" as a static resource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sentinel.data-dir=data",
        "sentinel.visits-per-week=15",
        "sentinel.ranking-strategy=risk-based"
})
class DashboardStaticResourceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("GET / returns 200 OK and serves the dashboard HTML")
    void getRoot_returnsDashboardHtml() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody()).contains("SENTINEL");
    }

    @Test
    @DisplayName("GET /index.html returns 200 OK and serves the Sentinel Operator Decision Console HTML")
    void getIndexHtml_returnsDashboardHtml() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SENTINEL")))
                .andExpect(content().string(containsString("Gateway Visit Decision Console")))
                .andExpect(content().string(containsString("predictionsTable")))
                .andExpect(content().string(containsString("evidenceModal")));
    }
}
