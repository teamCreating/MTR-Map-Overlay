package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OverlayContractsTest {
    private final WorldRef world = new WorldRef(UUID.randomUUID(), "minecraft:overworld");
    private Catalog catalog(boolean multiple) {
        var stops = List.of(new Stop(0,"a","A",""),new Stop(1,"b","B",""),new Stop(2,"a","A",""),new Stop(3,"d","D",""));
        var legs = List.of(new Leg(0,1,List.of(new RailSpan("ab",.5,1))),new Leg(1,2,List.of(new RailSpan("ba",1,0))),new Leg(2,3,List.of(new RailSpan("ad",0,.5))));
        var path = new PathVariant("depot:0",legs);
        return new Catalog(world, 12, 24, Status.READY, List.of(),List.of(),List.of(new Route("r","Same name","1",0x123456,"NORMAL",stops,multiple?List.of(path,new PathVariant("depot:4",legs)):List.of(path))));
    }
    @Test void catalogAndBoardRoundTripWithNativeTimesAndRepeatedPlatformIdentity() throws Exception {
        var c = catalog(true); assertEquals(c, OverlayCodec.decode(OverlayCodec.encode(c), Catalog.class));
        var a = new Arrival("r","Green",0x22AA22,"a","2","終點|Terminus",10000,13000,-250,true,false);
        var b = new Board(world,"1",45,Status.LIMITED,7000,7500,List.of(new Service("r","a","Green",0x22AA22,"2")),List.of(a));
        assertEquals(b, OverlayCodec.decode(OverlayCodec.encode(b), Board.class));
        assertThrows(UnsupportedOperationException.class, () -> b.arrivals().clear());
    }
    @Test void malformedAndTrailingTransfersCannotBecomePublishedSnapshots() throws Exception {
        var bytes = OverlayCodec.encode(catalog(false));
        assertThrows(IOException.class, () -> OverlayCodec.decode(Arrays.copyOf(bytes,bytes.length-1), Catalog.class));
        assertThrows(IOException.class, () -> OverlayCodec.decode(Arrays.copyOf(bytes,bytes.length+1), Catalog.class));
        assertThrows(IllegalArgumentException.class, () -> new OverlayAssembly(5000, 10));
        var assembly = new OverlayAssembly(1, bytes.length);
        assertTrue(assembly.add(0,1,bytes.length,bytes));
        var conflict = bytes.clone(); conflict[0] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> assembly.add(0,1,bytes.length,conflict));
        assertArrayEquals(bytes,assembly.assemble());
    }
    @Test void repeatedStopsUseOccurrenceAndPathsCannotSilentlyChooseFirst() {
        var section = new RouteSection("r",2,3,Optional.empty());
        var result = SectionResolver.resolve(catalog(false),List.of(section));
        assertEquals(Status.READY,result.status()); assertEquals("ad",result.spans().get(0).rail().railId());
        assertEquals(Status.AMBIGUOUS,SectionResolver.resolve(catalog(true),List.of(section)).status());
        assertEquals(Status.READY,SectionResolver.resolve(catalog(true),List.of(new RouteSection("r",2,3,Optional.of("depot:4")))).status());
        assertEquals(Status.INVALID,SectionResolver.resolve(catalog(false),List.of(new RouteSection("r",3,0,Optional.empty()))).status());
        assertEquals(Status.UNAVAILABLE,SectionResolver.resolve(catalog(false),List.of(new RouteSection("r",0,1,Optional.of("missing")))).status());
    }
    private ViewRequest request(long revision) { return new ViewRequest(world,revision,List.of(new SelectedSpan("r",new RailSpan("ab",0,.5))),Set.of(),Unmatched.HIDE,Map.of(),true,Set.of()); }
    @Test void ownedSessionsRestorePreviousFocusAndRejectWrongRevisionAtomically() throws Exception {
        var registry = new ViewRegistry(); var c = catalog(false);
        var first = registry.open("one:nav","One",Purpose.FOCUS,0,w->c);
        assertEquals(Status.READY,first.replace(request(12)).toCompletableFuture().get());
        var second = registry.open("two:nav","Two",Purpose.FOCUS,10,w->c);
        second.replace(request(12)); assertEquals("two:nav",registry.focus(world.dimensionId()).orElseThrow().owner());
        assertEquals(Status.STALE,second.replace(request(13)).toCompletableFuture().get());
        assertEquals("two:nav",registry.focus(world.dimensionId()).orElseThrow().owner());
        registry.toggleOverview(); assertTrue(registry.active(world.dimensionId()).isEmpty());
        second.replace(request(12)); assertTrue(registry.active(world.dimensionId()).isEmpty(),"External replace cannot override user's overview");
        registry.toggleOverview(); second.close(); assertEquals("one:nav",registry.focus(world.dimensionId()).orElseThrow().owner());
        registry.clear(); assertEquals(Status.CLOSED,first.replace(request(12)).toCompletableFuture().get());
    }
    @Test void invalidStyleAndFractionsAreRejectedAtTheContractBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new RailSpan("x",Double.NaN,1));
        assertThrows(IllegalArgumentException.class, () -> new Style(Optional.empty(),1,Double.POSITIVE_INFINITY,Pattern.SOLID));
        assertThrows(IllegalArgumentException.class, () -> new Style(Optional.empty(),2,1,Pattern.SOLID));
    }
}
