package com.ohgiraffers.backendapi.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Master-Slave DB 라우팅을 담당하는 DataSource
 * 
 * @Transactional(readOnly = true) → Slave DB로 라우팅
 * @Transactional (또는 readOnly = false) → Master DB로 라우팅
 */
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        // 현재 트랜잭션이 readOnly라면 'slave'를, 아니면 'master'를 반환
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        String dataSourceKey = isReadOnly ? "slave" : "master";

        log.info(">>>> Current Transaction Routing Target: {}", dataSourceKey);

        return dataSourceKey;
    }
}
