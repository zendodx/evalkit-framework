package com.evalkit.framework.eval.scripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

        // CSV 文件路径（相对于项目根目录）
        String csvBaseDir = "evalkit-eval/src/test/resources/travel_demo/csv";

        // 1. 转换城市数据 (cities.csv)
        System.out.println("正在加载城市数据...");
        ttl.append("# --- 城市节点 ---\n");
        for (City city : loadCities(csvBaseDir + "/cities.csv")) {
            ttl.append(String.format("travel:City_%s a travel:City ; rdfs:label \"%s\" .\n", city.cityId, city.cityName));
        }
        ttl.append("\n");

        // 2. 转换酒店数据 (hotels.csv)
        System.out.println("正在加载酒店数据...");
        ttl.append("# --- 酒店节点 ---\n");
        for (Hotel hotel : loadHotels(csvBaseDir + "/hotels.csv")) {
            ttl.append(String.format("travel:Hotel_%s a travel:Hotel ;\n", hotel.hotelId))
                    .append(String.format("    travel:hotelName \"%s\" ;\n", hotel.hotelName))
                    .append(String.format("    travel:locatedIn travel:City_%s ;\n", hotel.cityId))
                    .append(String.format("    travel:rating \"%s\"^^xsd:float ;\n", hotel.rating))
                    .append(String.format("    travel:starLevel \"%d\"^^xsd:integer .\n", hotel.starLevel));
        }
        ttl.append("\n");

        // 3. 转换房型数据 (rooms.csv)
        System.out.println("正在加载房型数据...");
        ttl.append("# --- 房型节点 ---\n");
        for (Room room : loadRooms(csvBaseDir + "/rooms.csv")) {
            ttl.append(String.format("travel:Room_%s a travel:Room ;\n", room.roomId))
                    .append(String.format("    travel:roomName \"%s\" ;\n", room.roomName))
                    .append(String.format("    travel:roomType \"%s\" ;\n", room.roomType))
                    .append(String.format("    travel:price \"%d\"^^xsd:integer .\n", room.price));
            // 建立 酒店 -> 房型 的关联
            ttl.append(String.format("travel:Hotel_%s travel:hasRoom travel:Room_%s .\n", room.hotelId, room.roomId));
        }
        ttl.append("\n");

        // 4. 转换景点数据 (attractions.csv)
        System.out.println("正在加载景点数据...");
        ttl.append("# --- 景点节点 ---\n");
        for (Attraction attr : loadAttractions(csvBaseDir + "/attractions.csv")) {
            ttl.append(String.format("travel:Attr_%s a travel:Attraction ;\n", attr.attrId))
                    .append(String.format("    travel:attractionName \"%s\" ;\n", attr.attrName))
                    .append(String.format("    travel:locatedIn travel:City_%s ;\n", attr.cityId))
                    .append(String.format("    travel:ticketPrice \"%d\"^^xsd:integer .\n", attr.ticketPrice));
        }
        ttl.append("\n");

        // 5. 转换交通数据 (transports.csv)
        System.out.println("正在加载交通数据...");
        ttl.append("# --- 交通节点 ---\n");
        for (Transport trans : loadTransports(csvBaseDir + "/transports.csv")) {
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
        String hotelId, hotelName, cityId, cityName;
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
    // 从 CSV 文件读取数据
    // ==========================================
    private static List<City> loadCities(String filePath) {
        List<City> cities = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    cities.add(new City(parts[0].trim(), parts[1].trim()));
                }
            }
        } catch (IOException e) {
            System.err.println("读取城市数据失败: " + e.getMessage());
        }
        return cities;
    }

    private static List<Hotel> loadHotels(String filePath) {
        List<Hotel> hotels = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    hotels.add(new Hotel(
                            parts[0].trim(),                           // hotel_id
                            parts[1].trim(),                           // hotel_name
                            parts[2].trim(),                           // city_id
                            Float.parseFloat(parts[5].trim()),        // rating
                            Integer.parseInt(parts[4].trim())         // star_level
                    ));
                }
            }
        } catch (IOException e) {
            System.err.println("读取酒店数据失败: " + e.getMessage());
        }
        return hotels;
    }

    private static List<Room> loadRooms(String filePath) {
        List<Room> rooms = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    rooms.add(new Room(
                            parts[0].trim(),                           // room_id
                            parts[2].trim(),                           // hotel_id
                            parts[1].trim(),                           // room_name
                            parts[3].trim(),                           // room_type
                            Integer.parseInt(parts[4].trim())         // price
                    ));
                }
            }
        } catch (IOException e) {
            System.err.println("读取房型数据失败: " + e.getMessage());
        }
        return rooms;
    }

    private static List<Attraction> loadAttractions(String filePath) {
        List<Attraction> attractions = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    attractions.add(new Attraction(
                            parts[0].trim(),                           // attr_id
                            parts[1].trim(),                           // attr_name
                            parts[2].trim(),                           // city_id
                            Integer.parseInt(parts[4].trim())         // ticket_price
                    ));
                }
            }
        } catch (IOException e) {
            System.err.println("读取景点数据失败: " + e.getMessage());
        }
        return attractions;
    }

    private static List<Transport> loadTransports(String filePath) {
        List<Transport> transports = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 7) {
                    transports.add(new Transport(
                            parts[0].trim(),                           // transport_no
                            parts[1].trim(),                           // transport_type
                            parts[2].trim(),                           // dep_city_id
                            parts[4].trim(),                           // dest_city_id
                            Integer.parseInt(parts[6].trim())         // price
                    ));
                }
            }
        } catch (IOException e) {
            System.err.println("读取交通数据失败: " + e.getMessage());
        }
        return transports;
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
