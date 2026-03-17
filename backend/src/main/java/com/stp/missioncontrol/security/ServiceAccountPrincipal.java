package com.stp.missioncontrol.security;

import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import com.stp.missioncontrol.model.MissionControlEnums.TokenScope;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

public record ServiceAccountPrincipal(
        UUID serviceAccountId,
        UUID tokenId,
        String name,
        Set<TokenScope> scopes,
        Set<ClusterEnvironment> allowedEnvironments,
        Set<UUID> allowedClusterIds
) implements Principal {

    @Override
    public String getName() {
        return name;
    }

    public boolean hasScope(TokenScope scope) {
        return scopes.contains(scope);
    }

    public boolean canAccessCluster(Cluster cluster) {
        boolean clusterAllowed = allowedClusterIds.isEmpty() || allowedClusterIds.contains(cluster.getId());
        boolean environmentAllowed = allowedEnvironments.contains(cluster.getEnvironment());
        return clusterAllowed && environmentAllowed;
    }
}
