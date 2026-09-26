package com.stockflow.controller;

import com.stockflow.common.ApiResponse;
import com.stockflow.dto.HealthStatus;
import com.stockflow.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success(healthService.check());
    }
}
