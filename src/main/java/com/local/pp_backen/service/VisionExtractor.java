package com.local.pp_backen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Reads rasterised exam pages with a vision model and returns structured questions.
 *
 * <p>The prompt is deliberately strict: transcribe verbatim, never translate, never
 * guess, and report a confidence so that anything shaky can be routed to review rather
 * than published. Temperature is zero so the same PDF yields the same JSON on a re-run.
 */
@Service
public class VisionExtractor {

    private static final Logger log = LoggerFactory.getLogger(VisionExtractor.class);
    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private static final String SCHEMA_PROMPT = """
            You are extracting multiple-choice exam questions from images of exam pages.

            Return ONLY valid JSON — no prose, no markdown fences — matching this schema:

            {
              "questions": [
                {
                  "number": 7,
                  "question_text": "...",
                  "question_text_en": "...",
                  "options": [ { "label": "1", "text": "8 N m", "text_en": "8 N m" } ],
                  "has_figure": true,
                  "figure_box": { "page": 2, "x0": 0.55, "y0": 0.12, "x1": 0.95, "y1": 0.34 },
                  "options_are_figures": false,
                  "option_figure_boxes": [],
                  "truncated": false,
                  "confidence": "high"
                }
              ],
              "page_notes": "anything odd, such as a question cut off at the page edge"
            }

            RULES
            - Transcribe "question_text" and every option EXACTLY as printed, in the
              original language of the paper. Do not translate, summarise or fix typos.
            - Additionally give "question_text_en" and each option's "text_en" as a faithful
              English translation. If the paper is already in English, repeat the same text.
            - Never invent content. If something is unreadable write "[unreadable]".
            - Write mathematics as plain Unicode text, exactly as it should appear on
              screen: Ω, μ, π, θ, ², ³, ⁻¹, ×, ÷, √, ≤, ≥, ±, °C, ½.
              Do NOT use LaTeX, dollar signs, or backslashes anywhere in your output —
              this is displayed as plain text, and a backslash also breaks the JSON.
            - "figure_box" locates a diagram as FRACTIONS of the page (0 to 1), with "page"
              being the page number printed in the image caption I give you. Give the box
              generously — include axis labels and captions. Omit the field when there is
              no figure.
            - If the answer options are themselves diagrams or graphs labelled (1)–(5), set
              "options_are_figures": true, leave each option's text empty, and give one box
              per option in "option_figure_boxes", in order.
            - Set "truncated": true when a question starts on one page and runs onto the
              next, and transcribe only what you can see.
            - "confidence" is "high", "medium" or "low" — use low whenever you are unsure of
              a character, a number, or which figure belongs to the question.
            - Do NOT work out the correct answers. The marking scheme is entered separately.
            """;

    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    /** Which service the configured key belongs to. */
    public enum Provider { ANTHROPIC, GEMINI, NONE }

    private final String apiKey;
    private final Provider provider;
    private final String model;
    private final PdfRasterizer rasterizer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeoutFactory())
            .build();

    /**
     * Vision calls on a busy free tier routinely take over a minute, so the read timeout
     * has to be generous — but not absent, or a stalled socket hangs the whole job.
     */
    private static org.springframework.http.client.ClientHttpRequestFactory timeoutFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(30));
        factory.setReadTimeout(java.time.Duration.ofMinutes(4));
        return factory;
    }

    /** Smallest gap between two calls, so a free-tier quota is never the thing that fails a run. */
    private final long minIntervalMs;

    /** When the last request went out, used to pace the next one. */
    private long lastRequestAt = 0;

    public VisionExtractor(PdfRasterizer rasterizer,
                           @Value("${app.import.api-key:${app.import.anthropic-api-key:}}") String apiKey,
                           @Value("${app.import.provider:auto}") String configuredProvider,
                           @Value("${app.import.model:}") String model,
                           @Value("${app.import.min-request-interval-ms:7000}") long minIntervalMs) {
        this.rasterizer = rasterizer;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.provider = resolveProvider(configuredProvider, this.apiKey);
        this.model = StringUtils.hasText(model) ? model.trim() : defaultModel(this.provider);
        this.minIntervalMs = Math.max(0, minIntervalMs);

        if (this.provider != Provider.NONE) {
            log.info("Vision extraction using {} / {}, at most one request every {} ms (~{} per minute)",
                    this.provider, this.model, this.minIntervalMs,
                    this.minIntervalMs > 0 ? 60_000 / this.minIntervalMs : "unlimited");
        }
    }

    /**
     * Waits until enough time has passed since the previous call.
     *
     * <p>Free tiers cap requests per minute, and being told off after the fact wastes the
     * work already done on a page. Spacing the calls out costs a few seconds and avoids
     * the problem entirely.
     */
    private synchronized void pace() {
        if (minIntervalMs == 0) return;

        long since = System.currentTimeMillis() - lastRequestAt;
        long wait = minIntervalMs - since;
        if (lastRequestAt > 0 && wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Extraction was interrupted.", e);
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    /**
     * Anthropic keys start with {@code sk-ant-}; anything else that looks like a key is
     * treated as Google Gemini, which is what an AI Studio key looks like.
     */
    private static Provider resolveProvider(String configured, String key) {
        if (!StringUtils.hasText(key)) return Provider.NONE;

        String choice = configured == null ? "auto" : configured.trim().toLowerCase();
        return switch (choice) {
            case "anthropic", "claude" -> Provider.ANTHROPIC;
            case "gemini", "google" -> Provider.GEMINI;
            default -> key.startsWith("sk-ant-") ? Provider.ANTHROPIC : Provider.GEMINI;
        };
    }

    private static String defaultModel(Provider provider) {
        return provider == Provider.ANTHROPIC ? "claude-sonnet-5" : "gemini-3.6-flash";
    }

    public boolean isConfigured() {
        return provider != Provider.NONE;
    }

    public Provider provider() {
        return provider;
    }

    public String modelName() {
        return model;
    }

    /** One question as the model reported it, before validation or cropping. */
    public record ExtractedQuestion(
            int number,
            String questionText,
            String questionTextEn,
            List<ExtractedOption> options,
            boolean hasFigure,
            FigureBox figureBox,
            boolean optionsAreFigures,
            List<FigureBox> optionFigureBoxes,
            boolean truncated,
            String confidence) {
    }

    public record ExtractedOption(String label, String text, String textEn) {
    }

    public record FigureBox(int page, double x0, double y0, double x1, double y1) {
    }

    public record PageResult(List<ExtractedQuestion> questions, String pageNotes) {
    }

    /**
     * @param pages the rendered pages to send together — two at a time gives an
     *              overlapping window so a question split across a break is still whole
     */
    public PageResult extract(List<PdfRasterizer.RenderedPage> pages) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Vision extraction is not configured. Put an API key in pp_backen/.env, "
                    + "or use the sample extraction to try the review flow.");
        }

        // JPEG rather than PNG: a 200 DPI page drops from ~1.6 MB to ~250 KB, which keeps
        // the request inside provider limits and cuts latency, with no loss of legibility.
        List<byte[]> images = pages.stream()
                .map(p -> rasterizer.toJpeg(p.png(), 0.85f))
                .toList();

        String raw = provider == Provider.ANTHROPIC
                ? callAnthropic(pages, images)
                : callGemini(pages, images);

        return parse(raw);
    }

    private String callAnthropic(List<PdfRasterizer.RenderedPage> pages, List<byte[]> images) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            content.add(Map.of("type", "text", "text", "PAGE " + pages.get(i).number() + ":"));
            content.add(Map.of(
                    "type", "image",
                    "source", Map.of(
                            "type", "base64",
                            "media_type", "image/jpeg",
                            "data", Base64.getEncoder().encodeToString(images.get(i)))));
        }
        content.add(Map.of("type", "text", "text", SCHEMA_PROMPT));

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 16000,
                "temperature", 0,
                "messages", List.of(Map.of("role", "user", "content", content)));

        JsonNode response = send(ENDPOINT, body, request -> request
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION));

        return response.path("content").path(0).path("text").asText("");
    }

    private String callGemini(List<PdfRasterizer.RenderedPage> pages, List<byte[]> images) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            parts.add(Map.of("text", "PAGE " + pages.get(i).number() + ":"));
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", "image/jpeg",
                    "data", Base64.getEncoder().encodeToString(images.get(i)))));
        }
        parts.add(Map.of("text", SCHEMA_PROMPT));

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                // Asking for JSON directly removes the markdown-fence guesswork
                "generationConfig", Map.of(
                        "temperature", 0,
                        "maxOutputTokens", 32000,
                        "responseMimeType", "application/json"));

        JsonNode response = send(String.format(GEMINI_ENDPOINT, model, apiKey), body, request -> request);

        JsonNode candidate = response.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(finish)) {
            throw new IllegalStateException(
                    "The model ran out of output room on this page range. Lower app.import.window-pages to 1.");
        }
        if ("SAFETY".equals(finish) || "RECITATION".equals(finish)) {
            throw new IllegalStateException("The model declined to transcribe this page (" + finish + ").");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            text.append(part.path("text").asText(""));
        }
        return text.toString();
    }

    /** Shared POST with a couple of retries, because free tiers rate-limit aggressively. */
    private JsonNode send(String uri, Map<String, Object> body,
                          java.util.function.UnaryOperator<RestClient.RequestBodySpec> headers) {
        RuntimeException last = null;
        int attempts = 5;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                pace();
                // Read the body as text and parse it here. Binding straight to JsonNode
                // depends on which Jackson converters the RestClient happens to carry,
                // and quietly fails with "Type definition error" when they are missing.
                String raw = headers
                        .apply(restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON))
                        .body(serialise(body))
                        .retrieve()
                        .body(String.class);

                if (raw == null || raw.isBlank()) {
                    throw new IllegalStateException("The vision model returned an empty response.");
                }

                JsonNode response = mapper.readTree(raw);
                if (response.has("error")) {
                    throw new IllegalStateException(
                            "The vision model rejected the request: "
                            + response.path("error").path("message").asText("unknown error"));
                }
                return response;

            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                last = e;
                if (attempt == attempts) break;

                // Providers usually say how long to hold off; trust that over guessing
                long wait = retryAfterMs(e).orElse(Math.min(60_000L, minIntervalMs * (1L << attempt)));
                log.warn("Rate limited, waiting {} ms before attempt {} of {}", wait, attempt + 1, attempts);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Extraction was interrupted.", interrupted);
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // 4xx other than 429 means the request itself is wrong — retrying will not help
                throw new IllegalStateException(
                        "The vision model rejected the request (" + e.getStatusCode() + "): "
                        + firstLine(e.getResponseBodyAsString()), e);

            } catch (org.springframework.web.client.ResourceAccessException
                     | org.springframework.web.client.HttpServerErrorException e) {
                // A dropped connection, a timeout, or a 5xx: all worth another go
                last = e instanceof RuntimeException re ? re : new IllegalStateException(e);
                if (attempt == attempts) break;

                long wait = Math.min(60_000L, minIntervalMs * (1L << attempt));
                log.warn("Transient failure ({}), waiting {} ms before attempt {} of {}",
                        e.getClass().getSimpleName(), wait, attempt + 1, attempts);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Extraction was interrupted.", interrupted);
                }

            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "The vision model could not be reached: " + e.getMessage(), e);
            }
        }

        throw new IllegalStateException(
                "Gave up after " + attempts + " attempts. Last problem: "
                + (last == null ? "unknown" : last.getMessage()), last);
    }

    /**
     * Reads how long the provider wants us to wait. Anthropic sends a {@code Retry-After}
     * header in seconds; Gemini puts a {@code retryDelay} such as "31s" in the error body.
     */
    private java.util.Optional<Long> retryAfterMs(
            org.springframework.web.client.HttpClientErrorException e) {

        String header = e.getResponseHeaders() == null
                ? null : e.getResponseHeaders().getFirst("Retry-After");
        if (header != null) {
            try {
                return java.util.Optional.of(Long.parseLong(header.trim()) * 1000L);
            } catch (NumberFormatException ignored) {
                // Some servers send a date instead; fall through to the body
            }
        }

        try {
            JsonNode body = mapper.readTree(e.getResponseBodyAsString());
            for (JsonNode detail : body.path("error").path("details")) {
                String delay = detail.path("retryDelay").asText("");
                if (delay.endsWith("s")) {
                    double seconds = Double.parseDouble(delay.substring(0, delay.length() - 1));
                    return java.util.Optional.of((long) (seconds * 1000));
                }
            }
        } catch (Exception ignored) {
            // No usable hint — the caller falls back to its own backoff
        }
        return java.util.Optional.empty();
    }

    private String serialise(Map<String, Object> body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the request for the vision model", e);
        }
    }

    private static String firstLine(String body) {
        if (body == null || body.isBlank()) return "no detail";
        String trimmed = body.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "…" : trimmed;
    }

    /** Tolerates the model wrapping its JSON in a markdown fence. */
    PageResult parse(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            int firstBreak = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstBreak > 0 && lastFence > firstBreak) {
                json = json.substring(firstBreak + 1, lastFence).trim();
            }
        }

        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception first) {
            // Models sometimes slip LaTeX through despite the instruction, and a lone
            // backslash is an illegal JSON escape. Repair it rather than lose the page.
            try {
                root = mapper.readTree(escapeStrayBackslashes(json));
                log.warn("Repaired stray backslashes in the model's JSON.");
            } catch (Exception second) {
                throw new IllegalStateException(
                        "The vision model did not return usable JSON (" + first.getMessage()
                        + "). First 200 characters: "
                        + json.substring(0, Math.min(200, json.length())));
            }
        }

        List<ExtractedQuestion> questions = new ArrayList<>();
        for (JsonNode q : root.path("questions")) {
            List<ExtractedOption> options = new ArrayList<>();
            int index = 0;
            for (JsonNode o : q.path("options")) {
                index++;
                options.add(new ExtractedOption(
                        o.path("label").asText(String.valueOf(index)),
                        o.path("text").asText(""),
                        o.path("text_en").asText("")));
            }

            List<FigureBox> optionBoxes = new ArrayList<>();
            for (JsonNode b : q.path("option_figure_boxes")) {
                box(b).ifPresent(optionBoxes::add);
            }

            int number = q.path("number").asInt(questions.size() + 1);
            questions.add(new ExtractedQuestion(
                    number,
                    stripLeadingNumber(q.path("question_text").asText(""), number),
                    stripLeadingNumber(q.path("question_text_en").asText(""), number),
                    options,
                    q.path("has_figure").asBoolean(false),
                    box(q.path("figure_box")).orElse(null),
                    q.path("options_are_figures").asBoolean(false),
                    optionBoxes,
                    q.path("truncated").asBoolean(false),
                    q.path("confidence").asText("medium")));
        }

        return new PageResult(questions, root.path("page_notes").asText(""));
    }

    /**
     * Doubles any backslash that is not the start of a legal JSON escape, so LaTeX such as
     * {@code $10\ \Omega$} stops breaking the parse. Legal escapes are left alone.
     */
    static String escapeStrayBackslashes(String json) {
        StringBuilder out = new StringBuilder(json.length() + 32);
        String legal = "\"\\/bfnrtu";

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }

            char next = i + 1 < json.length() ? json.charAt(i + 1) : '\0';
            if (legal.indexOf(next) >= 0) {
                // A real escape — copy both characters and skip past them
                out.append(c).append(next);
                i++;
            } else {
                out.append("\\\\");
            }
        }
        return out.toString();
    }

    /** Papers print "7. ..." in the stem; the number is stored on its own field. */
    static String stripLeadingNumber(String text, int number) {
        if (text == null) return "";
        return text.trim().replaceFirst("^" + number + "\s*[.)]\s*", "");
    }

    private java.util.Optional<FigureBox> box(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return java.util.Optional.empty();
        if (!node.has("x0") || !node.has("y1")) return java.util.Optional.empty();
        return java.util.Optional.of(new FigureBox(
                node.path("page").asInt(1),
                node.path("x0").asDouble(0),
                node.path("y0").asDouble(0),
                node.path("x1").asDouble(1),
                node.path("y1").asDouble(1)));
    }
}
