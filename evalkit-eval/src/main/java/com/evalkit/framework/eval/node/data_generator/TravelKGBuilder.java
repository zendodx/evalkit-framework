package com.evalkit.framework.eval.node.data_generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 创建酒旅知识图谱脚本
 */
public class TravelKGBuilder {
    // 统一定义通用的图谱前缀
    private static final String PREFIXES =
            "@prefix travel: <http://travel.evalkit.com/> .\n" +
                    "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
                    "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n";

    public static void main(String[] args) {
        System.out.println("开始构建通用酒旅知识图谱...");
        StringBuilder ttl = new StringBuilder(PREFIXES);

        // 1. 转换城市数据 (cities.csv)
        ttl.append("# --- 城市节点 ---\n");
        for (City city : getMockCities()) {
            ttl.append(String.format("travel:City_%s a travel:City ; rdfs:label \"%s\" .\n", city.cityId, city.cityName));
        }
        ttl.append("\n");

        // 2. 转换酒店数据 (hotels.csv)
        ttl.append("# --- 酒店节点 ---\n");
        for (Hotel hotel : getMockHotels()) {
            ttl.append(String.format("travel:Hotel_%s a travel:Hotel ;\n", hotel.hotelId))
                    .append(String.format("    travel:hotelName \"%s\" ;\n", hotel.hotelName))
                    .append(String.format("    travel:locatedIn travel:City_%s ;\n", hotel.cityId))
                    .append(String.format("    travel:rating \"%s\"^^xsd:float ;\n", hotel.rating))
                    .append(String.format("    travel:starLevel \"%d\"^^xsd:integer .\n", hotel.starLevel));
        }
        ttl.append("\n");

        // 3. 转换房型数据 (rooms.csv)
        ttl.append("# --- 房型节点 ---\n");
        for (Room room : getMockRooms()) {
            ttl.append(String.format("travel:Room_%s a travel:Room ;\n", room.roomId))
                    .append(String.format("    travel:roomName \"%s\" ;\n", room.roomName))
                    .append(String.format("    travel:roomType \"%s\" ;\n", room.roomType))
                    .append(String.format("    travel:price \"%d\"^^xsd:integer .\n", room.price));
            // 建立 酒店 -> 房型 的关联
            ttl.append(String.format("travel:Hotel_%s travel:hasRoom travel:Room_%s .\n", room.hotelId, room.roomId));
        }
        ttl.append("\n");

        // 4. 转换景点数据 (attractions.csv)
        ttl.append("# --- 景点节点 ---\n");
        for (Attraction attr : getMockAttractions()) {
            ttl.append(String.format("travel:Attr_%s a travel:Attraction ;\n", attr.attrId))
                    .append(String.format("    travel:attractionName \"%s\" ;\n", attr.attrName))
                    .append(String.format("    travel:locatedIn travel:City_%s ;\n", attr.cityId))
                    .append(String.format("    travel:ticketPrice \"%d\"^^xsd:integer .\n", attr.ticketPrice));
        }
        ttl.append("\n");

        // 5. 转换交通数据 (transports.csv)
        ttl.append("# --- 交通节点 ---\n");
        for (Transport trans : getMockTransports()) {
            ttl.append(String.format("travel:Trans_%s a travel:Transport ;\n", trans.transportNo))
                    .append(String.format("    travel:transportNo \"%s\" ;\n", trans.transportNo))
                    .append(String.format("    travel:transportType \"%s\" ;\n", trans.transportType))
                    .append(String.format("    travel:departure travel:City_%s ;\n", trans.depCityId))
                    .append(String.format("    travel:destination travel:City_%s ;\n", trans.destCityId))
                    .append(String.format("    travel:price \"%d\"^^xsd:integer .\n", trans.price));
        }

        // 6. 写入文件
        String outputFilePath = "evalkit_travel_kg.ttl";
        writeToFile(outputFilePath, ttl.toString());
        System.out.println("图谱构建完成！已输出到: " + outputFilePath);
    }

    // ==========================================
    // 内部数据模型 (严格对应 CSV 字段)
    // ==========================================
    static class City {
        String cityId, cityName;

        City(String id, String name) {
            this.cityId = id;
            this.cityName = name;
        }
    }

    static class Hotel {
        String hotelId, hotelName, cityId;
        float rating;
        int starLevel;

        Hotel(String hId, String hName, String cId, float r, int s) {
            hotelId = hId;
            hotelName = hName;
            cityId = cId;
            rating = r;
            starLevel = s;
        }
    }

    static class Room {
        String roomId, hotelId, roomName, roomType;
        int price;

        Room(String rId, String hId, String rName, String rType, int p) {
            roomId = rId;
            hotelId = hId;
            roomName = rName;
            roomType = rType;
            price = p;
        }
    }

    static class Attraction {
        String attrId, attrName, cityId;
        int ticketPrice;

        Attraction(String aId, String aName, String cId, int p) {
            attrId = aId;
            attrName = aName;
            cityId = cId;
            ticketPrice = p;
        }
    }

    static class Transport {
        String transportNo, transportType, depCityId, destCityId;
        int price;

        Transport(String tNo, String tType, String dep, String dest, int p) {
            transportNo = tNo;
            transportType = tType;
            depCityId = dep;
            destCityId = dest;
            price = p;
        }
    }

    // ==========================================
    // 模拟读取 CSV 数据
    // ==========================================
    private static List<City> getMockCities() {
        return Arrays.asList(
                new City("C001", "成都"), new City("C002", "上海"), new City("C003", "三亚")
        );
    }

    private static List<Hotel> getMockHotels() {
        return Arrays.asList(
                new Hotel("H1001", "熊猫主题客栈", "C001", 4.9f, 4),
                new Hotel("H1002", "亚特兰蒂斯酒店", "C003", 4.8f, 5)
        );
    }

    private static List<Room> getMockRooms() {
        return Arrays.asList(
                new Room("R5001", "H1001", "竹林亲子套房", "家庭房", 580),
                new Room("R5002", "H1002", "海底世界亲子套房", "家庭房", 5888)
        );
    }

    private static List<Attraction> getMockAttractions() {
        return Arrays.asList(
                new Attraction("A2001", "大熊猫繁育研究基地", "C001", 55),
                new Attraction("A2002", "蜈支洲岛", "C003", 144)
        );
    }

    private static List<Transport> getMockTransports() {
        return Arrays.asList(
                new Transport("G321", "高铁", "C002", "C001", 860),
                new Transport("CA1234", "飞机", "C001", "C003", 1250)
        );
    }

    private static void writeToFile(String filePath, String content) {
        Path path = Paths.get(filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (IOException e) {
            System.err.println("写入文件失败: " + e.getMessage());
        }
    }
}
