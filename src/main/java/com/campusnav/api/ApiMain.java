package com.campusnav.api;

import com.campusnav.app.ServiceFactory;
import com.campusnav.service.CampusService;
import com.campusnav.auth.AdminAuthService;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

public final class ApiMain {
    private ApiMain() { }
    public static void main(String[] args) throws Exception {
        String configuredPort=System.getenv().getOrDefault("CAMPUSNAV_PORT",System.getenv().getOrDefault("PORT","8080"));
        int port=parsePort(configuredPort);
        String host=System.getenv().getOrDefault("CAMPUSNAV_HOST","127.0.0.1");
        String origin=System.getenv().getOrDefault("CAMPUSNAV_CORS_ORIGIN","http://localhost:3000");
        CampusService service=ServiceFactory.createFromEnvironment();
        boolean writesEnabled=!"false".equalsIgnoreCase(System.getenv().getOrDefault("CAMPUSNAV_WRITES_ENABLED","true"));
        String adminUser=System.getenv().getOrDefault("CAMPUSNAV_ADMIN_USERNAME","admin");
        String adminPassword=System.getenv("CAMPUSNAV_ADMIN_PASSWORD");
        if(writesEnabled&&(adminPassword==null||adminPassword.isBlank()))throw new IllegalStateException("CAMPUSNAV_ADMIN_PASSWORD is required when online editing is enabled.");
        AdminAuthService auth=writesEnabled?new AdminAuthService(adminUser,adminPassword):AdminAuthService.trustedForTests();
        ApiServer server=new ApiServer(service,new InetSocketAddress(host,port),origin,writesEnabled,auth);server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close,"campusnav-api-shutdown"));
        System.out.printf("CampusNav API running at http://%s:%d%n",host,server.port());
        new CountDownLatch(1).await();
    }
    static int parsePort(String value){try{int p=Integer.parseInt(value);if(p<0||p>65535)throw new NumberFormatException();return p;}catch(NumberFormatException e){throw new IllegalArgumentException("CAMPUSNAV_PORT must be between 0 and 65535.");}}
}
