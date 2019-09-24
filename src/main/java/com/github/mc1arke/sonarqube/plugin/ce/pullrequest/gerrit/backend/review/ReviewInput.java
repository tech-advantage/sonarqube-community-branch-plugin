package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.review;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.utils.ReviewUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerrit request for review input. Used with JSON marshaller only.
 * <p>
 * Example JSON:
 * <p>
 * { "message": "Some nits need to be fixed.", "labels": { "Code-Review": -1 },
 * "comments": {
 * "backend-server/src/main/java/com/google/backend/server/project/RefControl.java"
 * : [ { "line": 23, "message": "[nit] trailing whitespace" }, { "line": 49,
 * "message": "[nit] s/conrtol/control" } ] } }
 */
public class ReviewInput {
    private static final Logger LOG = Loggers.get(ReviewInput.class);
    private String message = "Looks good to me.";
    private Map<String, Integer> labels = new ConcurrentHashMap<>();
    private Map<String, List<ReviewFileComment>> comments = new ConcurrentHashMap<>();

    public void setValueAndLabel(int value, String label) {
        labels.put(label, value);
    }

    public void setLabelToPlusOne(String label) {
        this.setValueAndLabel(1, label);
    }

    public void setLabelToMinusOne(String label) {
        this.setValueAndLabel(-1, label);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void addComments(String key, List<ReviewFileComment> reviewFileComments) {
        comments.put(key, new ArrayList<ReviewFileComment>(reviewFileComments));
    }

    public int size() {
        return comments.size();
    }

    public void emptyComments() {
        comments.clear();
    }

    public Map<String, Integer> getLabels() {
        return labels;
    }

    public Map<String, List<ReviewFileComment>> getComments() {
        return comments;
    }

    public boolean isEmpty() {
        return comments.isEmpty();
    }

    public int maxLevelSeverity() {
        int lvl = ReviewUtils.UNKNOWN_VALUE;

        for (Iterator<List<ReviewFileComment>> i = comments.values().iterator(); i.hasNext(); ) {
            List<ReviewFileComment> lrfc = i.next();
            for (ReviewFileComment review : lrfc) {
                lvl = Math.max(review.getSeverity(), lvl);
            }
        }
        LOG.debug("[GERRIT PLUGIN] The max level severity is {}", lvl);

        return lvl;
    }

    @Override
    public String toString() {
        return "ReviewInput [message=" + message + ", labels=" + labels + ", comments=" + comments + "]";
    }
}
