package com.lx862.mtrmap.wrapper.impl;

import com.lx862.mtrmap.wrapper.MTRRoutePlatform;
import org.mtr.core.data.SimplifiedRoutePlatform;

public class MTRSimplifiedRoutePlatformImpl implements MTRRoutePlatform {
    private final SimplifiedRoutePlatform instance;

    public MTRSimplifiedRoutePlatformImpl(SimplifiedRoutePlatform simplifiedRoutePlatform) {
        this.instance = simplifiedRoutePlatform;
    }

    @Override
    public long getPlatformId() {
        return this.instance.getPlatformId();
    }
}
