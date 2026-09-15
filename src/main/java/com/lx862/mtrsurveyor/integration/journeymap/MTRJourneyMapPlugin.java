package com.lx862.mtrsurveyor.integration.journeymap;

import com.lx862.mtrsurveyor.MTRSurveyor;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JourneyMap v2 plugin for displaying MTR station/depot markers on the map.
 * Discovered and instantiated by JourneyMap through the {@link JourneyMapPlugin}
 * annotation (JourneyMap only loads this class when JourneyMap itself is
 * installed).
 *
 * <p>Do NOT reference this class from mod code outside this package - other
 * classes reach the API through {@link JourneyMapIntegration}, which guards
 * class loading.</p>
 */
@ParametersAreNonnullByDefault
@JourneyMapPlugin(apiVersion = "2.0.0")
public class MTRJourneyMapPlugin implements IClientPlugin {
    private static IClientAPI clientAPI = null;

    @Override
    public void initialize(IClientAPI api) {
        clientAPI = api;
        MTRSurveyor.LOGGER.info("[{}] JourneyMap v2 API initialized!", MTRSurveyor.MOD_NAME);
    }

    @Override
    public String getModId() {
        return MTRSurveyor.MOD_ID;
    }

    public static IClientAPI getAPI() {
        return clientAPI;
    }
}
