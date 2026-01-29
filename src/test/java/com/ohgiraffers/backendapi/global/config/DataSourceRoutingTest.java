package com.ohgiraffers.backendapi.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Master-Slave DB 라우팅 테스트
 * 
 * 실행 전 필수 조건:
 * - docker-compose -f docker-compose.dev.yml up 실행 필요
 * - Master DB (5432), Slave DB (5433) 모두 실행 중이어야 함
 */
@SpringBootTest
@ActiveProfiles("dev")
class DataSourceRoutingTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Master DB로 라우팅되는지 확인 (쓰기 트랜잭션)")
    @Transactional // readOnly = false (기본값)
    void testMasterRouting() throws SQLException {
        System.out.println("=== [TEST] Master DB Routing Test ===");

        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            System.out.println("Connected to: " + url);

            // Master는 5432 포트를 사용
            assertThat(url).contains(":5432");
            System.out.println("✅ Master DB 라우팅 성공!");
        }
    }

    @Test
    @DisplayName("Slave DB로 라우팅되는지 확인 (읽기 전용 트랜잭션)")
    @Transactional(readOnly = true)
    void testSlaveRouting() throws SQLException {
        System.out.println("=== [TEST] Slave DB Routing Test ===");

        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            System.out.println("Connected to: " + url);

            // Slave는 5433 포트를 사용
            assertThat(url).contains(":5433");
            System.out.println("✅ Slave DB 라우팅 성공!");
        }
    }

    @Test
    @DisplayName("트랜잭션 없이 실행 시 기본값(Master)으로 라우팅")
    void testDefaultRouting() throws SQLException {
        System.out.println("=== [TEST] Default Routing Test (No Transaction) ===");

        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            System.out.println("Connected to: " + url);

            // 기본값은 Master (5432)
            assertThat(url).contains(":5432");
            System.out.println("✅ 기본 라우팅(Master) 성공!");
        }
    }
}
