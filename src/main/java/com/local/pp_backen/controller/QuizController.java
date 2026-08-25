package com.local.pp_backen.controller;

import com.local.pp_backen.dto.quiz.*;
import com.local.pp_backen.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/start")
    public ResponseEntity<QuizResponse> startQuiz(Authentication authentication,
                                                  @Valid @RequestBody StartQuizRequest request) {
        String username = authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(quizService.startQuiz(username, request));
    }

    @PostMapping("/{attemptId}/submit")
    public ResponseEntity<QuizResultResponse> submitQuiz(Authentication authentication,
                                                         @PathVariable Long attemptId,
                                                         @Valid @RequestBody SubmitQuizRequest request) {
        String username = authentication.getName();
        return ResponseEntity.ok(quizService.submitQuiz(username, attemptId, request));
    }

    @GetMapping("/attempts")
    public ResponseEntity<List<QuizResultResponse>> getUserAttempts(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(quizService.getUserAttempts(username));
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<QuizResultResponse> getAttemptDetail(Authentication authentication,
                                                               @PathVariable Long attemptId) {
        String username = authentication.getName();
        return ResponseEntity.ok(quizService.getAttemptDetail(username, attemptId));
    }
}
