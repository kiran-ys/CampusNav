package com.campusnav.model;

import java.util.List;

public record UsageAnalytics(int totalRouteQueries, int successfulRouteQueries, int bfsQueries,
                             int dijkstraQueries, int adminMutations, List<DailyUsage> dailyUsage) {
    public UsageAnalytics { dailyUsage = List.copyOf(dailyUsage); }
}
