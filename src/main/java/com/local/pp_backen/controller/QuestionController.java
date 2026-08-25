package com.local.pp_backen.controller;

import com.local.pp_backen.dto.question.QuestionRequest;
import com.local.pp_backen.dto.question.QuestionResponse;
import com.local.pp_backen.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionResponse>> getAllQuestions(
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Long subjectId) {
        return ResponseEntity.ok(questionService.getAllQuestions(paperId, subjectId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionResponse> getQuestionById(@PathVariable Long id) {
        return ResponseEntity.ok(questionService.getQuestionById(id));
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> createQuestion(@Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.createQuestion(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionResponse> updateQuestion(@PathVariable Long id,
                                                           @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(questionService.updateQuestion(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }
}
