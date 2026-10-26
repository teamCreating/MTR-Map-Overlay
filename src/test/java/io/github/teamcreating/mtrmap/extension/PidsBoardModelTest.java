package io.github.teamcreating.mtrmap.extension;
import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrmap.config.PidsStyle;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PidsBoardModelTest {
    final WorldRef world=new WorldRef(UUID.randomUUID(),"minecraft:overworld");
    final Service service=new Service("r","p","Local",0x11AA22,"A long platform name");
    Arrival arrival(String destination,long time,long departure) {
        return new Arrival("r","Local",0x11AA22,"p",service.platformName(),destination,time,departure,999999,true,false);
    }
    Board board(List<Arrival> arrivals) { return new Board(world,"s",1,Status.LIMITED,1000,1000,List.of(service),arrivals); }
    @Test void countdownUsesNativeTimestampsAndDoesNotAddDeviation() {
        var rows=PidsBoardModel.rows(board(List.of(arrival("Alpha",121000,130000))),1000);
        assertEquals(PidsBoardModel.Eta.MINUTES,rows.get(0).eta());assertEquals(2,rows.get(0).value());
        assertEquals(59,PidsBoardModel.rows(board(List.of(arrival("Alpha",60000,65000))),1000).get(0).value());
        assertEquals(PidsBoardModel.Eta.ARRIVED,PidsBoardModel.rows(board(List.of(arrival("Alpha",1000,10000))),2000).get(0).eta());
    }
    @Test void missingPredictionRemainsAnExplicitServiceRowAndExpiredTrainDisappears() {
        var row=PidsBoardModel.rows(board(List.of(arrival("Alpha",1000,1500))),2000).get(0);
        assertEquals(PidsBoardModel.Eta.MISSING,row.eta());assertEquals(service.platformName(),row.platform());assertEquals("Local",row.route());
    }
    @Test void differentDestinationsAtOnePlatformStaySeparate() {
        assertEquals(2,PidsBoardModel.rows(board(List.of(arrival("Alpha",4000,5000),arrival("Charlie",6000,7000))),1000).size());
    }
    @Test void columnsNeverOverlapAtSmallOrLargeWindowWidths() {
        for(int width:new int[]{150,180,206,264,324,600}) {
            var c=PidsBoardModel.columns(width);
            assertTrue(c.destinationWidth()>0);assertTrue(c.lineWidth()>0);
            assertTrue(c.destination()+c.destinationWidth()<c.line());
            assertTrue(c.line()+c.lineWidth()<c.platform());
            assertTrue(c.platform()+c.platformWidth()<c.eta());assertEquals(width,c.eta()+c.etaWidth());
        }
    }
    @Test void invalidConfigFallsBackToTheBritishDefault() { assertEquals(PidsStyle.BRITISH,PidsStyle.parse("invalid"));assertEquals(PidsStyle.EUROPEAN,PidsStyle.parse("european")); }
}
