package io.github.teamcreating.mtrmap.wrapper.impl;

import io.github.teamcreating.mtrmap.wrapper.MTRRoutePlatform;
import org.mtr.core.data.RoutePlatformData;

public class MTRRoutePlatformDataImpl implements MTRRoutePlatform {
    private final RoutePlatformData instance;

    public MTRRoutePlatformDataImpl(RoutePlatformData data) {
        this.instance = data;
    }

    public long getPlatformId() {
        return instance.getPlatform().getId();
    }
}
