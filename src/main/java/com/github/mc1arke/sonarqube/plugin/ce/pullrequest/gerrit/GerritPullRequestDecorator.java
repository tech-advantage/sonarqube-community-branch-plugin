/*
 * Copyright (C) 2019 Michael Clarke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.PostAnalysisIssueVisitor;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.PullRequestBuildStatusDecorator;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.GerritFacade;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.factory.GerritFacadeFactory;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.review.ReviewInput;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.ce.posttask.Analysis;
import org.sonar.api.ce.posttask.PostProjectAnalysisTask;
import org.sonar.api.ce.posttask.QualityGate;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.platform.Server;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.ce.task.projectanalysis.component.ConfigurationRepository;
import org.sonar.ce.task.projectanalysis.component.TreeRootHolder;
import org.sonar.ce.task.projectanalysis.measure.Measure;
import org.sonar.ce.task.projectanalysis.measure.MeasureRepository;
import org.sonar.ce.task.projectanalysis.metric.MetricRepository;
import org.sonar.scanner.protocol.GsonHelper;
import org.sonar.server.measure.Rating;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GerritPullRequestDecorator implements PullRequestBuildStatusDecorator {

    private static final Logger LOG = Loggers.get(GerritPullRequestDecorator.class);
    private static final List<String> OPEN_ISSUE_STATUSES =
            Issue.STATUSES.stream().filter(s -> !Issue.STATUS_CLOSED.equals(s) && !Issue.STATUS_RESOLVED.equals(s))
                    .collect(Collectors.toList());

    private final ConfigurationRepository configurationRepository;
    private final Server server;
    private final MetricRepository metricRepository;
    private final MeasureRepository measureRepository;
    private final TreeRootHolder treeRootHolder;
    private final PostAnalysisIssueVisitor postAnalysisIssueVisitor;


    private final Settings settings;
    private final GerritConfiguration gerritConfiguration;
    private List<String> gerritModifiedFiles;
    private GerritFacade gerritFacade;
    private ReviewInput reviewInput = ReviewHolder.getReviewInput();

    public GerritPullRequestDecorator(Server server, ConfigurationRepository configurationRepository,
                                      MeasureRepository measureRepository, MetricRepository metricRepository,
                                      TreeRootHolder treeRootHolder,
                                      PostAnalysisIssueVisitor postAnalysisIssueVisitor, Settings settings, GerritConfiguration gerritConfiguration,
                                      GerritFacadeFactory gerritFacadeFactory) {
        super();
        LOG.debug("[GERRIT PLUGIN] Instanciating GerritPullRequestDecorator");
        this.configurationRepository = configurationRepository;
        this.server = server;
        this.measureRepository = measureRepository;
        this.metricRepository = metricRepository;
        this.treeRootHolder = treeRootHolder;
        this.postAnalysisIssueVisitor = postAnalysisIssueVisitor;
        this.settings = settings;
        this.gerritFacade = gerritFacadeFactory.getFacade();
        this.gerritConfiguration = gerritConfiguration;
    }

    @Override
    public void decorateQualityGateStatus(PostProjectAnalysisTask.ProjectAnalysis projectAnalysis) {

        Optional<Analysis> optionalAnalysis = projectAnalysis.getAnalysis();
        if (!optionalAnalysis.isPresent()) {
            LOG.warn(
                    "No analysis results were created for this project analysis. This is likely to be due to an earlier failure");
            return;
        }

        Analysis analysis = optionalAnalysis.get();

        Optional<String> revision = analysis.getRevision();
        if (!revision.isPresent()) {
            LOG.warn("No commit details were submitted with this analysis. Check the project is committed to Git");
            return;
        }

        if (null == projectAnalysis.getQualityGate()) {
            LOG.warn("No quality gate was found on the analysis, so no results are available");
            return;
        }

        String commitId = revision.get();

        try {
            Configuration configuration = configurationRepository.getConfiguration();
            String apiUrl = getMandatoryProperty("sonar.pullrequest.backend.endpoint", configuration);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + repositoryAuthenticationToken.getAuthenticationToken());
            headers.put("Accept", "application/vnd.github.antiope-preview+json");

            String status =
                    (QualityGate.Status.OK == projectAnalysis.getQualityGate().getStatus() ? "Passed" : "Failed");

            List<QualityGate.Condition> failedConditions = projectAnalysis.getQualityGate().getConditions().stream()
                    .filter(c -> c.getStatus() != QualityGate.EvaluationStatus.OK).collect(Collectors.toList());

            QualityGate.Condition newCoverageCondition = projectAnalysis.getQualityGate().getConditions().stream()
                    .filter(c -> CoreMetrics.NEW_COVERAGE_KEY.equals(c.getMetricKey())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find New Coverage Condition in analysis"));
            String estimatedCoverage = measureRepository
                    .getRawMeasure(treeRootHolder.getRoot(), metricRepository.getByKey(CoreMetrics.COVERAGE_KEY))
                    .map(Measure::getData).orElse("0");

            QualityGate.Condition newDuplicationCondition = projectAnalysis.getQualityGate().getConditions().stream()
                    .filter(c -> CoreMetrics.NEW_DUPLICATED_LINES_DENSITY_KEY.equals(c.getMetricKey())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Could not find New Duplicated Lines Condition in analysis"));
            String estimatedDuplications = measureRepository.getRawMeasure(treeRootHolder.getRoot(), metricRepository
                    .getByKey(CoreMetrics.DUPLICATED_LINES_KEY)).map(Measure::getData).orElse("0");


            Map<RuleType, Long> issueCounts =
                    Arrays.stream(
                            RuleType.values()).
                            collect(Collectors.toMap(k -> k,
                    k -> postAnalysisIssueVisitor
                    .getIssues()
                    .stream()
                    .filter(i -> OPEN_ISSUE_STATUSES
                        .contains(
                            i.status()))
                    .filter(i -> k ==
                        i.type())
                    .count()));

            String summaryBuilder = status + "\n" + failedConditions.stream().map(c -> "- " + format(c))
                    .collect(Collectors.joining("\n")) + "\n# Analysis Details\n" + "## " +
                                    issueCounts.entrySet().stream().mapToLong(Map.Entry::getValue).sum() + " Issues\n" +
                                    " - " + pluralOf(issueCounts.get(RuleType.BUG), "Bug", "Bugs") + "\n" + " - " +
                                    pluralOf(issueCounts.get(RuleType.VULNERABILITY) +
                                             issueCounts.get(RuleType.SECURITY_HOTSPOT), "Vulnerability",
                                             "Vulnerabilities") + "\n" + " - " +
                                    pluralOf(issueCounts.get(RuleType.CODE_SMELL), "Code Smell", "Code Smells") + "\n" +
                                    "## Coverage and Duplications\n" + " - " + newCoverageCondition.getValue() +
                                    "% Coverage (" + estimatedCoverage + "% Estimated after merge)\n" + " - " +
                                    newDuplicationCondition.getValue() + "% Duplicated Code (" + estimatedDuplications +
                                    "% Estimated after merge)\n";

            InputObject<String> checkRunOutputContent =
                    new InputObject.Builder<String>().put("title", "Quality Gate " + status.toLowerCase(Locale.ENGLISH))
                            .put("summary", summaryBuilder).build();

            InputObject<Object> repositoryInputObject =
                    new InputObject.Builder<>().put("repositoryId", repositoryAuthenticationToken.getRepositoryId())
                            .put("name", appName + " Results").put("headSha", commitId)
                            .put("status", RequestableCheckStatusState.COMPLETED).put("conclusion",
                                                                                      QualityGate.Status.OK ==
                                                                                      projectAnalysis.getQualityGate()
                                                                                              .getStatus() ?
                                                                                      CheckConclusionState.SUCCESS :
                                                                                      CheckConclusionState.FAILURE)
                            .put("detailsUrl",
                                 String.format("%s/dashboard?id=%s&pullRequest=%s", server.getPublicRootUrl(),
                                               URLEncoder.encode(projectAnalysis.getProject().getKey(),
                                                                 StandardCharsets.UTF_8.name()), URLEncoder
                                                       .encode(projectAnalysis.getBranch().get().getName().get(),
                                                               StandardCharsets.UTF_8.name()))).put("startedAt",
                                                                                                    new SimpleDateFormat(
                                                                                                            "yyyy-MM-dd'T'HH:mm:ssXXX")
                                                                                                            .format(analysis.getDate()))
                            .put("completedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date()))
                            .put("externalId", analysis.getAnalysisUuid()).put("output", checkRunOutputContent).build();


            GraphQLRequestEntity graphQLRequestEntity =
                    GraphQLRequestEntity.Builder().url(apiUrl + "/graphql").headers(headers)
                            .request(CreateCheckRun.class)
                            .arguments(new Arguments("createCheckRun", new Argument<>("input", repositoryInputObject)))
                            .requestMethod(GraphQLTemplate.GraphQLMethod.MUTATE).build();

            LOG.debug("Using request: " + graphQLRequestEntity.getRequest());

            GraphQLTemplate graphQLTemplate = new GraphQLTemplate();

            GraphQLResponseEntity<CreateCheckRun> response =
                    graphQLTemplate.mutate(graphQLRequestEntity, CreateCheckRun.class);

            LOG.debug("Received response: " + response.toString());

            if (null != response.getErrors() && response.getErrors().length > 0) {
                for (Error error : response.getErrors()) {
                    LOG.warn(error.toString());
                }
                throw new IllegalStateException(
                        "An error was returned in the response from the Github API. See the previous log messages for details");
            }
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Could not decorate Pull Request on Github", ex);
        }

    }

    protected void decorate(InputPath resource, PostJobContext context, Collection<PostJobIssue> issues) {
        LOG.debug("[GERRIT PLUGIN] Decorate: {}", resource.relativePath());
        if (!resource.file().isFile()) {
            LOG.debug("[GERRIT PLUGIN] {} is not a file", resource.relativePath());
            return;
        }

        try {
            LOG.debug("[GERRIT PLUGIN] Start Sonar decoration for Gerrit");
            assertOrFetchGerritModifiedFiles();
        } catch (GerritPluginException e) {
            LOG.error("[GERRIT PLUGIN] Error getting Gerrit datas", e);
            return;
        }

        LOG.debug("[GERRIT PLUGIN] Look for in Gerrit if the file was under review, resource={}", resource);
        LOG.debug("[GERRIT PLUGIN] Look for in Gerrit if the file was under review, name={}", resource.relativePath());
        LOG.debug("[GERRIT PLUGIN] Look for in Gerrit if the file was under review, key={}", resource.key());

        String filename = getFileNameFromInputPath(resource);
        if (filename != null) {
            LOG.info("[GERRIT PLUGIN] Found a match between Sonar and Gerrit for {}: ", resource.relativePath(),
                    filename);
            processFileResource(filename, issues);
        }
    }

    protected void assertOrFetchGerritModifiedFiles() throws GerritPluginException {
        if (gerritModifiedFiles != null) {
            return;
        }
        gerritModifiedFiles = gerritFacade.listFiles();
        LOG.debug("[GERRIT PLUGIN] Modified files in gerrit : {}", gerritModifiedFiles);
    }

    protected ReviewLineComment issueToComment(PostJobIssue issue) {
        ReviewLineComment result = new ReviewLineComment();

        result.setLine(issue.line());
        result.setSeverity(ReviewUtils.thresholdToValue(issue.severity().toString()));

        result.setMessage(MessageUtils.createIssueMessage(gerritConfiguration.getIssueComment(), settings, issue));
        LOG.debug("[GERRIT PLUGIN] issueToComment {}", result.toString());
        return result;
    }

    protected void processFileResource(@NotNull String file, @NotNull Collection<PostJobIssue> issuable) {
        List<ReviewFileComment> comments = new ArrayList<>();
        commentIssues(issuable, comments);
        if (!comments.isEmpty()) {
            reviewInput.addComments(file, comments);
        }
    }

    private void commentIssues(Collection<PostJobIssue> issues, List<ReviewFileComment> comments) {
        LOG.info("[GERRIT PLUGIN] Found {} issues", issues.size());

        for (PostJobIssue issue : issues) {
            if (gerritConfiguration.shouldCommentNewIssuesOnly() && !issue.isNew()) {
                LOG.info(
                        "[GERRIT PLUGIN] Issue is not new and only new one should be commented. Will not push back to Gerrit. Issue: {}", issue);
            } else {
                comments.add(issueToComment(issue));
            }
        }
    }

    private String getFileNameFromInputPath(InputPath resource) {
        String filename = null;
        if (gerritModifiedFiles.contains(resource.relativePath())) {
            LOG.info("[GERRIT PLUGIN] Found a match between Sonar and Gerrit for {}", resource.relativePath());
            filename = resource.relativePath();
        } else if (gerritModifiedFiles.contains(gerritFacade.parseFileName(resource.relativePath()))) {
            LOG.info("[GERRIT PLUGIN] Found a match between Sonar and Gerrit for {}",
                    gerritFacade.parseFileName(resource.relativePath()));
            filename = gerritFacade.parseFileName(resource.relativePath());
        } else {
            LOG.debug("[GERRIT PLUGIN] Parse the Gerrit List to look for the resource: {}", resource.relativePath());
            // Loop on each item
            for (String fileGerrit : gerritModifiedFiles) {
                if (gerritFacade.parseFileName(fileGerrit).equals(resource.relativePath())) {
                    filename = fileGerrit;
                    break;
                }
            }
        }
        if (filename == null) {
            LOG.debug("[GERRIT PLUGIN] File '{}' was not found in the review list)", resource.relativePath());
            LOG.debug("[GERRIT PLUGIN] Try to find with: '{}', '{}' and '{}'", resource.relativePath(),
                    gerritFacade.parseFileName(resource.relativePath()));
        }
        return filename;
    }

    private static String getMandatoryProperty(String propertyName, Configuration configuration) {
        return configuration.get(propertyName).orElseThrow(() -> new IllegalStateException(
                String.format("%s must be specified in the project configuration", propertyName)));
    }

    private static String format(QualityGate.Condition condition) {
        org.sonar.api.measures.Metric<?> metric = CoreMetrics.getMetric(condition.getMetricKey());
        if (metric.getType() == org.sonar.api.measures.Metric.ValueType.RATING) {
            return String
                    .format("%s %s (%s %s)", Rating.valueOf(Integer.parseInt(condition.getValue())), metric.getName(),
                            condition.getOperator() == QualityGate.Operator.GREATER_THAN ? "is worse than" :
                            "is better than", Rating.valueOf(Integer.parseInt(condition.getErrorThreshold())));
        } else {
            return String.format("%s %s (%s %s)", condition.getValue(), metric.getName(),
                                 condition.getOperator() == QualityGate.Operator.GREATER_THAN ? "is greater than" :
                                 "is less than", condition.getErrorThreshold());
        }
    }

    @Override
    public String name() {
        return "Gerrit";
    }
}
