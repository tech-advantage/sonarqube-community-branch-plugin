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
package com.github.mc1arke.sonarqube.plugin;

import com.github.mc1arke.sonarqube.plugin.ce.CommunityBranchEditionProvider;
import com.github.mc1arke.sonarqube.plugin.ce.CommunityReportAnalysisComponentProvider;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.GerritConstants;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.PropertyKey;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityBranchConfigurationLoader;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityBranchParamsValidator;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityProjectBranchesLoader;
import com.github.mc1arke.sonarqube.plugin.scanner.CommunityProjectPullRequestsLoader;
import com.github.mc1arke.sonarqube.plugin.server.CommunityBranchFeatureExtension;
import com.github.mc1arke.sonarqube.plugin.server.CommunityBranchSupportDelegate;
import org.sonar.api.CoreProperties;
import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;
import org.sonar.core.config.PurgeConstants;

/**
 * @author Michael Clarke
 */
public class CommunityBranchPlugin implements Plugin {

    private static final String PULL_REQUEST_CATEGORY_LABEL = "Pull Request";
    private static final String GITHUB_INTEGRATION_SUBCATEGORY_LABEL = "Integration With Github";
    private static final String GERRIT_INTEGRATION_SUBCATEGORY_LABEL = "Integration With Gerrit";

    @Override
    public void define(Context context) {
        if (SonarQubeSide.SCANNER == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(CommunityProjectBranchesLoader.class, CommunityProjectPullRequestsLoader.class,
                                  CommunityBranchConfigurationLoader.class, CommunityBranchParamsValidator.class);
        } else if (SonarQubeSide.COMPUTE_ENGINE == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(CommunityReportAnalysisComponentProvider.class, CommunityBranchEditionProvider.class);
        } else if (SonarQubeSide.SERVER == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(CommunityBranchFeatureExtension.class, CommunityBranchSupportDelegate.class);
        }

        context.addExtensions(
            /* org.sonar.db.purge.PurgeConfiguration uses the value for the this property if it's configured, so it only
            needs to be specified here, but doesn't need any additional classes to perform the relevant purge/cleanup
             */
                PropertyDefinition.builder(PurgeConstants.DAYS_BEFORE_DELETING_INACTIVE_SHORT_LIVING_BRANCHES)
                        .name("Number of days before purging inactive short living branches").description(
                        "Short living branches are permanently deleted when there are no analysis for the configured number of days.")
                        .category(CoreProperties.CATEGORY_GENERAL)
                        .subCategory(CoreProperties.SUBCATEGORY_DATABASE_CLEANER).defaultValue("30")
                        .type(PropertyType.INTEGER).build(),

                //the name and description shown on the UI are automatically loaded from core.properties so don't need to be specified here
                PropertyDefinition.builder(CoreProperties.LONG_LIVED_BRANCHES_REGEX).onQualifiers(Qualifiers.PROJECT)
                        .category(CoreProperties.CATEGORY_GENERAL).subCategory(CoreProperties.SUBCATEGORY_BRANCHES)
                        .defaultValue(CommunityBranchConfigurationLoader.DEFAULT_BRANCH_REGEX).build(),

                PropertyDefinition.builder("sonar.pullrequest.provider").subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory("General")
                        .onlyOnQualifiers(Qualifiers.PROJECT).name("Provider").type(PropertyType.SINGLE_SELECT_LIST)
                        .options("Gerrit", "Github").build(),

                PropertyDefinition.builder("sonar.alm.github.app.privateKey.secured")
                        .subCategory(PULL_REQUEST_CATEGORY_LABEL).subCategory(GITHUB_INTEGRATION_SUBCATEGORY_LABEL)
                        .onQualifiers(Qualifiers.APP).name("App Private Key")
                        .type(PropertyType.PASSWORD).build(),

                PropertyDefinition.builder("sonar.alm.github.app.name").subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GITHUB_INTEGRATION_SUBCATEGORY_LABEL).onQualifiers(Qualifiers.APP).name("App Name")
                        .defaultValue("SonarQube Community Pull Request Analysis").type(PropertyType.STRING).build(),

                PropertyDefinition.builder("sonar.alm.github.app.id").subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GITHUB_INTEGRATION_SUBCATEGORY_LABEL).onQualifiers(Qualifiers.APP).name("App ID")
                        .type(PropertyType.STRING).build(),

                PropertyDefinition.builder("sonar.pullrequest.github.repository")
                        .subCategory(PULL_REQUEST_CATEGORY_LABEL).subCategory(GITHUB_INTEGRATION_SUBCATEGORY_LABEL)
                        .onlyOnQualifiers(Qualifiers.PROJECT)
                        .name("Repository identifier").description("Example: SonarSource/sonarqube")
                        .type(PropertyType.STRING).build(),

                PropertyDefinition.builder("sonar.pullrequest.github.endpoint").subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GITHUB_INTEGRATION_SUBCATEGORY_LABEL).onQualifiers(Qualifiers.APP)
                        .name("The API URL for a GitHub instance").description(
                        "The API url for a GitHub instance. https://api.github.com/ for github.com, https://github.company.com/api/ when using GitHub Enterprise")
                        .type(PropertyType.STRING).defaultValue("https://api.github.com").build()

                             );

        PropertyDefinition.builder(PropertyKey.GERRIT_SCHEME)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .type(PropertyType.SINGLE_SELECT_LIST)
                .options(GerritConstants.SCHEME_HTTP, GerritConstants.SCHEME_HTTPS, GerritConstants.SCHEME_SSH)
                .defaultValue(GerritConstants.SCHEME_HTTP).build();

        PropertyDefinition.builder(PropertyKey.GERRIT_HOST)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .build();

        PropertyDefinition.builder(PropertyKey.GERRIT_PORT)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .type(PropertyType.INTEGER).defaultValue("80").build();

        PropertyDefinition.builder(PropertyKey.GERRIT_USERNAME)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .build();

        PropertyDefinition.builder(PropertyKey.GERRIT_PASSWORD)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .type(PropertyType.PASSWORD).build();

        PropertyDefinition.builder(PropertyKey.GERRIT_SSH_KEY_PATH)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .type(PropertyType.STRING).build();

        PropertyDefinition.builder(PropertyKey.GERRIT_STRICT_HOSTKEY)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .type(PropertyType.BOOLEAN).defaultValue(GerritConstants.GERRIT_STRICT_HOSTKEY_DEFAULT)
                .build();

        PropertyDefinition.builder(PropertyKey.GERRIT_HTTP_AUTH_SCHEME)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .type(PropertyType.SINGLE_SELECT_LIST).options(GerritConstants.AUTH_BASIC, GerritConstants.AUTH_DIGEST)
                .defaultValue(GerritConstants.AUTH_DIGEST).build();

        PropertyDefinition.builder(PropertyKey.GERRIT_BASE_PATH)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_SERVER)
                .defaultValue("/").build();

        PropertyDefinition.builder(PropertyKey.GERRIT_LABEL)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW)
                .defaultValue("Code-Review").build();

        PropertyDefinition.builder(PropertyKey.GERRIT_MESSAGE)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW)
                .defaultValue("Sonar review at ${sonar.host.url}").build();

        PropertyDefinition.builder(PropertyKey.GERRIT_COMMENT_NEW_ISSUES_ONLY)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW)
                .type(PropertyType.BOOLEAN).defaultValue(GerritConstants.GERRIT_COMMENT_NEW_ISSUES_ONLY)
                .onQualifiers(Qualifiers.PROJECT).build();

        PropertyDefinition.builder(PropertyKey.GERRIT_THRESHOLD)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW)
                .type(PropertyType.SINGLE_SELECT_LIST)
                .options(Severity.INFO.toString(), Severity.MINOR.toString(), Severity.MAJOR.toString(),
                        Severity.CRITICAL.toString(), Severity.BLOCKER.toString())
                .defaultValue(Severity.INFO.toString()).onQualifiers(Qualifiers.PROJECT)
                .build();

        PropertyDefinition.builder(PropertyKey.GERRIT_VOTE_NO_ISSUE)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW)
                .type(PropertyType.SINGLE_SELECT_LIST).options("+1", "+2")
                .defaultValue(GerritConstants.GERRIT_VOTE_NO_ISSUE_DEFAULT)
                .onQualifiers(Qualifiers.PROJECT).build();

        PropertyDefinition
                .builder(PropertyKey.GERRIT_VOTE_ISSUE_BELOW_THRESHOLD).category(GerritConstants.GERRIT_CATEGORY)
                .subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW).type(PropertyType.SINGLE_SELECT_LIST)
                .options("-2", "-1", "0", "+1", "+2")
                .defaultValue(GerritConstants.GERRIT_VOTE_ISSUE_BELOW_THRESHOLD_DEFAULT)
                .onQualifiers(Qualifiers.PROJECT).build();

        PropertyDefinition
                .builder(PropertyKey.GERRIT_VOTE_ISSUE_ABOVE_THRESHOLD).category(GerritConstants.GERRIT_CATEGORY)
                .subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW).type(PropertyType.SINGLE_SELECT_LIST)
                .options("-2", "-1", "0").defaultValue(GerritConstants.GERRIT_VOTE_ISSUE_ABOVE_THRESHOLD_DEFAULT)
                .onQualifiers(Qualifiers.PROJECT).build();

        PropertyDefinition.builder(PropertyKey.GERRIT_ISSUE_COMMENT)
                .category(GerritConstants.GERRIT_CATEGORY).subCategory(GerritConstants.GERRIT_SUBCATEGORY_REVIEW)
                .defaultValue(
                        "[${issue.isNew}] New: ${issue.ruleKey} Severity: ${issue.severity}, Message: ${issue.message}")
                .build();

    }

}
