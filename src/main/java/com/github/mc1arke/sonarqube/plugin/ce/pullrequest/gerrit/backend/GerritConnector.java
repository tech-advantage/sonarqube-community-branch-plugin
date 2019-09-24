package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend;

import java.io.IOException;

public interface GerritConnector {
    public String listFiles() throws IOException;

    public String setReview(String reviewInputAsJson) throws IOException;
}
