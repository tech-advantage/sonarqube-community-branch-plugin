package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit;

public final class PropertyKey {
    public static final String GERRIT_SCHEME = "sonar.pullrequest.backend.scheme";
    public static final String GERRIT_HOST = "sonar.pullrequest.backend.url";
    public static final String GERRIT_PORT = "sonar.pullrequest.backend.port";
    public static final String GERRIT_PROJECT = "sonar.pullrequest.backend.project";
    public static final String GERRIT_BRANCH = "sonar.pullrequest.backend.branch";
    public static final String GERRIT_CHANGE_NUMBER = "sonar.pullrequest.backend.change";
    public static final String GERRIT_REVISION_ID = "sonar.pullrequest.backend.revision";
    public static final String GERRIT_USERNAME = "sonar.pullrequest.backend.username";
    public static final String GERRIT_PASSWORD = "sonar.pullrequest.backend.password"; // NOSONAR
    public static final String GERRIT_SSH_KEY_PATH = "sonar.pullrequest.backend.sshkeypath";
    public static final String GERRIT_HTTP_AUTH_SCHEME = "sonar.pullrequest.backend.httpauthscheme";
    public static final String GERRIT_LABEL = "sonar.pullrequest.backend.label";
    public static final String GERRIT_MESSAGE = "sonar.pullrequest.backend.message";
    public static final String GERRIT_BASE_PATH = "sonar.pullrequest.backend.basepath";
    public static final String GERRIT_THRESHOLD = "sonar.pullrequest.backend.threshold";
    public static final String GERRIT_FORCE_BRANCH = "sonar.pullrequest.backend.forcebranch";
    public static final String GERRIT_COMMENT_NEW_ISSUES_ONLY = "sonar.pullrequest.backend.newissueonly";
    public static final String GERRIT_VOTE_NO_ISSUE = "sonar.pullrequest.backend.votenoissue";
    public static final String GERRIT_VOTE_ISSUE_BELOW_THRESHOLD = "sonar.pullrequest.backend.votebelow";
    public static final String GERRIT_VOTE_ISSUE_ABOVE_THRESHOLD = "sonar.pullrequest.backend.voteabove";
    public static final String GERRIT_ISSUE_COMMENT = "sonar.pullrequest.backend.issuecomment";
    public static final String GERRIT_STRICT_HOSTKEY = "sonar.pullrequest.backend.sshstricthostkey";

    private PropertyKey() {
    }
}
