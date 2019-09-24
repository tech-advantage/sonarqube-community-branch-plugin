package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.factory;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.GerritConnector;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.GerritFacade;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.network.rest.GerritRestConnector;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.network.rest.GerritRestFacade;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.network.ssh.GerritSshConnector;
import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.gerrit.backend.network.ssh.GerritSshFacade;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class GerritFacadeFactory {
    private static final Logger LOG = Loggers.get(GerritFacadeFactory.class);

    private GerritFacade gerritFacade;

    public GerritFacadeFactory(GerritConnectorFactory gerritConnectorFactory) {
        GerritConnector gerritConnector = gerritConnectorFactory.getConnector();
        if (gerritConnector instanceof GerritRestConnector) {
            LOG.debug("[GERRIT PLUGIN] Using REST connector.");
            gerritFacade = new GerritRestFacade(gerritConnector);
        } else if (gerritConnector instanceof GerritSshConnector) {
            LOG.debug("[GERRIT PLUGIN] Using SSH facade.");
            gerritFacade = new GerritSshFacade(gerritConnector);
        } else {
            LOG.error("[GERRIT PLUGIN] Unknown type of connector. Cannot assign facade.");
        }
    }

    public GerritFacade getFacade() {
        return gerritFacade;
    }
}
