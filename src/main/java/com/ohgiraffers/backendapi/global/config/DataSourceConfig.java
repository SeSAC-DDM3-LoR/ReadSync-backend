package com.ohgiraffers.backendapi.global.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Master-Slave DB 설정
 * 
 * - dev 프로파일에서만 활성화 (운영 환경에서는 RDS Read Replica 사용 권장)
 * - Master: 쓰기 작업 (INSERT, UPDATE, DELETE)
 * - Slave: 읽기 작업 (SELECT)
 */
@Configuration
@Profile({ "dev", "test" }) // 개발 환경 및 테스트 환경에서 활성화
public class DataSourceConfig {

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String masterUrl;

    @Value("${spring.datasource.slave-url:}")
    private String slaveUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    /**
     * Master DataSource (쓰기 전용)
     */
    @Bean
    public DataSource masterDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);
        config.setJdbcUrl(masterUrl); // localhost:5432
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setPoolName("MasterDB-Pool");

        return new HikariDataSource(config);
    }

    /**
     * Slave DataSource (읽기 전용)
     */
    @Bean
    public DataSource slaveDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);

        // Slave URL 설정 (설정값이 없으면 기존 로직대로 5433 포트 사용)
        String url = (slaveUrl != null && !slaveUrl.isEmpty())
                ? slaveUrl
                : masterUrl.replace(":5432", ":5433");

        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setPoolName("SlaveDB-Pool");
        config.setReadOnly(true); // 읽기 전용 설정

        return new HikariDataSource(config);
    }

    /**
     * 라우팅 DataSource
     * Master와 Slave를 동적으로 선택
     */
    @Bean
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource") DataSource slaveDataSource) {

        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("master", masterDataSource);
        dataSourceMap.put("slave", slaveDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource); // 기본은 Master

        return routingDataSource;
    }

    /**
     * 실제로 사용될 DataSource
     * LazyConnectionDataSourceProxy로 감싸서 트랜잭션 시작 시점에 라우팅 결정
     */
    @Primary
    @Bean
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        // LazyConnectionDataSourceProxy는 실제 쿼리 실행 시점에 Connection을 가져옴
        // 이렇게 해야 @Transactional의 readOnly 속성을 제대로 인식할 수 있음
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
