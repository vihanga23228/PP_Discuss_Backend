package com.local.pp_backen.service;

import com.local.pp_backen.dto.question.*;
import com.local.pp_backen.entity.Option;
import com.local.pp_backen.entity.Paper;
import com.local.pp_backen.entity.Question;
import com.local.pp_backen.entity.Subject;
import com.local.pp_backen.exception.ResourceNotFoundException;
import com.local.pp_backen.repository.PaperRepository;
import com.local.pp_backen.repository.QuestionRepository;
import com.local.pp_backen.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final PaperRepository paperRepository;
    private final SubjectRepository subjectRepository;

    public QuestionService(QuestionRepository questionRepository,
                           PaperRepository paperRepository,
                           SubjectRepository subjectRepository) {
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
        this.subjectRepository = subjectRepository;
    }

    public List<QuestionResponse> getAllQuestions(Long paperId, Long subjectId) {
        List<Question> questions;
        if (paperId != null && subjectId != null) {
            questions = questionRepository.findByPaperIdAndSubjectId(paperId, subjectId);
        } else if (paperId != null) {
            questions = questionRepository.findByPaperId(paperId);
        } else if (subjectId != null) {
            questions = questionRepository.findBySubjectId(subjectId);
        } else {
            questions = questionRepository.findAll();
        }
        return questions.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public QuestionResponse getQuestionById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));
        return toResponse(question);
    }

    public QuestionResponse createQuestion(QuestionRequest request) {
        Question question = new Question();
        question.setType(request.getType());
        question.setStem(request.getStem());
        question.setStemSi(request.getStemSi());
        question.setNote(request.getNote());
        question.setNoteSi(request.getNoteSi());
        question.setImageUrl(request.getImageUrl());
        question.setExplanation(request.getExplanation());
        question.setExplanationSi(request.getExplanationSi());

        if (request.getPaperId() != null) {
            Paper paper = paperRepository.findById(request.getPaperId())
                    .orElseThrow(() -> new ResourceNotFoundException("Paper", "id", request.getPaperId()));
            question.setPaper(paper);
        }

        question.setOptions(new ArrayList<>());
        for (OptionRequest optReq : request.getOptions()) {
            Option option = Option.builder()
                    .label(optReq.getLabel())
                    .text(optReq.getText())
                    .textSi(optReq.getTextSi())
                    .imageUrl(optReq.getImageUrl())
                    .correct(optReq.getCorrect())
                    .explanationEn(optReq.getExplanationEn())
                    .explanationSi(optReq.getExplanationSi())
                    .question(question)
                    .build();
            question.getOptions().add(option);
        }

        question = questionRepository.save(question);
        return toResponse(question);
    }

    public QuestionResponse updateQuestion(Long id, QuestionRequest request) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));

        question.setType(request.getType());
        question.setStem(request.getStem());
        question.setStemSi(request.getStemSi());
        question.setNote(request.getNote());
        question.setNoteSi(request.getNoteSi());
        question.setImageUrl(request.getImageUrl());
        question.setExplanation(request.getExplanation());
        question.setExplanationSi(request.getExplanationSi());

        if (request.getPaperId() != null) {
            Paper paper = paperRepository.findById(request.getPaperId())
                    .orElseThrow(() -> new ResourceNotFoundException("Paper", "id", request.getPaperId()));
            question.setPaper(paper);
        }

        // Replace options
        question.getOptions().clear();
        for (OptionRequest optReq : request.getOptions()) {
            Option option = Option.builder()
                    .label(optReq.getLabel())
                    .text(optReq.getText())
                    .textSi(optReq.getTextSi())
                    .imageUrl(optReq.getImageUrl())
                    .correct(optReq.getCorrect())
                    .explanationEn(optReq.getExplanationEn())
                    .explanationSi(optReq.getExplanationSi())
                    .question(question)
                    .build();
            question.getOptions().add(option);
        }

        question = questionRepository.save(question);
        return toResponse(question);
    }

    public void deleteQuestion(Long id) {
        if (!questionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Question", "id", id);
        }
        questionRepository.deleteById(id);
    }

    /** A question inherits its subject from its paper's exam. */
    private static Subject subjectOf(Question question) {
        return question.getPaper() != null && question.getPaper().getExam() != null
                ? question.getPaper().getExam().getSubject()
                : null;
    }

    public QuestionResponse toResponse(Question question) {
        List<OptionResponse> optionResponses = question.getOptions().stream()
                .map(opt -> OptionResponse.builder()
                        .id(opt.getId())
                        .label(opt.getLabel())
                        .text(opt.getText())
                        .textSi(opt.getTextSi())
                        .imageUrl(opt.getImageUrl())
                        .correct(opt.getCorrect())
                        .explanationEn(opt.getExplanationEn())
                        .explanationSi(opt.getExplanationSi())
                        .build())
                .collect(Collectors.toList());

        return QuestionResponse.builder()
                .id(question.getId())
                .type(question.getType())
                .stem(question.getStem())
                .stemSi(question.getStemSi())
                .note(question.getNote())
                .noteSi(question.getNoteSi())
                .imageUrl(question.getImageUrl())
                .explanation(question.getExplanation())
                .explanationSi(question.getExplanationSi())
                .paperId(question.getPaper() != null ? question.getPaper().getId() : null)
                .paperTitle(question.getPaper() != null ? question.getPaper().getTitle() : null)
                .subjectId(subjectOf(question) != null ? subjectOf(question).getId() : null)
                .subjectName(subjectOf(question) != null ? subjectOf(question).getName() : null)
                .options(optionResponses)
                .build();
    }
}
