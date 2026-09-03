package com.campusnav.api;

import com.campusnav.model.LocationType;
import com.campusnav.service.CampusService;
import com.campusnav.auth.AdminAuthService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 65_536;
    private final CampusService service; private final HttpServer server; private final ExecutorService executor; private final String allowedOrigin; private final boolean writesEnabled;private final AdminAuthService auth;

    public ApiServer(CampusService service, InetSocketAddress address, String allowedOrigin) throws IOException {
        this(service,address,allowedOrigin,true,AdminAuthService.trustedForTests());
    }
    public ApiServer(CampusService service, InetSocketAddress address, String allowedOrigin, boolean writesEnabled) throws IOException {
        this(service,address,allowedOrigin,writesEnabled,AdminAuthService.trustedForTests());
    }
    public ApiServer(CampusService service,InetSocketAddress address,String allowedOrigin,boolean writesEnabled,AdminAuthService auth)throws IOException{
        this.service=service; this.allowedOrigin=allowedOrigin; this.writesEnabled=writesEnabled;this.auth=auth; this.server=HttpServer.create(address,0);
        this.executor=Executors.newFixedThreadPool(Math.max(2,Math.min(8,Runtime.getRuntime().availableProcessors())));
        server.setExecutor(executor); server.createContext("/api",this::handle); server.createContext("/",this::handleStatic);
    }
    public void start(){server.start();}
    public int port(){return server.getAddress().getPort();}
    @Override public void close(){server.stop(0);executor.shutdownNow();}

    private void handle(HttpExchange exchange) throws IOException {
        String requestId=UUID.randomUUID().toString();
        try {
            addCommonHeaders(exchange,requestId);
            if("OPTIONS".equals(exchange.getRequestMethod())){handleOptions(exchange);return;}
            if(!originAllowed(exchange)){send(exchange,403,Json.error("ORIGIN_NOT_ALLOWED","The request origin is not permitted.",requestId));return;}
            route(exchange,requestId);
        } catch(BodyTooLargeException e){send(exchange,413,Json.error("REQUEST_TOO_LARGE",e.getMessage(),requestId));}
        catch(SecurityException e){send(exchange,401,Json.error("AUTH_REQUIRED",e.getMessage(),requestId));}
        catch(IllegalArgumentException e){int status=statusFor(e.getMessage());send(exchange,status,Json.error(codeFor(status),e.getMessage(),requestId));}
        catch(Exception e){send(exchange,500,Json.error("INTERNAL_ERROR","The server could not complete the request.",requestId));}
        finally{exchange.close();}
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String requestPath = exchange.getRequestURI().getPath();
            String relative = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
            Path root = Path.of("frontend").toAbsolutePath().normalize();
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] content = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(file));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            addSecurityHeaders(exchange.getResponseHeaders());
            exchange.sendResponseHeaders(200, "HEAD".equals(exchange.getRequestMethod()) ? -1 : content.length);
            if (!"HEAD".equals(exchange.getRequestMethod())) exchange.getResponseBody().write(content);
        } finally {
            exchange.close();
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private void route(HttpExchange x,String id)throws IOException {
        String method=x.getRequestMethod(),path=x.getRequestURI().getPath();Map<String,String> q=query(x.getRequestURI().getRawQuery());
        if(path.equals("/api/health")){requireMethod(method,"GET");try{service.verifyStorageAvailable();send(x,200,"{\"status\":\"ok\",\"service\":\"CampusNav\",\"phase\":5,\"writesEnabled\":"+writesEnabled+",\"authenticationRequired\":"+auth.required()+"}");}catch(IllegalStateException exception){send(x,503,Json.error("STORAGE_UNAVAILABLE","Database connection is unavailable.",id));}return;}
        if(path.equals("/api/auth/session")){requireMethod(method,"GET");boolean logged=auth.authenticated(x);send(x,200,"{\"authenticated\":"+logged+(logged?",\"csrfToken\":"+Json.quote(auth.csrf(x)):"")+"}");return;}
        if(path.equals("/api/auth/login")){requireMethod(method,"POST");requireJson(x);Map<String,Object>b=body(x);var login=auth.login(text(b,"username"),text(b,"password"),x.getRemoteAddress().getAddress().getHostAddress());boolean secure="https".equalsIgnoreCase(x.getRequestHeaders().getFirst("X-Forwarded-Proto"));x.getResponseHeaders().add("Set-Cookie","campusnav_session="+login.token()+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800"+(secure?"; Secure":""));send(x,200,"{\"authenticated\":true,\"csrfToken\":"+Json.quote(login.csrf())+"}");return;}
        if(path.equals("/api/auth/logout")){requireMethod(method,"POST");auth.requireMutation(x);auth.logout(x);x.getResponseHeaders().add("Set-Cookie","campusnav_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");send(x,204,"");return;}
        if(path.equals("/api/admin/analytics")){requireMethod(method,"GET");requireWrites();if(!auth.authenticated(x))throw new SecurityException("Administrator login is required.");send(x,200,Json.analytics(service.usageAnalytics()));return;}
        if(path.equals("/api/locations")){
            if(method.equals("GET")){send(x,200,Json.locations(q.containsKey("name")?service.findLocationsByName(q.get("name")):service.locations()));return;}
            if(method.equals("POST")){requireAdmin(x);requireJson(x);Map<String,Object>b=body(x);var value=service.addLocation(text(b,"id"),text(b,"name"),LocationType.parse(text(b,"type")),text(b,"description"),optionalDouble(b,"latitude"),optionalDouble(b,"longitude"));service.recordAdminMutation();send(x,201,Json.location(value));return;}
            throw new MethodException();
        }
        if(path.startsWith("/api/locations/")){String locationId=decode(path.substring("/api/locations/".length()));if(method.equals("GET")){var value=service.findLocationById(locationId).orElseThrow(()->new IllegalArgumentException("Location does not exist: "+locationId));send(x,200,Json.location(value));return;}requireAdmin(x);if(method.equals("PUT")){requireJson(x);Map<String,Object>b=body(x);var value=service.updateLocation(locationId,text(b,"name"),LocationType.parse(text(b,"type")),text(b,"description"),optionalDouble(b,"latitude"),optionalDouble(b,"longitude"));service.recordAdminMutation();send(x,200,Json.location(value));return;}if(method.equals("DELETE")){service.deleteLocation(locationId);service.recordAdminMutation();send(x,204,"");return;}throw new MethodException();}
        if(path.equals("/api/routes")){
            if(method.equals("GET")){send(x,200,Json.routes(service.routes()));return;}
            if(method.equals("POST")){requireAdmin(x);requireJson(x);Map<String,Object>b=body(x);var value=service.addRoute(text(b,"sourceId"),text(b,"destinationId"),integer(b,"distanceMetres"));service.recordAdminMutation();send(x,201,Json.route(value));return;}
            if(method.equals("PUT")){requireAdmin(x);requireJson(x);Map<String,Object>b=body(x);var value=service.updateRoute(text(b,"sourceId"),text(b,"destinationId"),integer(b,"distanceMetres"));service.recordAdminMutation();send(x,200,Json.route(value));return;}
            if(method.equals("DELETE")){requireAdmin(x);service.deleteRoute(required(q,"source"),required(q,"destination"));service.recordAdminMutation();send(x,204,"");return;}
            throw new MethodException();
        }
        if(path.equals("/api/routes/reachable")){requireMethod(method,"GET");send(x,200,Json.path(service.findAnyRoute(required(q,"source"),required(q,"destination")),"BFS"));return;}
        if(path.equals("/api/routes/shortest")){requireMethod(method,"GET");send(x,200,Json.path(service.findShortestRoute(required(q,"source"),required(q,"destination")),"DIJKSTRA"));return;}
        send(x,404,Json.error("NOT_FOUND","API endpoint not found.",id));
    }

    private Map<String,Object> body(HttpExchange x)throws IOException{return Json.parseObject(readBody(x));}
    private String readBody(HttpExchange x)throws IOException{
        try(var in=x.getRequestBody();var out=new ByteArrayOutputStream()){byte[]buf=new byte[4096];int total=0,n;while((n=in.read(buf))!=-1){total+=n;if(total>MAX_BODY_BYTES)throw new BodyTooLargeException();out.write(buf,0,n);}return out.toString(StandardCharsets.UTF_8);}
    }
    private void requireJson(HttpExchange x){String type=x.getRequestHeaders().getFirst("Content-Type");if(type==null||!type.toLowerCase().startsWith("application/json"))throw new IllegalArgumentException("Content-Type must be application/json.");}
    private static String text(Map<String,Object>b,String key){Object v=b.get(key);if(!(v instanceof String s))throw new IllegalArgumentException("JSON field '"+key+"' must be a string.");return s;}
    private static int integer(Map<String,Object>b,String key){Object v=b.get(key);if(!(v instanceof Integer i))throw new IllegalArgumentException("JSON field '"+key+"' must be an integer.");return i;}
    private static Double optionalDouble(Map<String,Object>b,String key){Object v=b.get(key);if(v==null)return null;if(v instanceof Number n)return n.doubleValue();throw new IllegalArgumentException("JSON field '"+key+"' must be a number.");}
    private static String required(Map<String,String>q,String key){String v=q.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Query parameter '"+key+"' is required.");return v;}
    private static Map<String,String> query(String raw){Map<String,String>r=new LinkedHashMap<>();if(raw==null||raw.isBlank())return r;for(String pair:raw.split("&")){String[]p=pair.split("=",2);r.put(decode(p[0]),decode(p.length>1?p[1]:""));}return r;}
    private static String decode(String v){return URLDecoder.decode(v,StandardCharsets.UTF_8);}
    private void requireMethod(String actual,String expected){if(!actual.equals(expected))throw new MethodException();}
    private void requireWrites(){if(!writesEnabled)throw new ForbiddenException();}
    private void requireAdmin(HttpExchange exchange){requireWrites();auth.requireMutation(exchange);}
    private boolean originAllowed(HttpExchange x){String origin=x.getRequestHeaders().getFirst("Origin");return origin==null||origin.equals(allowedOrigin)||sameOrigin(x,origin);}
    private void handleOptions(HttpExchange x)throws IOException{if(!originAllowed(x)){send(x,403,"{}");return;}Headers h=x.getResponseHeaders();h.set("Access-Control-Allow-Methods","GET, POST, PUT, DELETE, OPTIONS");h.set("Access-Control-Allow-Headers","Content-Type, X-CSRF-Token");h.set("Access-Control-Allow-Credentials","true");h.set("Access-Control-Max-Age","600");send(x,204,"");}
    private void addCommonHeaders(HttpExchange x,String id){Headers h=x.getResponseHeaders();String origin=x.getRequestHeaders().getFirst("Origin");h.set("Content-Type","application/json; charset=utf-8");h.set("Cache-Control","no-store");h.set("X-Content-Type-Options","nosniff");h.set("X-Request-Id",id);h.set("Access-Control-Allow-Origin",origin!=null&&sameOrigin(x,origin)?origin:allowedOrigin);h.set("Vary","Origin");addSecurityHeaders(h);}
    private static void addSecurityHeaders(Headers headers){headers.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");headers.set("Referrer-Policy","no-referrer");headers.set("Permissions-Policy","camera=(), microphone=(), geolocation=()");headers.set("X-Frame-Options","DENY");}
    private static boolean sameOrigin(HttpExchange exchange,String origin){try{var uri=java.net.URI.create(origin);String host=exchange.getRequestHeaders().getFirst("Host");return host!=null&&host.equalsIgnoreCase(uri.getAuthority());}catch(IllegalArgumentException exception){return false;}}
    private static void send(HttpExchange x,int status,String json)throws IOException{byte[]bytes=json.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(status,status==204?-1:bytes.length);if(status!=204)x.getResponseBody().write(bytes);}
    private static int statusFor(String m){if(m==null)return 422;if(m.contains("Too many failed")||m.contains("Invalid administrator"))return 401;if(m.contains("Public deployment is read-only"))return 403;if(m.contains("HTTP method is not allowed"))return 405;if(m.contains("already exists")||m.contains("route already exists")||m.contains("A route already exists"))return 409;if(m.contains("does not exist")||m.contains("Unknown location"))return 404;if(m.startsWith("Invalid JSON")||m.contains("Content-Type")||m.startsWith("JSON field")||m.startsWith("Query parameter"))return 400;return 422;}
    private static String codeFor(int s){return switch(s){case 400->"BAD_REQUEST";case 403->"READ_ONLY";case 404->"NOT_FOUND";case 405->"METHOD_NOT_ALLOWED";case 409->"CONFLICT";default->"VALIDATION_ERROR";};}
    private static final class MethodException extends IllegalArgumentException{
        private static final long serialVersionUID=1L;
        MethodException(){super("HTTP method is not allowed for this endpoint.");}
    }
    private static final class BodyTooLargeException extends IOException{
        private static final long serialVersionUID=1L;
        BodyTooLargeException(){super("Request body exceeds 65536 bytes.");}
    }
    private static final class ForbiddenException extends IllegalArgumentException{
        private static final long serialVersionUID=1L;
        ForbiddenException(){super("Public deployment is read-only. Run locally to manage campus data.");}
    }
}
