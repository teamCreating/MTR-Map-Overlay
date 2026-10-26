package io.github.teamcreating.mtrmap.extension;
import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrmap.mapdata.*;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.*;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OverlayGeometryTest {
    @Test void navigationDimsCompletedAndUnusedIntervalsOnTheSameRailAndBacking() {
        var rail=rail();
        var route=MapRoute.ofTracks("green","Green",0x00AA00,RouteLineStyle.HIGH_SPEED,List.of(),List.of(rail));
        var input=new MapDataCache.DimensionData("minecraft/overworld",List.of(route),List.of(rail),List.of(),4);
        var world=new WorldRef(UUID.randomUUID(),"minecraft:overworld");
        var request=ViewRequest.navigation(world,1,List.of(new SelectedSpan("green",new RailSpan(rail.id,.5,.8))),
                List.of(new SelectedSpan("green",new RailSpan(rail.id,0,.5))),.35,.1);
        var active=new ViewRegistry.Active("test:nav","Test",Purpose.FOCUS,0,1,request);
        var result=ViewRenderer.build(input,List.of(active),active);
        assertEquals(3,result.tracks.size());
        double[] opacity={.35,1,.1};
        for(int i=0;i<3;i++) {
            var t=result.tracks.get(i); assertEquals(opacity[i],t.opacity());assertEquals(opacity[i],t.forScale(32).opacity());
            assertEquals(opacity[i],result.routePalette.get(t.id).get(0).opacity());
            var scene=PlanarMapScene.build(List.of(t),Map.of(t.id,result.routePalette.get(t.id)),Map.of(t.id,result.trackPatterns.get(t.id)),List.of(),true,true,8,-100,-100,200,200);
            int whiteAlpha=(int)(255*opacity[i]);
            assertTrue(scene.quads().stream().anyMatch(q -> q.color()==0xFFFFFF && q.alpha()==whiteAlpha),"White blocks must dim with their route band");
            int backingAlpha=(int)(RailRenderStyle.TRACK_ALPHA*opacity[i]);
            assertTrue(scene.quads().stream().anyMatch(q -> q.color()==RailRenderStyle.TRACK_COLOR && q.alpha()==backingAlpha),"Backing must not remain fully opaque");
        }
        assertEquals(1,input.routePalette.get(rail.id).get(0).opacity());
    }
    @Test void repeatedRideStillHighlightsTheUpcomingIntervalAndCatalogRestoresDeduplicatedIdentity() {
        var rail=rail();var world=new WorldRef(UUID.randomUUID(),"minecraft:overworld");
        var span=new SelectedSpan("native",new RailSpan(rail.id,.2,.6));
        var request=ViewRequest.navigation(world,1,List.of(span),List.of(span),.35,.1);
        var input=new MapDataCache.DimensionData("minecraft/overworld",List.of(),List.of(rail),List.of(),4);
        var catalog=new Catalog(world,1,4,Status.READY,List.of(),List.of(),List.of(new io.github.teamcreating.mtrmap.api.MtrMapApi.Route("native","Green","",0x00AA00,"HIGH_SPEED",List.of(),List.of())));
        var active=new ViewRegistry.Active("test:nav","Test",Purpose.FOCUS,0,1,request);
        var result=ViewRenderer.build(input,List.of(active),active,catalog);
        var middle=result.tracks.get(1);assertEquals(1,result.routePalette.get(middle.id).get(0).opacity());
        assertEquals("native",result.routePalette.get(middle.id).get(0).id());
        assertTrue(result.routePalette.get(middle.id).get(0).colourWhite());
    }
    private MapTrack rail() {
        return TrackSampler.track(Rail.newRail(new Position(0,-30,0),Angle.E,new Position(100,40,80),Angle.W,Rail.Shape.QUADRATIC,
                0,new ObjectArrayList<>(),1,2,false,false,true,false,true,TransportMode.TRAIN));
    }
    @Test void halfRailUsesNativeXYZAndRetainsZoomAdaptiveCurvature() {
        var source=rail(); var half=new SpanMapTrack(source,.25,.75,"half");
        for(double scale:new double[]{.25,4,32}) {
            var mesh=half.forScale(scale); assertEquals(source.length()*.5,mesh.length(),1E-7);
            for(int i=0;i<mesh.points.size();i++) {
                var p=source.curve.position(source.length()*.25+mesh.distances[i]);
                assertEquals(p.x(),mesh.points.get(i)[0],1E-7); assertEquals(p.y(),mesh.height(i),1E-7); assertEquals(p.z(),mesh.points.get(i)[1],1E-7);
            }
        }
        assertTrue(half.forScale(32).points.size()>half.forScale(.25).points.size());
    }
    @Test void focusHidesUnriddenPartsAndSharedBandsWithoutMutatingSource() {
        var rail=rail();
        var green=MapRoute.ofTracks("green","Green",0x00AA00,List.of(),List.of(rail));
        var red=MapRoute.ofTracks("red","Red",0xAA0000,List.of(),List.of(rail));
        var input=new MapDataCache.DimensionData("minecraft/overworld",List.of(green,red),List.of(rail),List.of(),4);
        var request=new ViewRequest(new WorldRef(UUID.randomUUID(),"minecraft:overworld"),1,
                List.of(new SelectedSpan("green",new RailSpan(rail.id,.2,.6))),Set.of(),Unmatched.HIDE,
                Map.of("green",new Style(Optional.of(0x112233),.5,2,Pattern.COLOUR_WHITE)),false,Set.of());
        var active=new ViewRegistry.Active("test:nav","Test",Purpose.FOCUS,0,1,request);
        var result=ViewRenderer.build(input,List.of(active),active);
        assertEquals(1,result.tracks.size()); assertEquals(rail.length()*.4,result.tracks.get(0).length(),1E-7);
        var band=result.routePalette.values().iterator().next().get(0);
        assertEquals("green",band.id()); assertEquals(0x112233,band.color()); assertEquals(.5,band.opacity()); assertEquals(2,band.widthScale()); assertTrue(band.colourWhite());
        assertEquals(2,input.routePalette.get(rail.id).size()); assertEquals(1,input.tracks.size());
        var scene=PlanarMapScene.build(result.tracks,result.routePalette,result.trackPatterns,List.of(),true,true,8,-100,-100,200,200);
        assertFalse(scene.quads().isEmpty());
    }
}
