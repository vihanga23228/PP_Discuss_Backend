package com.local.pp_backen.dto.quiz;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AnswerSubmission {

    @NotNull(message = "Question ID is required")
    private Long questionId;

    /**
     * For "single" / "multi" questions: the option ids the user picked.
     * For "tf" questions this is derived from {@link #optionAnswers} and may be left null.
     */
    private List<Long> selectedOptionIds;

    /**
     * For "tf" questions: the user's True/False verdict per option id.
     * Options missing from this map are treated as unanswered (and therefore wrong),
     * which is what lets us tell "said False" apart from "skipped it".
     */
    private Map<Long, Boolean> optionAnswers;

    public List<Long> selectedOptionIdsOrEmpty() {
        return selectedOptionIds != null ? selectedOptionIds : new ArrayList<>();
    }

    public Map<Long, Boolean> optionAnswersOrEmpty() {
        return optionAnswers != null ? optionAnswers : Map.of();
    }
}
