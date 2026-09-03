package com.campusnav;

import com.campusnav.app.SampleDataLoader;
import com.campusnav.model.LocationType;
import com.campusnav.model.PathResult;
import com.campusnav.service.CampusService;
import com.campusnav.ui.ConsoleMenu;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class CampusNavTestSuite {
    private int passed; private int failed;
    public static void main(String[] args) { new CampusNavTestSuite().run(); }

    private void run() {
        test("add and normalize location",()->{var s=empty();var l=s.addLocation(" lib ","Library",LocationType.LIBRARY,"Study");eq("LIB",l.id());eq(1,s.locations().size());});
        test("duplicate ID rejected",()->{var s=empty();add(s,"A1");expectFailure(()->add(s,"a1"),"already exists");eq(1,s.locations().size());});
        test("blank name rejected",()->expectFailure(()->empty().addLocation("A1"," ",LocationType.OTHER,"x"),"cannot be blank"));
        test("invalid ID rejected",()->expectFailure(()->add(empty(),"!"),"must be 2-20"));
        test("ID lookup uses canonical form",()->{var s=empty();add(s,"AB");check(s.findLocationById(" ab ").isPresent(),"not found");});
        test("missing ID returns empty",()->check(empty().findLocationById("AB").isEmpty(),"unexpected record"));
        test("partial name search ignores case",()->{var s=empty();s.addLocation("AA","Central Library",LocationType.LIBRARY,"x");eq(1,s.findLocationsByName("LIBr").size());});
        test("name search returns all matches",()->{var s=empty();s.addLocation("AA","North Lab",LocationType.LABORATORY,"x");s.addLocation("BB","South Lab",LocationType.LABORATORY,"x");eq(2,s.findLocationsByName("lab").size());});
        test("route between valid locations",()->{var s=two();s.addRoute("AA","BB",10);eq(1,s.routes().size());});
        test("route missing source rejected",()->{var s=two();expectFailure(()->s.addRoute("XX","BB",10),"Source location does not exist");});
        test("route missing destination rejected",()->{var s=two();expectFailure(()->s.addRoute("AA","XX",10),"Destination location does not exist");});
        test("zero distance rejected",()->{var s=two();expectFailure(()->s.addRoute("AA","BB",0),"greater than zero");});
        test("negative distance rejected",()->{var s=two();expectFailure(()->s.addRoute("AA","BB",-1),"greater than zero");});
        test("self route rejected",()->{var s=two();expectFailure(()->s.addRoute("AA","AA",5),"must be different");});
        test("duplicate reverse route rejected",()->{var s=two();s.addRoute("AA","BB",5);expectFailure(()->s.addRoute("BB","AA",9),"already exists");eq(1,s.routes().size());});
        test("BFS direct path",()->{var s=two();s.addRoute("AA","BB",9);eq(List.of("AA","BB"),ids(s.findAnyRoute("AA","BB")));});
        test("BFS handles cycle",()->{var s=three();s.addRoute("AA","BB",1);s.addRoute("BB","CC",1);s.addRoute("CC","AA",1);check(s.findAnyRoute("AA","CC").found(),"route not found");});
        test("BFS disconnected result",()->{var s=two();check(!s.findAnyRoute("AA","BB").found(),"unexpected route");});
        test("same endpoint path",()->{var s=two();var p=s.findShortestRoute("AA","AA");eq(List.of("AA"),ids(p));eq(0,p.totalDistanceMetres());});
        test("Dijkstra chooses cheaper indirect path",()->{var s=three();s.addRoute("AA","CC",20);s.addRoute("AA","BB",5);s.addRoute("BB","CC",7);var p=s.findShortestRoute("AA","CC");eq(List.of("AA","BB","CC"),ids(p));eq(12,p.totalDistanceMetres());});
        test("Dijkstra disconnected result",()->{var s=two();check(!s.findShortestRoute("AA","BB").found(),"unexpected route");});
        test("Dijkstra works in reverse",()->{var s=three();s.addRoute("AA","BB",5);s.addRoute("BB","CC",7);var p=s.findShortestRoute("CC","AA");eq(12,p.totalDistanceMetres());eq(List.of("CC","BB","AA"),ids(p));});
        test("representative shortest route is 440m",()->{var s=empty();SampleDataLoader.load(s);var p=s.findShortestRoute("GATE-A","CSE");eq(440,p.totalDistanceMetres());eq(List.of("GATE-A","ADMIN","LIB","CSE"),ids(p));});
        test("sample disconnected archive",()->{var s=empty();SampleDataLoader.load(s);check(!s.findShortestRoute("GATE-A","ARCHIVE").found(),"archive should be disconnected");});
        test("expanded sample campus",()->{var s=empty();SampleDataLoader.load(s);eq(16,s.locations().size());eq(24,s.routes().size());check(s.findLocationById("HRD").isPresent(),"HRD missing");check(s.findLocationById("GIRLS-HOSTEL").isPresent(),"girls hostel missing");});
        test("alternative routes are distinct and ordered",()->{var s=empty();SampleDataLoader.load(s);var paths=s.findAlternativeRoutes("GATE-A","HEALTH",3);eq(3,paths.size());check(paths.get(0).totalDistanceMetres()<=paths.get(1).totalDistanceMetres(),"alternatives not ordered");check(!ids(paths.get(0)).equals(ids(paths.get(1))),"routes are not distinct");});
        test("console rejects invalid choice and recovers",()->{String output=console("99\n0\n");check(output.contains("Invalid choice"),"missing invalid-choice message");check(output.contains("closed safely"),"did not recover");});
        test("console rejects malformed distance",()->{String output=console("5\nGATE-A\nADMIN\nabc\n0\n");check(output.contains("valid whole number"),"missing numeric error");});
        test("console displays verified shortest path",()->{String output=console("8\nGATE-A\nCSE\n0\n");check(output.contains("Dijkstra shortest route"),"missing algorithm label");check(output.contains("Total distance: 440 m"),"wrong total");});
        test("console displays no-route message",()->{String output=console("7\nGATE-A\nARCHIVE\n0\n");check(output.contains("No route exists"),"missing no-route message");});
        System.out.printf("%nCampusNav tests: %d passed, %d failed%n",passed,failed);
        if(failed>0) throw new AssertionError("Test suite failed");
    }

    private CampusService empty(){return new CampusService();}
    private CampusService two(){var s=empty();add(s,"AA");add(s,"BB");return s;}
    private CampusService three(){var s=two();add(s,"CC");return s;}
    private void add(CampusService s,String id){s.addLocation(id,"Location "+id,LocationType.OTHER,"Test location");}
    private List<String> ids(PathResult p){return p.locations().stream().map(x->x.id()).toList();}
    private String console(String input){var s=empty();SampleDataLoader.load(s);var bytes=new ByteArrayOutputStream();new ConsoleMenu(s,new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),new PrintStream(bytes,true,StandardCharsets.UTF_8)).run();return bytes.toString(StandardCharsets.UTF_8);}
    private void test(String name,Checked action){try{action.run();passed++;System.out.println("PASS  "+name);}catch(Throwable e){failed++;System.out.println("FAIL  "+name+" -> "+e.getMessage());}}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void eq(Object expected,Object actual){if(!Objects.equals(expected,actual))throw new AssertionError("expected="+expected+", actual="+actual);}
    private void expectFailure(Checked action,String text){try{action.run();throw new AssertionError("expected failure containing: "+text);}catch(IllegalArgumentException e){check(e.getMessage().contains(text),"unexpected message: "+e.getMessage());}catch(Exception e){throw new AssertionError(e);}}
    @FunctionalInterface private interface Checked{void run() throws Exception;}
}
