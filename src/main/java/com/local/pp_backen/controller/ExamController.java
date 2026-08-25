package com.local.pp_backen.controller;

import com.local.pp_backen.dto.exam.ExamRequest;
import com.local.pp_backen.dto.exam.ExamResponse;
import com.local.pp_backen.service.ExamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @GetMapping
    public ResponseEntity<List<ExamResponse>> getAllExams(
            @RequestParam(required = false) Long subjectId) {
        return ResponseEntity.ok(subjectId != null
                ? examService.getExamsBySubject(subjectId)
                : examService.getAllExams());
    }

    /** Exams created before subjects existed, so an admin can file them. */
    @GetMapping("/unassigned")
    public ResponseEntity<List<ExamResponse>> getUnassignedExams() {
        return ResponseEntity.ok(examService.getUnassignedExams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamResponse> getExamById(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getExamById(id));
    }

    @PostMapping
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody ExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examService.createExam(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable Long id, @Valid @RequestBody ExamRequest request) {
        return ResponseEntity.ok(examService.updateExam(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExam(@PathVariable Long id) {
        examService.deleteExam(id);
        return ResponseEntity.noContent().build();
    }
}
