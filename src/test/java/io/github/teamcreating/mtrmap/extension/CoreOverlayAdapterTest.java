package io.github.teamcreating.mtrmap.extension;
import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.data.*;
import org.mtr.core.serializer.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.operation.ArrivalsRequest;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.com.google.gson.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongImmutableList;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreOverlayAdapterTest {
    @TempDir Path root;
    private JsonObject position(Position p) {
        var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;
    }
    @Test void actualCoreCatalogPreservesNativeStopsAndDepotPathWithoutRunningPathfinding() {
        var sim=new Simulator("minecraft/overworld",new String[]{"minecraft/overworld"},root,false);
        try {
            var stations=new org.mtr.core.data.Station(sim);
            var area=new JsonObject();area.addProperty("name","Native station");area.add("position1",position(new Position(-10,-100,-10)));area.add("position2",position(new Position(240,100,10)));stations.updateData(new JsonReader(area));sim.stations.add(stations);
            var platforms=new ArrayList<org.mtr.core.data.Platform>();var rails=new ArrayList<Rail>();
            var paths=new ObjectArrayList<PathData>();
            for(int i=0;i<5;i++) {
                long start=i*40L;var a=new Position(start,10,0);var b=new Position(start+40,10,0);
                var rail=Rail.newPlatformRail(a,Angle.E,b,Angle.W,Rail.Shape.QUADRATIC,0,0,0,0,0,0,0,0,0,0,0,0,0,new ObjectArrayList<>(),TransportMode.TRAIN);
                sim.rails.add(rail);rails.add(rail);long platformId=0;
                if(i%2==0) {var p=new org.mtr.core.data.Platform(a,b,TransportMode.TRAIN,sim);p.rail=rail;sim.platforms.add(p);platforms.add(p);platformId=p.getId();}
                paths.add(new PathData(rail,platformId,1000,i/2,a,b));
            }
            var route=new org.mtr.core.data.Route(TransportMode.TRAIN,sim);
            for(var p:platforms) {var stop=new RoutePlatformData(p.getId());stop.platform=p;route.getRoutePlatforms().add(stop);}sim.routes.add(route);
            var depot=new Depot(TransportMode.TRAIN,sim);depot.getRouteIds().add(route.getId());sim.depots.add(depot);sim.sync();
            depot.getPath().addAll(paths);
            PathData.writePathCache(depot.getPath(),sim,TransportMode.TRAIN);
            assertEquals(5,depot.getPath().size());
            var world=new WorldRef(UUID.randomUUID(),"minecraft:overworld");var catalog=CoreOverlayAdapter.catalog(sim,world,42);
            assertEquals(3,catalog.platforms().size());assertEquals(1,catalog.routes().size());
            var exported=catalog.routes().getFirst();assertEquals(3,exported.stops().size());assertEquals(1,exported.paths().size());
            var resolved=SectionResolver.resolve(catalog,List.of(new RouteSection(exported.id(),0,1,Optional.empty())));
            assertEquals(Status.READY,resolved.status());assertEquals(3,resolved.spans().size());
            assertEquals(.5,resolved.spans().getFirst().rail().from());assertEquals(.5,resolved.spans().getLast().rail().to());
            assertEquals(rails.get(1).getHexId(),resolved.spans().get(1).rail().railId());
            var nativeArrivals=new ArrivalsRequest(new LongImmutableList(platforms.stream().mapToLong(p->p.getId()).toArray()),160,4097).getArrivals(sim);
            var board=CoreOverlayAdapter.board(sim,world,Long.toUnsignedString(stations.getId(),16),4);
            assertEquals(nativeArrivals.getArrivals().size(),board.arrivals().size());
            assertEquals(3,board.services().size());assertEquals(Status.LIMITED,board.status(),"Missing native predictions must not mean a complete no-service result");
            assertEquals(Status.NOT_FOUND,CoreOverlayAdapter.board(sim,world,"0",5).status());
        } finally {sim.stop();}
    }
}
