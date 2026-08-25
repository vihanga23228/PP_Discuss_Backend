package com.local.pp_backen.controller;

import com.local.pp_backen.dto.paper.PaperRequest;
import com.local.pp_backen.dto.paper.PaperResponse;
import com.local.pp_backen.service.PaperService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exams/{examId}/papers")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @GetMapping
    public ResponseEntity<List<PaperResponse>> getPapersByExam(@PathVariable Long examId) {
        return ResponseEntity.ok(paperService.getPapersByExam(examId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaperResponse> getPaperById(@PathVariable Long examId, @PathVariable Long id) {
        return ResponseEntity.ok(paperService.getPaperById(examId, id));
    }

    @PostMapping
    public ResponseEntity<PaperResponse> createPaper(@PathVariable Long examId,
                                                     @Valid @RequestBody PaperRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paperService.createPaper(examId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaperResponse> updatePaper(@PathVariable Long examId,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody PaperRequest request) {
        return ResponseEntity.ok(paperService.updatePaper(examId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaper(@PathVariable Long examId, @PathVariable Long id) {
        paperService.deletePaper(examId, id);
        return ResponseEntity.noContent().build();
    }
}
