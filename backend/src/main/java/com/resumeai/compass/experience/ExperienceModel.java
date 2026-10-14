package com.resumeai.compass.experience;

public interface ExperienceModel {
    Experience.QuestionOutput questions(Experience.Context context, String correction);
}
