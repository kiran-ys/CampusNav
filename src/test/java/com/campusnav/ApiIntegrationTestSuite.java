package com.campusnav;

import com.campusnav.api.ApiServer;
import com.campusnav.app.SampleDataLoader;
import com.campusnav.service.CampusService;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ApiIntegrationTestSuite {
    private int passed,failed;private ApiServer server;private HttpClient client;private String base;
    public static void main(String[]args)throws Exception{new ApiIntegrationTestSuite().run();}
    private void run()throws Exception{
        var service=new CampusService();SampleDataLoader.load(service);server=new ApiServer(service,new InetSocketAddress("127.0.0.1",0),"https://campusnav.test");server.start();base="http://127.0.0.1:"+server.port();client=HttpClient.newHttpClient();
        try{
            test("serves Phase 4 frontend",()->{var r=get("/");eq(200,r.statusCode());check(r.body().contains("CampusNav"),"frontend title missing");check(r.headers().firstValue("Content-Type").orElse("").contains("text/html"),"incorrect HTML content type");});
            test("serves frontend JavaScript",()->{var r=get("/app.js");eq(200,r.statusCode());check(r.body().contains("findRoute"),"frontend application missing");});
            test("missing static file is 404",()->eq(404,get("/missing.css").statusCode()));
            test("health",()->expect(get("/api/health"),200,"\"status\":\"ok\""));
            test("list locations",()->expect(get("/api/locations"),200,"\"id\":\"GATE-A\""));
            test("get location by ID",()->expect(get("/api/locations/lib"),200,"Central Library"));
            test("missing location is 404",()->expect(get("/api/locations/ZZ"),404,"NOT_FOUND"));
            test("search locations",()->expect(get("/api/locations?name=library"),200,"Central Library"));
            test("create location",()->expect(post("/api/locations","{\"id\":\"AUD\",\"name\":\"Auditorium\",\"type\":\"AUDITORIUM\",\"description\":\"Events\"}"),201,"\"id\":\"AUD\""));
            test("duplicate location is 409",()->expect(post("/api/locations","{\"id\":\"AUD\",\"name\":\"Again\",\"type\":\"OTHER\",\"description\":\"x\"}"),409,"CONFLICT"));
            test("malformed JSON is 400",()->expect(post("/api/locations","{"),400,"BAD_REQUEST"));
            test("wrong content type is 400",()->{var r=send(HttpRequest.newBuilder(URI.create(base+"/api/locations")).header("Content-Type","text/plain").POST(HttpRequest.BodyPublishers.ofString("{}")).build());expect(r,400,"Content-Type");});
            test("list routes",()->expect(get("/api/routes"),200,"distanceMetres"));
            test("create route",()->expect(post("/api/routes","{\"sourceId\":\"AUD\",\"destinationId\":\"LIB\",\"distanceMetres\":75}"),201,"\"distanceMetres\":75"));
            test("invalid route is 422",()->expect(post("/api/routes","{\"sourceId\":\"AUD\",\"destinationId\":\"LIB\",\"distanceMetres\":0}"),422,"VALIDATION_ERROR"));
            test("BFS reachable",()->expect(get("/api/routes/reachable?source=GATE-A&destination=CSE"),200,"\"algorithm\":\"BFS\""));
            test("Dijkstra shortest is 440",()->expect(get("/api/routes/shortest?source=GATE-A&destination=CSE"),200,"\"totalDistanceMetres\":440"));
            test("alternative routes",()->{var r=get("/api/routes/alternatives?source=GATE-A&destination=HEALTH&limit=3");expect(r,200,"K_SHORTEST_SIMPLE_PATHS");check(r.body().split("totalDistanceMetres",-1).length-1==3,"expected three alternatives");});
            test("disconnected path",()->expect(get("/api/routes/shortest?source=GATE-A&destination=ARCHIVE"),200,"\"found\":false"));
            test("missing query parameter is 400",()->expect(get("/api/routes/shortest?source=GATE-A"),400,"Query parameter"));
            test("unknown endpoint is 404",()->expect(get("/api/nothing"),404,"API endpoint not found"));
            test("method not allowed",()->{var r=send(HttpRequest.newBuilder(URI.create(base+"/api/health")).POST(HttpRequest.BodyPublishers.noBody()).build());expect(r,405,"METHOD_NOT_ALLOWED");});
            test("allowed CORS origin",()->{var r=send(HttpRequest.newBuilder(URI.create(base+"/api/health")).header("Origin","https://campusnav.test").GET().build());eq("https://campusnav.test",r.headers().firstValue("Access-Control-Allow-Origin").orElse(""));});
            test("blocked CORS origin",()->{var r=send(HttpRequest.newBuilder(URI.create(base+"/api/health")).header("Origin","https://evil.test").GET().build());eq(403,r.statusCode());});
            test("CORS preflight",()->{var r=send(HttpRequest.newBuilder(URI.create(base+"/api/locations")).header("Origin","https://campusnav.test").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build());eq(204,r.statusCode());check(r.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"),"missing preflight methods");});
            test("oversized request is 413",()->{String body="x".repeat(66_000);var r=post("/api/locations",body);expect(r,413,"REQUEST_TOO_LARGE");});
            test("concurrent health requests",()->{var calls=java.util.stream.IntStream.range(0,12).mapToObj(i->client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString())).toArray(CompletableFuture[]::new);CompletableFuture.allOf(calls).join();for(var call:calls)eq(200,((HttpResponse<?>)call.join()).statusCode());});
            test("public mode reports read only",()->withReadOnlyServer(service,(readOnlyBase)->expect(send(HttpRequest.newBuilder(URI.create(readOnlyBase+"/api/health")).GET().build()),200,"\"writesEnabled\":false")));
            test("public mode rejects writes",()->withReadOnlyServer(service,(readOnlyBase)->expect(send(HttpRequest.newBuilder(URI.create(readOnlyBase+"/api/locations")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"id\":\"NEW\",\"name\":\"New\",\"type\":\"OTHER\",\"description\":\"New\"}")).build()),403,"READ_ONLY")));
        }finally{server.close();}
        System.out.printf("%nCampusNav API tests: %d passed, %d failed%n",passed,failed);if(failed>0)throw new AssertionError("API test suite failed");
    }
    private HttpResponse<String>get(String p)throws Exception{return send(HttpRequest.newBuilder(URI.create(base+p)).GET().build());}
    private HttpResponse<String>post(String p,String b)throws Exception{return send(HttpRequest.newBuilder(URI.create(base+p)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(b)).build());}
    private HttpResponse<String>send(HttpRequest r)throws Exception{return client.send(r,HttpResponse.BodyHandlers.ofString());}
    private void withReadOnlyServer(CampusService service,ReadOnlyCheck check)throws Exception{try(var readOnly=new ApiServer(service,new InetSocketAddress("127.0.0.1",0),"https://campusnav.test",false)){readOnly.start();check.run("http://127.0.0.1:"+readOnly.port());}}
    private void expect(HttpResponse<String>r,int status,String body){eq(status,r.statusCode());check(r.body().contains(body),"body missing "+body+": "+r.body());check(r.headers().firstValue("X-Request-Id").isPresent(),"missing request ID");}
    private void test(String n,Checked a){try{a.run();passed++;System.out.println("PASS  "+n);}catch(Throwable e){failed++;System.out.println("FAIL  "+n+" -> "+e.getMessage());}}
    private void check(boolean c,String m){if(!c)throw new AssertionError(m);}private void eq(Object e,Object a){if(!Objects.equals(e,a))throw new AssertionError("expected="+e+", actual="+a);}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    @FunctionalInterface private interface ReadOnlyCheck{void run(String base)throws Exception;}
}
