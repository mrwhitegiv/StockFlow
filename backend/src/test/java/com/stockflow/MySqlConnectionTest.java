package com.stockflow;

import com.stockflow.mapper.HealthMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// Opt in to test the real MySQL instance, without creating business tables.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_INTEGRATION_TEST", matches = "true")
class MySqlConnectionTest {
    @Autowired
    private HealthMapper healthMapper;

    @Test
    void mybatisCanQueryRealMySql() {
        assertThat(healthMapper.checkConnection()).isEqualTo(1);
    }
}
