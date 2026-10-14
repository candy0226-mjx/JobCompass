package com.resumeai.compass.experience;

import com.resumeai.compass.CompassException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ExperienceValidator {
    public static final List<String> FOCUSES = List.of("SCENARIO", "IMPLEMENTATION", "VALIDATION");

    public List<Experience.Question> questions(Experience.QuestionOutput output) {
        if (output == null || output.questions() == null || output.questions().size() != 3)
            throw invalid("INVALID_QUESTIONS", "须分别询问业务场景、实际实现和验证结果，共三个问题");
        var byFocus = new HashMap<String, String>();
        for (var q : output.questions()) {
            if (q == null || !FOCUSES.contains(q.focus()) || q.question() == null || q.question().isBlank()
                    || q.question().length() > 400 || byFocus.put(q.focus(), q.question().strip()) != null)
                throw invalid("INVALID_QUESTIONS", "追问类别、内容或数量无效");
        }
        var result = new ArrayList<Experience.Question>();
        for (int i = 0; i < FOCUSES.size(); i++) {
            String focus = FOCUSES.get(i);
            result.add(new Experience.Question("q" + (i + 1), focus, byFocus.get(focus)));
        }
        if (new HashSet<>(byFocus.values()).size() != 3)
            throw invalid("INVALID_QUESTIONS", "三个问题不能重复");
        return List.copyOf(result);
    }

    public List<Experience.Answer> answers(List<Experience.Question> questions, List<Experience.Answer> input) {
        if (questions.size() != 3 || input == null || input.size() != 3)
            throw CompassException.bad("请提交三个问题的回答；暂不清楚的项目可留空");
        var validIds = new HashSet<String>(); questions.forEach(q -> validIds.add(q.id()));
        var byId = new HashMap<String, String>();
        for (var answer : input) {
            if (answer == null || !validIds.contains(answer.questionId()) || answer.text() == null
                    || answer.text().length() > 2000 || byId.put(answer.questionId(), answer.text().strip()) != null)
                throw CompassException.bad("回答 ID 重复或不属于本次追问，单项回答不能超过 2000 字符");
        }
        return questions.stream().map(q -> new Experience.Answer(q.id(), byId.get(q.id()))).toList();
    }

    public Experience.Draft draft(String title, List<Experience.Question> questions,
                                  List<Experience.Answer> answers) {
        var nonempty = new LinkedHashMap<String, String>();
        answers.stream().filter(a -> !a.text().isBlank()).forEach(a -> nonempty.put(a.questionId(), a.text()));
        if (nonempty.isEmpty()) throw CompassException.bad("请至少填写一项真实回答，再生成证据卡");
        // Preserve full answers, including negation and uncertainty; do not ask a model to rewrite facts.
        var facts = questions.stream().filter(q -> nonempty.containsKey(q.id()))
                .map(q -> new Experience.Fact(q.id(), q.focus(), nonempty.get(q.id()), "USER_ANSWER")).toList();
        return new Experience.Draft(title, "USER_SUPPLIED", facts);
    }

    private CompassException invalid(String code, String message) { return new CompassException(422, code, message); }
}
