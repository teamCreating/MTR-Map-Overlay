package com.lx862.mtrmap.wrapper.impl;

import com.lx862.mtrmap.wrapper.MTRRoute;
import com.lx862.mtrmap.wrapper.MTRRoutePlatform;
import org.mtr.core.data.SimplifiedRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MTRSimplifiedRouteImpl implements MTRRoute {
    private final SimplifiedRoute instance;

    public MTRSimplifiedRouteImpl(SimplifiedRoute route) {
        this.instance = route;
    }

    @Override
    public String getName() {
        return this.instance.getName();
    }

    @Override
    public int getColor() {
        return this.instance.getColor();
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public List<MTRRoutePlatform> getRoutePlatforms() {
        return new ArrayList<>(this.instance.getPlatforms()).stream().map(MTRSimplifiedRoutePlatformImpl::new).collect(Collectors.toList());
    }
}
