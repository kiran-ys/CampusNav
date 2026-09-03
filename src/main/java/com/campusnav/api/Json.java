package com.campusnav.api;

import com.campusnav.model.Location;
import com.campusnav.model.PathResult;
import com.campusnav.model.PathSegment;
import com.campusnav.model.Route;
import com.campusnav.model.UsageAnalytics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() { }

    static String location(Location value) {
        return "{" + field("id", value.id()) + "," + field("name", value.name()) + ","
                + field("type", value.type().name()) + "," + field("typeDisplay", value.type().displayName()) + ","
                + field("description", value.description()) + ",\"latitude\":" + number(value.latitude()) + ",\"longitude\":" + number(value.longitude()) + "}";
    }

    static String locations(List<Location> values) {
        return values.stream().map(Json::location).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    static String route(Route value) {
        return "{" + field("sourceId", value.sourceId()) + "," + field("destinationId", value.destinationId())
                + ",\"distanceMetres\":" + value.distanceMetres() + "}";
    }

    static String routes(List<Route> values) {
        return values.stream().map(Json::route).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    static String path(PathResult result, String algorithm) {
        if (!result.found()) {
            return "{\"found\":false," + field("algorithm", algorithm) + ",\"locations\":[],\"segments\":[],\"totalDistanceMetres\":0}";
        }
        String locations = result.locations().stream().map(Json::location)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String segments = result.segments().stream().map(Json::segment)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return "{\"found\":true," + field("algorithm", algorithm) + ",\"locations\":" + locations
                + ",\"segments\":" + segments + ",\"totalDistanceMetres\":" + result.totalDistanceMetres() + "}";
    }
    static String alternativePaths(List<PathResult> results){return results.stream().map(r->path(r,"ALTERNATIVE")).collect(java.util.stream.Collectors.joining(",","{\"algorithm\":\"K_SHORTEST_SIMPLE_PATHS\",\"routes\":[","]}"));}

    private static String segment(PathSegment value) {
        return "{" + field("sourceId", value.source().id()) + "," + field("sourceName", value.source().name()) + ","
                + field("destinationId", value.destination().id()) + "," + field("destinationName", value.destination().name())
                + ",\"distanceMetres\":" + value.distanceMetres() + "}";
    }

    static String error(String code, String message, String requestId) {
        return "{\"error\":{" + field("code", code) + "," + field("message", message) + "," + field("requestId", requestId) + "}}";
    }
    static String analytics(UsageAnalytics value){String daily=value.dailyUsage().stream().map(d->"{"+field("date",d.date())+",\"routeQueries\":"+d.routeQueries()+"}").collect(java.util.stream.Collectors.joining(",","[","]"));return "{\"totalRouteQueries\":"+value.totalRouteQueries()+",\"successfulRouteQueries\":"+value.successfulRouteQueries()+",\"bfsQueries\":"+value.bfsQueries()+",\"dijkstraQueries\":"+value.dijkstraQueries()+",\"adminMutations\":"+value.adminMutations()+",\"dailyUsage\":"+daily+"}";}
    private static String number(Double value){return value==null?"null":Double.toString(value);}

    static String field(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    static String quote(String value) {
        if (value == null) return "null";
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\"' -> result.append("\\\""); case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b"); case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n"); case '\r' -> result.append("\\r"); case '\t' -> result.append("\\t");
                default -> { if (c < 0x20) result.append(String.format("\\u%04x", (int)c)); else result.append(c); }
            }
        }
        return result.append('\"').toString();
    }

    static Map<String, Object> parseObject(String source) {
        return new Parser(source).parseObject();
    }

    private static final class Parser {
        private final String source; private int index;
        Parser(String source) { this.source = source == null ? "" : source; }
        Map<String,Object> parseObject() {
            skip(); expect('{'); Map<String,Object> result = new LinkedHashMap<>(); skip();
            if (peek('}')) { index++; finish(); return result; }
            while (true) {
                String key = string(); skip(); expect(':'); skip(); Object value = value(); result.put(key, value); skip();
                if (peek('}')) { index++; finish(); return result; } expect(','); skip();
            }
        }
        private Object value() {
            if (peek('\"')) return string();
            if(source.startsWith("null",index)){index+=4;return null;}
            int start=index; if(peek('-'))index++; while(index<source.length()&&Character.isDigit(source.charAt(index)))index++;boolean decimal=false;if(peek('.')){decimal=true;index++;while(index<source.length()&&Character.isDigit(source.charAt(index)))index++;}
            if(start==index||(start+1==index&&source.charAt(start)=='-'))throw bad("Expected a string or number");
            try{if(decimal)return Double.parseDouble(source.substring(start,index));return Integer.parseInt(source.substring(start,index));}catch(NumberFormatException e){throw bad("Number is out of range");}
        }
        private String string() {
            skip(); expect('\"'); StringBuilder result=new StringBuilder();
            while(index<source.length()) { char c=source.charAt(index++); if(c=='\"')return result.toString();
                if(c=='\\') { if(index>=source.length())throw bad("Incomplete escape"); char e=source.charAt(index++);
                    switch(e){case '\"','\\','/'->result.append(e);case 'b'->result.append('\b');case 'f'->result.append('\f');case 'n'->result.append('\n');case 'r'->result.append('\r');case 't'->result.append('\t');default->throw bad("Unsupported escape");}
                } else { if(c<0x20)throw bad("Control character in string");result.append(c); }
            } throw bad("Unterminated string");
        }
        private void finish(){skip();if(index!=source.length())throw bad("Unexpected trailing content");}
        private void skip(){while(index<source.length()&&Character.isWhitespace(source.charAt(index)))index++;}
        private boolean peek(char c){return index<source.length()&&source.charAt(index)==c;}
        private void expect(char c){if(!peek(c))throw bad("Expected '"+c+"'");index++;}
        private IllegalArgumentException bad(String m){return new IllegalArgumentException("Invalid JSON at position "+index+": "+m+".");}
    }
}
