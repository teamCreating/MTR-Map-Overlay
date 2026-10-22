package io.github.teamcreating.mtrmap.integration.journeymap;

import io.github.teamcreating.mtrmap.MTRMap;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.event.ClientEvent;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JourneyMap 5 plugin for displaying MTR station/depot markers on the map.
 * Discovered and instantiated by JourneyMap through the {@link ClientPlugin}
 * annotation (JourneyMap only loads this class when JourneyMap itself is
 * installed).
 *
 * <p>Do NOT reference this class from mod code outside this package - other
 * classes reach the API through {@link JourneyMapIntegration}, which guards
 * class loading.</p>
 */
@ParametersAreNonnullByDefault
@ClientPlugin
public class MTRJourneyMapPlugin implements IClientPlugin {
    private static IClientAPI clientAPI = null;

    @Override
    public void initialize(IClientAPI api) {
        clientAPI = api;
        JourneyMapToolbar.register();
        MTRMap.LOGGER.info("[{}] JourneyMap 5 API initialized!", MTRMap.MOD_NAME);
    }

    @Override
    public String getModId() {
        return MTRMap.MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent event) { }

    public static IClientAPI getAPI() {
        return clientAPI;
    }
}
