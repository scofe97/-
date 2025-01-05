package com.onion.backend.controller;

import com.onion.backend.entity.Article;
import com.onion.backend.service.ArticleService;
import com.onion.backend.service.AsyncService;
import com.onion.backend.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/async")
public class AsyncController {
    private final AsyncService asyncService;

    @GetMapping("/async")
    public void getAsync() {
        CompletableFuture<String> task1 = asyncService.asyncTask(1);
        CompletableFuture<String> task2 = asyncService.asyncTask(2);
        CompletableFuture<String> task3 = asyncService.asyncTask(3);
        CompletableFuture.allOf(task1, task2, task3).join();
    }

    @GetMapping("/sync")
    public void getSync() {
        asyncService.syncTask(1);
        asyncService.syncTask(2);
        asyncService.syncTask(3);
    }

    @GetMapping("/dbTime")
    public void getDbTime() {
        asyncService.dbExecuteLongTime();
    }
}
