package com.ohgiraffers.backendapi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);        // 평상시 유지할 스레드
        executor.setMaxPoolSize(50);        // 부하 시 최대 확장
        executor.setQueueCapacity(500);     // 대기 작업 주머니
        executor.setThreadNamePrefix("ReadingWorker-");

        // 중요: 서버 종료 시 큐에 남은 작업을 마저 처리하고 꺼지도록 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
