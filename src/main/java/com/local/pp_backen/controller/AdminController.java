package com.local.pp_backen.controller;

import com.local.pp_backen.dto.admin.*;
import com.local.pp_backen.service.AdminService;
import com.local.pp_backen.service.MediaUploadService;
import com.local.pp_backen.service.PaperExtractionService;
import com.local.pp_backen.service.PaperReviewService;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Everything under here is locked to ROLE_ADMIN by SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final PaperExtractionService extractionService;
    private final MediaUploadService mediaUploadService;
    private final PaperReviewService paperReviewService;

    public AdminController(AdminService adminService,
                           PaperExtractionService extractionService,
                           MediaUploadService mediaUploadService,
                           PaperReviewService paperReviewService) {
        this.adminService = adminService;
        this.extractionService = extractionService;
        this.mediaUploadService = mediaUploadService;
        this.paperReviewService = paperReviewService;
    }

    /**
     * Which questions in a published paper still need fixing by hand, and why.
     * Only the flagged ones come back — on a 50-question paper the useful answer
     * is the short list to work through.
     */
    @GetMapping("/papers/{paperId}/review")
    public ResponseEntity<PaperReviewResponse> paperReview(@PathVariable Long paperId) {
        return ResponseEntity.ok(paperReviewService.review(paperId));
    }

    /**
     * Takes a screenshot pasted or picked in the console and returns the URL to
     * store on the question. Kept in the database, because the host's disk is
     * wiped on every redeploy.
     */
    @PostMapping("/media")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.ok(Map.of("url", mediaUploadService.store(file)));
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(adminService.stats());
    }

    @GetMapping("/users")
    public ResponseEntity<PagedResponse<AdminUserResponse>> users(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.users(search, page, size));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(Authentication authentication,
                                                        @PathVariable Long id,
                                                        @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(adminService.updateUser(authentication.getName(), id, request));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(Authentication authentication, @PathVariable Long id) {
        adminService.deleteUser(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Whether the console should offer extraction, and in which mode. Also
     * reports a spent daily quota, so the upload page can say so before someone
     * picks a file and waits through rasterising to be told.
     */
    @GetMapping("/import/capabilities")
    public ResponseEntity<Map<String, Object>> importCapabilities() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("vision", extractionService.isConfigured());
        body.put("quotaExhausted", extractionService.isDailyQuotaExhausted());
        body.put("quotaResetsAt", java.util.Objects.toString(extractionService.quotaResetsAt(), null));
        body.put("requestsToday", extractionService.requestsToday());
        // Null until a rejection tells us the cap; HashMap because Map.of rejects nulls.
        body.put("dailyLimit", extractionService.knownDailyLimit());
        return ResponseEntity.ok(body);
    }

    /** Uploads a PDF and starts extraction. Returns straight away with a job id. */
    @PostMapping("/papers/extract")
    public ResponseEntity<Map<String, String>> extract(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean mock) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Choose a PDF to upload.");
        }
        String name = file.getOriginalFilename() == null ? "paper.pdf" : file.getOriginalFilename();
        requireReadable(name);

        String jobId = extractionService.start(file.getBytes(), name, mock);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    /**
     * Uploads a marking scheme / answer sheet. Poll the same status endpoint; when
     * it is DONE the job carries `answers` instead of `questions`.
     */
    @PostMapping("/papers/extract-answers")
    public ResponseEntity<Map<String, String>> extractAnswers(@RequestParam("file") MultipartFile file)
            throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Choose the marking scheme PDF to upload.");
        }
        String name = file.getOriginalFilename() == null ? "answers.pdf" : file.getOriginalFilename();
        requireReadable(name);

        return ResponseEntity.accepted()
                .body(Map.of("jobId", extractionService.startAnswerKey(file.getBytes(), name)));
    }

    /** Stops a running extraction before it spends any more of the daily quota. */
    @PostMapping("/papers/extract/{jobId}/cancel")
    public ResponseEntity<Void> cancelExtraction(@PathVariable String jobId) {
        extractionService.cancel(jobId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/papers/extract/{jobId}")
    public ResponseEntity<ExtractionJobResponse> extractionStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(extractionService.status(jobId));
    }

    /**
     * @param keepFigures pass true after publishing — the cropped figures are referenced by
     *                    the new questions, so only the page renders are cleared
     */
    @DeleteMapping("/papers/extract/{jobId}")
    public ResponseEntity<Void> discardExtraction(@PathVariable String jobId,
                                                  @RequestParam(defaultValue = "false") boolean keepFigures) {
        extractionService.discard(jobId, keepFigures);
        return ResponseEntity.noContent().build();
    }

    /** A page of a paper is as often photographed or screenshotted as it is a PDF. */
    private static void requireReadable(String name) {
        String lower = name.toLowerCase();
        boolean ok = lower.endsWith(".pdf") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp");
        if (!ok) {
            throw new IllegalArgumentException(
                    "\"" + name + "\" is not a PDF or an image. Upload a PDF, PNG, JPEG or WebP.");
        }
    }

    @PostMapping("/papers/import")
    public ResponseEntity<ImportPaperResponse> importPaper(@Valid @RequestBody ImportPaperRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.importPaper(request));
    }
}
