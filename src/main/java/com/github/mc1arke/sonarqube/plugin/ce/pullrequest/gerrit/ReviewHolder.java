package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.review.ReviewInput;

public final class ReviewHolder {
    private static ReviewInput reviewInput = new ReviewInput();

    private ReviewHolder() {
    }

    public static ReviewInput getReviewInput() {
        return reviewInput;
    }
}
