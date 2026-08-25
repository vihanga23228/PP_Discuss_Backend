package com.local.pp_backen.service;

import com.local.pp_backen.dto.admin.ExtractionJobResponse;
import com.local.pp_backen.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Runs a PDF through rasterisation and vision extraction, then hands back a draft the
 * admin can review and publish.
 *
 * <p>Extraction takes tens of seconds, so it runs on a background thread and the console
 * polls for progress. Nothing reaches the database here — the reviewed draft is published
 * through the ordinary paper importer.
 */
@Service
public class PaperExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PaperExtractionService.class);

    /** Figures are cropped with this much of the page added on every side. */
    private static final double FIGURE_PADDING = 0.015;

    private final PdfRasterizer rasterizer;
    private final VisionExtractor extractor;
    private final MediaStorageService media;
    private final int windowPages;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public PaperExtractionService(PdfRasterizer rasterizer,
                                  VisionExtractor extractor,
                                  MediaStorageService media,
                                  @Value("${app.import.window-pages:2}") int windowPages) {
        this.rasterizer = rasterizer;
        this.extractor = extractor;
        this.media = media;
        this.windowPages = Math.max(1, windowPages);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    private static final class Job {
        volatile ExtractionJobResponse.Status status = ExtractionJobResponse.Status.RUNNING;
        volatile String message = "Reading the PDF…";
        volatile int pagesTotal;
        volatile int pagesDone;
        volatile boolean mock;
        volatile List<String> pageImages = List.of();
        volatile List<Map<String, Object>> questions = List.of();
        volatile List<String> errors = List.of();
        volatile List<String> warnings = List.of();
        volatile List<Integer> needsReview = List.of();
        String fileName;
        String model;
    }

    public boolean isConfigured() {
        return extractor.isConfigured();
    }

    /**
     * @param mock when true, skips the vision call and produces a small sample draft so the
     *             review and publish flow can be exercised without an API key
     */
    public String start(byte[] pdfBytes, String fileName, boolean mock) {
        if (!mock && !extractor.isConfigured()) {
            throw new IllegalArgumentException(
                    "Vision extraction is not configured. Set ANTHROPIC_API_KEY on the API, "
                    + "or tick \"use sample data\" to try the review flow first.");
        }

        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 12);
        Job job = new Job();
        job.fileName = fileName;
        job.mock = mock;
        job.model = mock ? "sample data" : extractor.modelName();
        jobs.put(jobId, job);

        pool.submit(() -> run(jobId, job, pdfBytes, mock));
        return jobId;
    }

    public ExtractionJobResponse status(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Extraction job", "id", jobId);
        }
        return ExtractionJobResponse.builder()
                .jobId(jobId)
                .status(job.status)
                .fileName(job.fileName)
                .message(job.message)
                .pagesTotal(job.pagesTotal)
                .pagesDone(job.pagesDone)
                .model(job.model)
                .mock(job.mock)
                .pageImages(job.pageImages)
                .questions(job.questions)
                .errors(job.errors)
                .warnings(job.warnings)
                .needsReview(job.needsReview)
                .build();
    }

    /**
     * Forgets a draft.
     *
     * @param keepFigures true once the draft has been published — the cropped figures are
     *                    now referenced by live questions and must survive, so only the
     *                    full-page renders are cleared. False discards everything.
     */
    public void discard(String jobId, boolean keepFigures) {
        jobs.remove(jobId);
        if (keepFigures) {
            media.deletePageRenders(jobId);
        } else {
            media.deleteFolder(jobId);
        }
    }

    // --- the actual work ---------------------------------------------------

    private void run(String jobId, Job job, byte[] pdfBytes, boolean mock) {
        try {
            job.message = "Rendering pages…";
            List<PdfRasterizer.RenderedPage> pages = rasterizer.rasterise(pdfBytes, (done, total) -> {
                job.pagesTotal = total;
                job.message = done == 0
                        ? "Rendering " + total + " pages…"
                        : "Rendered page " + done + " of " + total + "…";
            });
            job.pagesTotal = pages.size();
            job.pagesDone = 0;

            // Store the renders so the reviewer can check the draft against the original
            List<String> pageUrls = new ArrayList<>();
            for (PdfRasterizer.RenderedPage page : pages) {
                pageUrls.add(media.write(jobId, "page-" + page.number() + ".png", page.png()));
            }
            job.pageImages = pageUrls;

            Map<Integer, VisionExtractor.ExtractedQuestion> byNumber = new TreeMap<>();
            List<String> notes = new ArrayList<>();

            if (mock) {
                job.message = "Building a sample draft…";
                sample(pages).forEach(q -> byNumber.put(q.number(), q));
                job.pagesDone = pages.size();
            } else {
                // Overlapping windows: pages 1+2, 2+3, 3+4 … so a question that straddles a
                // page break is seen whole by at least one call. Duplicates are merged below.
                List<String> failedWindows = new ArrayList<>();

                for (int start = 0; start < pages.size(); start += Math.max(1, windowPages - 1)) {
                    int end = Math.min(pages.size(), start + windowPages);
                    List<PdfRasterizer.RenderedPage> window = pages.subList(start, end);

                    String label = window.size() > 1
                            ? window.get(0).number() + "–" + window.get(window.size() - 1).number()
                            : String.valueOf(window.get(0).number());
                    job.message = "Reading page " + label + " of " + pages.size() + "…";

                    try {
                        VisionExtractor.PageResult result = extractor.extract(window);
                        result.questions().forEach(q -> merge(byNumber, q));
                        if (!result.pageNotes().isBlank()) notes.add(result.pageNotes());

                    } catch (RuntimeException e) {
                        // One bad window must not throw away every page already read. With
                        // overlapping windows a neighbour has often covered the same pages,
                        // and anything genuinely missing shows up as a numbering gap.
                        log.warn("Window {} failed: {}", label, e.getMessage());
                        failedWindows.add(label);
                        notes.add("Pages " + label + " could not be read: " + e.getMessage());
                    }

                    job.pagesDone = end;
                    if (end >= pages.size()) break;
                }

                if (!failedWindows.isEmpty() && byNumber.isEmpty()) {
                    throw new IllegalStateException("No pages could be read. " + notes.get(notes.size() - 1));
                }
            }

            job.message = "Cropping figures…";
            Map<Integer, PdfRasterizer.RenderedPage> pageByNumber = pages.stream()
                    .collect(Collectors.toMap(PdfRasterizer.RenderedPage::number, p -> p));

            List<Map<String, Object>> drafts = new ArrayList<>();
            for (VisionExtractor.ExtractedQuestion q : byNumber.values()) {
                drafts.add(toDraft(jobId, q, pageByNumber));
            }

            Validation validation = validate(drafts, byNumber.values(), notes);
            job.questions = drafts;
            job.errors = validation.errors();
            job.warnings = validation.warnings();
            job.needsReview = validation.needsReview();
            job.status = ExtractionJobResponse.Status.DONE;
            job.message = drafts.size() + " questions extracted";

            log.info("Extraction {} finished: {} questions, {} errors",
                    jobId, drafts.size(), validation.errors().size());

        } catch (Exception e) {
            log.warn("Extraction {} failed: {}", jobId, e.getMessage());
            job.status = ExtractionJobResponse.Status.FAILED;
            job.message = e.getMessage() != null ? e.getMessage() : "Extraction failed.";
        }
    }

    /** Keeps the better of two readings of the same question number. */
    private void merge(Map<Integer, VisionExtractor.ExtractedQuestion> byNumber,
                       VisionExtractor.ExtractedQuestion candidate) {
        VisionExtractor.ExtractedQuestion existing = byNumber.get(candidate.number());
        if (existing == null) {
            byNumber.put(candidate.number(), candidate);
            return;
        }
        // A complete reading always beats a truncated one; otherwise prefer more confidence
        boolean better = (existing.truncated() && !candidate.truncated())
                || (existing.truncated() == candidate.truncated()
                    && rank(candidate.confidence()) > rank(existing.confidence()))
                || (existing.options().isEmpty() && !candidate.options().isEmpty());
        if (better) byNumber.put(candidate.number(), candidate);
    }

    private static int rank(String confidence) {
        return switch (confidence == null ? "" : confidence.toLowerCase(Locale.ROOT)) {
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    /**
     * Converts one extracted question into the shape the paper importer already accepts,
     * cropping any figures on the way.
     */
    private Map<String, Object> toDraft(String jobId,
                                        VisionExtractor.ExtractedQuestion q,
                                        Map<Integer, PdfRasterizer.RenderedPage> pages) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("number", q.number());

        // The importer stores English in `stem` and the original language in `stem_si`
        String original = blankToNull(q.questionText());
        String english = blankToNull(q.questionTextEn());
        draft.put("stem", english != null ? english : original);
        draft.put("stem_si", Objects.equals(original, english) ? null : original);
        draft.put("type", "single");

        if (q.hasFigure() && q.figureBox() != null) {
            crop(jobId, "q" + q.number(), q.figureBox(), pages).ifPresent(url -> draft.put("image", url));
        }

        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            VisionExtractor.ExtractedOption o = q.options().get(i);
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("L", blankToNull(o.label()) != null ? o.label() : String.valueOf(i + 1));

            String optOriginal = blankToNull(o.text());
            String optEnglish = blankToNull(o.textEn());
            option.put("text", optEnglish != null ? optEnglish : Objects.toString(optOriginal, ""));
            option.put("text_si", Objects.equals(optOriginal, optEnglish) ? null : optOriginal);
            option.put("correct", false);

            if (q.optionsAreFigures() && i < q.optionFigureBoxes().size()) {
                crop(jobId, "q" + q.number() + "-opt" + (i + 1), q.optionFigureBoxes().get(i), pages)
                        .ifPresent(url -> option.put("image", url));
            }
            options.add(option);
        }
        draft.put("options", options);

        // Review metadata — the console shows it, the importer ignores it
        draft.put("confidence", q.confidence());
        draft.put("optionsAreFigures", q.optionsAreFigures());
        draft.put("truncated", q.truncated());
        draft.put("sourcePage", q.figureBox() != null ? q.figureBox().page() : null);
        return draft;
    }

    private Optional<String> crop(String jobId, String name, VisionExtractor.FigureBox box,
                                  Map<Integer, PdfRasterizer.RenderedPage> pages) {
        PdfRasterizer.RenderedPage page = pages.get(box.page());
        if (page == null) return Optional.empty();
        try {
            byte[] png = rasterizer.crop(page, box.x0(), box.y0(), box.x1(), box.y1(), FIGURE_PADDING);
            return Optional.of(media.write(jobId, name + ".png", png));
        } catch (Exception e) {
            log.warn("Could not crop {} on page {}: {}", name, box.page(), e.getMessage());
            return Optional.empty();
        }
    }

    // --- validation --------------------------------------------------------

    private record Validation(List<String> errors, List<String> warnings, List<Integer> needsReview) {
    }

    /**
     * Catches the failure modes that matter before anything reaches students: gaps in the
     * numbering, the wrong number of options, unreadable text, and figures that were
     * promised but never cropped.
     */
    private Validation validate(List<Map<String, Object>> drafts,
                                Collection<VisionExtractor.ExtractedQuestion> extracted,
                                List<String> pageNotes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>(pageNotes);
        List<Integer> needsReview = new ArrayList<>();

        if (drafts.isEmpty()) {
            errors.add("No questions could be read from that PDF.");
            return new Validation(errors, warnings, needsReview);
        }

        List<Integer> numbers = drafts.stream().map(d -> (Integer) d.get("number")).sorted().toList();
        int highest = numbers.get(numbers.size() - 1);

        List<Integer> missing = new ArrayList<>();
        for (int n = 1; n <= highest; n++) {
            if (!numbers.contains(n)) missing.add(n);
        }
        if (!missing.isEmpty()) {
            errors.add("Missing question numbers: " + missing);
        }

        Map<Integer, VisionExtractor.ExtractedQuestion> byNumber = extracted.stream()
                .collect(Collectors.toMap(VisionExtractor.ExtractedQuestion::number, q -> q, (a, b) -> a));

        for (Map<String, Object> draft : drafts) {
            int number = (Integer) draft.get("number");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> options = (List<Map<String, Object>>) draft.get("options");

            if (options == null || options.size() != 5) {
                warnings.add("Q" + number + ": expected 5 options, got "
                        + (options == null ? 0 : options.size()) + ".");
            }
            if (String.valueOf(draft.get("stem")).contains("[unreadable]")
                    || String.valueOf(options).contains("[unreadable]")) {
                errors.add("Q" + number + ": contains text the model could not read.");
                needsReview.add(number);
            }
            if (!"high".equalsIgnoreCase(String.valueOf(draft.get("confidence")))) {
                needsReview.add(number);
            }
            if (Boolean.TRUE.equals(draft.get("truncated"))) {
                warnings.add("Q" + number + ": ran across a page break — check it is complete.");
                needsReview.add(number);
            }

            VisionExtractor.ExtractedQuestion source = byNumber.get(number);
            if (source != null && source.hasFigure() && draft.get("image") == null) {
                warnings.add("Q" + number + ": a diagram was expected but none could be cropped.");
                needsReview.add(number);
            }
            if (source != null && source.optionsAreFigures()) {
                long cropped = options == null ? 0
                        : options.stream().filter(o -> o.get("image") != null).count();
                if (cropped < 5) {
                    warnings.add("Q" + number + ": answers are diagrams but only "
                            + cropped + " of 5 were cropped.");
                    needsReview.add(number);
                }
            }
        }

        return new Validation(errors, warnings,
                needsReview.stream().distinct().sorted().collect(Collectors.toList()));
    }

    // --- offline sample ----------------------------------------------------

    /** A tiny canned result so the review and publish flow works without an API key. */
    private List<VisionExtractor.ExtractedQuestion> sample(List<PdfRasterizer.RenderedPage> pages) {
        int page = pages.get(0).number();
        return List.of(
                new VisionExtractor.ExtractedQuestion(
                        1,
                        "ඒකකයක් ඇති නමුත් මානයක් නොමැති පහත සඳහන් භෞතික රාශිය කුමක් ද?",
                        "Which of the following physical quantities has a unit but no dimensions?",
                        List.of(
                                new VisionExtractor.ExtractedOption("1", "ප්ලාන්ක් නියතය", "Planck constant"),
                                new VisionExtractor.ExtractedOption("2", "පෘෂ්ඨික ආතතිය", "Surface tension"),
                                new VisionExtractor.ExtractedOption("3", "ශක්තිය", "Energy"),
                                new VisionExtractor.ExtractedOption("4", "සාපේක්ෂ ප්‍රවේගය", "Relative velocity"),
                                new VisionExtractor.ExtractedOption("5", "ධ්වනි තීව්‍රතා මට්ටම", "Sound intensity level")),
                        false, null, false, List.of(), false, "high"),
                new VisionExtractor.ExtractedQuestion(
                        2,
                        "කාලය සමග වස්තුවක ප්‍රවේගයේ විචලනය රූපයේ දැක්වේ.",
                        "The graph shows how the velocity of a body varies with time.",
                        List.of(
                                new VisionExtractor.ExtractedOption("1", "", ""),
                                new VisionExtractor.ExtractedOption("2", "", ""),
                                new VisionExtractor.ExtractedOption("3", "", ""),
                                new VisionExtractor.ExtractedOption("4", "", ""),
                                new VisionExtractor.ExtractedOption("5", "", "")),
                        true,
                        new VisionExtractor.FigureBox(page, 0.55, 0.10, 0.95, 0.32),
                        true,
                        List.of(
                                new VisionExtractor.FigureBox(page, 0.10, 0.40, 0.30, 0.55),
                                new VisionExtractor.FigureBox(page, 0.32, 0.40, 0.52, 0.55),
                                new VisionExtractor.FigureBox(page, 0.54, 0.40, 0.74, 0.55),
                                new VisionExtractor.FigureBox(page, 0.10, 0.58, 0.30, 0.73),
                                new VisionExtractor.FigureBox(page, 0.32, 0.58, 0.52, 0.73)),
                        false, "medium"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
