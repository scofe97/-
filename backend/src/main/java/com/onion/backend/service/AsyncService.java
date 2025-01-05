package com.onion.backend.service;

import com.onion.backend.repository.CustomQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncService {

    private final CustomQueryRepository customQueryRepository;

    public String syncTask(int taskNumber) {
        log.info("Starting async task {}", taskNumber);
        try {
            // 2초 대기 시간 (비동기 작업 시뮬레이션)
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            log.error("Sync task interrupted", e);
        }
        return "Sync Task " + taskNumber + " completed";
    }

    @Async
    public CompletableFuture<String> asyncTask(int taskNumber) {
        log.info("Starting async task {}", taskNumber);
        try {
            // 2초 대기 시간 (비동기 작업 시뮬레이션)
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            log.error("Async task interrupted", e);
        }
        return CompletableFuture.completedFuture("Async Task " + taskNumber + " completed");
    }

    public void dbExecuteLongTime() {
        customQueryRepository.executeSleepQuery();
    }
}