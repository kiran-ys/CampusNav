package com.campusnav.graph;

import com.campusnav.model.Edge;
import com.campusnav.model.Route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public final class CampusGraph {
    private final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();

    public void addVertex(String locationId) {
        adjacency.putIfAbsent(locationId, new ArrayList<>());
    }

    public boolean containsVertex(String id) { return adjacency.containsKey(id); }

    public void addBidirectionalRoute(String source, String destination, int distance) {
        requireVertex(source); requireVertex(destination);
        if (source.equals(destination)) throw new IllegalArgumentException("Source and destination must be different.");
        if (distance <= 0) throw new IllegalArgumentException("Distance must be greater than zero metres.");
        if (hasEdge(source, destination)) throw new IllegalArgumentException("A route already exists between " + source + " and " + destination + ".");
        adjacency.get(source).add(new Edge(destination, distance));
        adjacency.get(destination).add(new Edge(source, distance));
    }

    public List<Route> routes() {
        List<Route> routes = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (var entry : adjacency.entrySet()) {
            for (Edge edge : entry.getValue()) {
                String a = entry.getKey(); String b = edge.destinationId();
                String key = a.compareTo(b) < 0 ? a + "\u0000" + b : b + "\u0000" + a;
                if (seen.add(key)) routes.add(new Route(a, b, edge.distanceMetres()));
            }
        }
        return List.copyOf(routes);
    }

    public List<String> breadthFirstPath(String source, String destination) {
        requireVertex(source); requireVertex(destination);
        if (source.equals(destination)) return List.of(source);
        Queue<String> queue = new ArrayDeque<>(); Set<String> visited = new HashSet<>(); Map<String,String> parent = new HashMap<>();
        queue.add(source); visited.add(source);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            for (Edge edge : adjacency.get(current)) {
                String next = edge.destinationId();
                if (visited.add(next)) {
                    parent.put(next, current);
                    if (next.equals(destination)) return reconstruct(parent, source, destination);
                    queue.add(next);
                }
            }
        }
        return List.of();
    }

    public WeightedPath dijkstra(String source, String destination) {
        requireVertex(source); requireVertex(destination);
        if (source.equals(destination)) return new WeightedPath(List.of(source), 0);
        Map<String,Integer> distance = new HashMap<>(); Map<String,String> parent = new HashMap<>();
        for (String id : adjacency.keySet()) distance.put(id, Integer.MAX_VALUE);
        distance.put(source, 0);
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingInt(NodeDistance::distance));
        queue.add(new NodeDistance(source, 0));
        while (!queue.isEmpty()) {
            NodeDistance item = queue.remove();
            if (item.distance() != distance.get(item.id())) continue;
            if (item.id().equals(destination)) break;
            for (Edge edge : adjacency.get(item.id())) {
                long candidateLong = (long) item.distance() + edge.distanceMetres();
                if (candidateLong > Integer.MAX_VALUE) continue;
                int candidate = (int) candidateLong;
                if (candidate < distance.get(edge.destinationId())) {
                    distance.put(edge.destinationId(), candidate); parent.put(edge.destinationId(), item.id());
                    queue.add(new NodeDistance(edge.destinationId(), candidate));
                }
            }
        }
        if (distance.get(destination) == Integer.MAX_VALUE) return WeightedPath.notFound();
        return new WeightedPath(reconstruct(parent, source, destination), distance.get(destination));
    }

    public List<WeightedPath> shortestSimplePaths(String source,String destination,int limit){
        requireVertex(source);requireVertex(destination);if(limit<1||limit>5)throw new IllegalArgumentException("Alternative route limit must be between 1 and 5.");
        PriorityQueue<PathCandidate> queue=new PriorityQueue<>(Comparator.comparingInt(PathCandidate::distance));
        queue.add(new PathCandidate(List.of(source),0));List<WeightedPath> results=new ArrayList<>();int examined=0;
        while(!queue.isEmpty()&&results.size()<limit&&examined++<20_000){PathCandidate current=queue.remove();String last=current.ids().get(current.ids().size()-1);if(last.equals(destination)){results.add(new WeightedPath(current.ids(),current.distance()));continue;}for(Edge edge:adjacency.get(last)){if(current.ids().contains(edge.destinationId()))continue;long next=(long)current.distance()+edge.distanceMetres();if(next>Integer.MAX_VALUE)continue;List<String> ids=new ArrayList<>(current.ids());ids.add(edge.destinationId());queue.add(new PathCandidate(List.copyOf(ids),(int)next));}}
        return List.copyOf(results);
    }

    public int distanceBetween(String source, String destination) {
        return adjacency.getOrDefault(source, List.of()).stream()
                .filter(e -> e.destinationId().equals(destination)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No direct route between " + source + " and " + destination + "."))
                .distanceMetres();
    }

    private boolean hasEdge(String source, String destination) {
        return adjacency.get(source).stream().anyMatch(e -> e.destinationId().equals(destination));
    }
    private void requireVertex(String id) {
        if (!adjacency.containsKey(id)) throw new IllegalArgumentException("Unknown location ID: " + id);
    }
    private static List<String> reconstruct(Map<String,String> parent, String source, String destination) {
        ArrayDeque<String> path = new ArrayDeque<>(); String current = destination; path.addFirst(current);
        while (!current.equals(source)) { current = parent.get(current); if (current == null) return List.of(); path.addFirst(current); }
        return List.copyOf(path);
    }
    private record NodeDistance(String id, int distance) { }
    private record PathCandidate(List<String> ids,int distance) { }
    public record WeightedPath(List<String> ids, int totalDistanceMetres) {
        public WeightedPath { ids = List.copyOf(ids); }
        public static WeightedPath notFound() { return new WeightedPath(List.of(), 0); }
        public boolean found() { return !ids.isEmpty(); }
    }
}
