package com.campusnav.app;

import com.campusnav.service.CampusService;
import com.campusnav.ui.ConsoleMenu;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        CampusService service = ServiceFactory.createFromEnvironment();
        new ConsoleMenu(service, System.in, System.out).run();
    }
}
