package io.github.teamcreating.mtrmap.integration.howtogo;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import org.junit.jupiter.api.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HowToGoBridgeTest {
    @Test void absentModIsOptional() throws Exception {
        assertTrue(HowToGoBridge.find(new URLClassLoader(new java.net.URL[0],null)).isEmpty());
    }
    @Test void invalidOrOutOfRangePositionsAreUnavailable() {
        assertFalse(HowToGoBridge.supports(null));
        for (double x : new double[]{Double.NaN,Double.POSITIVE_INFINITY,2147483648d,-2147483649d})
            assertFalse(HowToGoBridge.supports(new Station("id","Station",new Point(x,0,0),List.of())));
    }
    @Test void publishedHowToGoDeclaresThePublicDestinationAndNavigationEntryPoints() throws Exception {
        var path=Path.of(System.getProperty("mtrmap.howtogoJar", ".cache/howtogo-0.2.1-neoforge.jar"));
        Assumptions.assumeTrue(Files.isRegularFile(path),"Optional actual HowToGo artifact not downloaded");
        // javap reads the genuine class file without fabricating Minecraft classes or initializing the mod.
        var suffix=System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")?".exe":"";
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","javap"+suffix).toString(),
                "-classpath",path.toString(),"bili.dongsz.howtogo.route.Destination","bili.dongsz.howtogo.client.Navigation")
                .redirectErrorStream(true).start();
        var signatures=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor(),signatures);
        assertTrue(signatures.contains("public bili.dongsz.howtogo.route.Destination(java.lang.String, int, int, int, java.lang.String);"));
        assertTrue(signatures.contains("public static void setTarget(bili.dongsz.howtogo.route.Destination);"));
    }
}
