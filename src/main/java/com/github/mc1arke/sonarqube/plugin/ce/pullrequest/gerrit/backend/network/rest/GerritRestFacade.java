package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.network.rest;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.GerritPluginException;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.GerritFacade;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.IOException;
import java.util.Map.Entry;

public class GerritRestFacade extends GerritFacade {
    private static final Logger LOG = Loggers.get(GerritRestFacade.class);
    private static final String JSON_RESPONSE_PREFIX = ")]}'";
    private static final String ERROR_LISTING = "Error listing files";

    public GerritRestFacade(GerritConnector gerritConnector) {
        super(gerritConnector);
        LOG.debug("[GERRIT PLUGIN] Instanciating GerritRestFacade");
    }

    @Override
    protected void fillListFilesFromGerrit() throws GerritPluginException {
        try {
            String rawJsonString = getGerritConnector().listFiles();
            String jsonString = trimResponse(rawJsonString);
            JsonElement rootJsonElement = new JsonParser().parse(jsonString);
            for (Entry<String, JsonElement> fileList : rootJsonElement.getAsJsonObject().entrySet()) {
                JsonObject jsonObject = fileList.getValue().getAsJsonObject();
                if (jsonObject.has("status") && isMarkAsDeleted(jsonObject)) {
                    LOG.debug("[GERRIT PLUGIN] File is marked as deleted, won't comment.");
                    continue;
                }

                addFile(fileList.getKey());
            }
        } catch (IOException e) {
            throw new GerritPluginException(ERROR_LISTING, e);
        }
    }

    private String trimResponse(String response) {
        return StringUtils.replaceOnce(response, JSON_RESPONSE_PREFIX, "");
    }

    private boolean isMarkAsDeleted(JsonObject jsonObject) {
        return jsonObject.get("status").getAsCharacter() == 'D';
    }
}
