package com.stockflow.service;

import com.stockflow.dto.HealthStatus;
import com.stockflow.mapper.HealthMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final HealthMapper healthMapper;

    public HealthService(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    public HealthStatus check() {
        if (healthMapper.checkConnection() != 1) {
            throw new DataAccessResourceFailureException("Database check failed");
        }
        return new HealthStatus("UP");
    }
}
