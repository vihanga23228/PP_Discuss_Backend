package com.local.pp_backen.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** The state of one PDF extraction, polled by the admin console while it runs. */
@Data
@Builder
public class ExtractionJobResponse {

    public enum Status { RUNNING, DONE, FAILED }

    private String jobId;
    private Status status;
    private String fileName;
    private String message;

    private int pagesTotal;
    private int pagesDone;

    /** Which model produced this, or "sample data" when run without an API key */
    private String model;
    private boolean mock;

    /** Rendered page images, shown beside the draft for checking against the original */
    private List<String> pageImages;

    /** Draft questions in the importer's own shape, ready to edit and publish */
    private List<Map<String, Object>> questions;

    /** Set instead of questions when the upload was a marking scheme. */
    private List<Map<String, Object>> answers;

    /** Blocking problems — the draft should not be published until these are cleared */
    private List<String> errors;

    /** Things worth a look, but not blocking */
    private List<String> warnings;

    /** Question numbers the model was unsure about */
    private List<Integer> needsReview;
}
