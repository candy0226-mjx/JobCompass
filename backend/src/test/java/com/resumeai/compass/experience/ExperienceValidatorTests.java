package com.resumeai.compass.experience;

import static org.assertj.core.api.Assertions.*;
import com.resumeai.compass.CompassException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExperienceValidatorTests {
    private final ExperienceValidator validator = new ExperienceValidator();
    static Experience.QuestionOutput questions() {
        return new Experience.QuestionOutput(List.of(
                new Experience.QuestionDraft("VALIDATION", "你如何验证重复预约处理？未验证可如实说明。"),
                new Experience.QuestionDraft("SCENARIO", "预约中是否遇到过重复请求？实际场景是什么？"),
                new Experience.QuestionDraft("IMPLEMENTATION", "你本人具体如何处理，承担了哪些工作？")));
    }
    @Test void questionsMustCoverThreeDistinctAnglesAndGetServerAssignedIds() {
        var result = validator.questions(questions());
        assertThat(result).extracting(Experience.Question::id).containsExactly("q1","q2","q3");
        assertThat(result).extracting(Experience.Question::focus).containsExactly("SCENARIO","IMPLEMENTATION","VALIDATION");
        assertThatThrownBy(() -> validator.questions(new Experience.QuestionOutput(List.of(
                new Experience.QuestionDraft("SCENARIO","问题"), new Experience.QuestionDraft("SCENARIO","问题2"),
                new Experience.QuestionDraft("VALIDATION","问题3"))))).isInstanceOf(CompassException.class);
    }
    @Test void answersRejectForeignIdsDuplicatesAndOversizedText() {
        var q = validator.questions(questions());
        assertThatThrownBy(() -> validator.answers(q, List.of(new Experience.Answer("q1","a"), new Experience.Answer("q1","b"),new Experience.Answer("q3","c")))).isInstanceOf(CompassException.class);
        assertThatThrownBy(() -> validator.answers(q, List.of(new Experience.Answer("q1","a"), new Experience.Answer("q2","b"),new Experience.Answer("foreign","c")))).isInstanceOf(CompassException.class);
        assertThatThrownBy(() -> validator.answers(q, List.of(new Experience.Answer("q1","x".repeat(2001)), new Experience.Answer("q2",""),new Experience.Answer("q3","")))).isInstanceOf(CompassException.class);
    }
    @Test void cardPreservesNegationAndUncertaintyVerbatimWithoutInventedMetrics() {
        var q = validator.questions(questions());
        var answers = validator.answers(q, List.of(new Experience.Answer("q3","没有压测，不能声称提升了性能。"),
                new Experience.Answer("q1","我没有做过 Redis 分布式锁。"), new Experience.Answer("q2","目前只加了数据库唯一约束，不记得异常处理的具体写法。")));
        var draft = validator.draft("接口幂等",q,answers);
        assertThat(draft.sourceType()).isEqualTo("USER_SUPPLIED");
        assertThat(draft.facts()).extracting(Experience.Fact::quote).containsExactlyElementsOf(answers.stream().map(Experience.Answer::text).toList());
        assertThat(draft.facts()).allMatch(f -> f.sourceType().equals("USER_ANSWER"));
    }
    @Test void blankAnswersStayMissingAndEntirelyBlankAnswersCannotProduceCard() {
        var q = validator.questions(questions());
        var answers = validator.answers(q, List.of(new Experience.Answer("q1","做过预约接口"),new Experience.Answer("q2"," "),new Experience.Answer("q3","")));
        assertThat(validator.draft("要求",q,answers).facts()).hasSize(1);
        assertThatThrownBy(() -> validator.draft("要求",q,validator.answers(q,List.of(new Experience.Answer("q1",""),new Experience.Answer("q2",""),new Experience.Answer("q3",""))))).isInstanceOf(CompassException.class);
    }
}
