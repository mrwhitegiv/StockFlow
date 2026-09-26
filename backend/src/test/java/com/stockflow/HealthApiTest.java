package com.stockflow;

import com.stockflow.mapper.HealthMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HealthApiTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HealthMapper healthMapper;

    @Test
    void healthIsPublicAndUsesUnifiedResponse() throws Exception {
        when(healthMapper.checkConnection()).thenReturn(1);
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void databaseFailureReturns503WithoutLeakingDetails() throws Exception {
        when(healthMapper.checkConnection())
                .thenThrow(new DataAccessResourceFailureException("private connection details"));
        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("Database unavailable"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedFailureReturnsGeneric500() throws Exception {
        when(healthMapper.checkConnection()).thenThrow(new IllegalStateException("private details"));
        mvc.perform(get("/api/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void allowsConfiguredFrontendOriginWithoutCredentials() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void rejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/health")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void otherEndpointsAreNotPublic() throws Exception {
        mvc.perform(get("/api/private")).andExpect(status().isForbidden());
    }
}
