package com.lx862.mtrmap;

import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.wrapper.MTRRoute;
import com.lx862.mtrmap.wrapper.impl.MTRRouteImpl;
import com.lx862.mtrmap.wrapper.MTRRoutePlatform;
import com.lx862.mtrmap.wrapper.impl.MTRSimplifiedRouteImpl;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.mtr.core.data.*;
import org.mtr.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MTRDataSummary {
    private final Long2ObjectArrayMap<List<BasicRouteInfo>> stationToRoutes = new Long2ObjectArrayMap<>();
    private final Data dataInstance;

    private MTRDataSummary(Data dataInstance, List<MTRRoute> routes) {
        this.dataInstance = dataInstance;
        for (Station station : new ArrayList<>(dataInstance.stations)) {
            if (station.savedRails.isEmpty())
                continue;

            List<MTRRoute> routePassing = new ArrayList<>();
            for (MTRRoute route : new ArrayList<>(routes)) {
                for (MTRRoutePlatform routePlatformData : route.getRoutePlatforms()) {
                    for (Platform platform : station.savedRails) {
                        if (routePlatformData.getPlatformId() == platform.getId()) {
                            routePassing.add(route);
                        }
                    }
                }
            }

            List<BasicRouteInfo> basicRouteInfos = new ArrayList<>();
            for (MTRRoute route : routePassing) {
                BasicRouteInfo basicRouteInfo = BasicRouteInfo.of(route);
                if (basicRouteInfos.contains(basicRouteInfo))
                    continue;
                if (!MTRMapConfig.INSTANCE.showHiddenRoute.get() && route.isHidden())
                    continue;
                basicRouteInfos.add(basicRouteInfo);
            }

            stationToRoutes.put(station.getId(), basicRouteInfos);
        }
    }

    public static MTRDataSummary of(Data data) {
        return new MTRDataSummary(data, data.routes.stream().map(MTRRouteImpl::new).collect(Collectors.toList()));
    }

    public static MTRDataSummary of(MinecraftClientData data) {
        return new MTRDataSummary(data,
                data.simplifiedRouteIdMap.values().stream().map(MTRSimplifiedRouteImpl::new)
                        .collect(Collectors.toList()));
    }

    public Data getData() {
        return this.dataInstance;
    }

    public List<BasicRouteInfo> getRoutesInStation(Station station) {
        return stationToRoutes.get(station.getId());
    }

    public record BasicRouteInfo(String name, int color) {
        public static BasicRouteInfo of(MTRRoute MTRRoute) {
            return new BasicRouteInfo(MTRRoute.getName().split("\\|\\|")[0], MTRRoute.getColor());
        }
    }
}
