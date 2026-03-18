package com.evalkit.framework.eval.scripts;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用 Jena 构建酒旅知识图谱 - V2 版本
 */
public class TravelKGBuilderV2 {

    // 定义 RDF 命名空间
    private static final String TRAVEL_NS = "http://travel.evalkit.com/";

    // 创建全局 Model
    private static final Model model = ModelFactory.createDefaultModel();

    // 定义资源属性
    private static Property hotelName;
    private static Property locatedIn;
    private static Property rating;
    private static Property starLevel;
    private static Property roomName;
    private static Property roomType;
    private static Property price;
    private static Property attractionName;
    private static Property ticketPrice;
    private static Property transportNo;
    private static Property transportType;
    private static Property departure;
    private static Property destination;
    private static Property hasRoom;

    // 定义资源类型
    private static Resource cityClass;
    private static Resource hotelClass;
    private static Resource roomClass;
    private static Resource attractionClass;
    private static Resource transportClass;

    public static void main(String[] args) {
        System.out.println("开始使用 Jena 构建酒旅知识图谱 (V2)...");

        // 初始化 RDF 资源和属性
        initializeRDFElements();

        // CSV 文件路径
        String csvBaseDir = "evalkit-eval/src/test/resources/travel_demo/csv";

        // 1. 加载城市数据
        System.out.println("正在加载城市数据...");
        List<City> cities = loadCities(csvBaseDir + "/cities.csv");
        addCitiesToModel(cities);

        // 2. 加载酒店数据
        System.out.println("正在加载酒店数据...");
        List<Hotel> hotels = loadHotels(csvBaseDir + "/hotels.csv");
        addHotelsToModel(hotels);

        // 3. 加载房型数据
        System.out.println("正在加载房型数据...");
        List<Room> rooms = loadRooms(csvBaseDir + "/rooms.csv");
        addRoomsToModel(rooms);

        // 4. 加载景点数据
        System.out.println("正在加载景点数据...");
        List<Attraction> attractions = loadAttractions(csvBaseDir + "/attractions.csv");
        addAttractionsToModel(attractions);

        // 5. 加载交通数据
        System.out.println("正在加载交通数据...");
        List<Transport> transports = loadTransports(csvBaseDir + "/transports.csv");
        addTransportsToModel(transports);

        // 6. 写入 RDF/Turtle 文件
        String outputFilePath = "evalkit_travel_kg_v2.ttl";
        writeModelToFile(outputFilePath);
        System.out.println("图谱构建完成！已输出到: " + outputFilePath);
        System.out.println("图谱统计信息 - 资源数: " + model.size() + " 三元组");
    }

    /**
     * 初始化 RDF 元素（资源和属性）
     */
    private static void initializeRDFElements() {
        // 设置命名空间前缀
        model.setNsPrefix("travel", TRAVEL_NS);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("xsd", XSD.getURI());

        // 定义属性
        hotelName = model.createProperty(TRAVEL_NS, "hotelName");
        locatedIn = model.createProperty(TRAVEL_NS, "locatedIn");
        rating = model.createProperty(TRAVEL_NS, "rating");
        starLevel = model.createProperty(TRAVEL_NS, "starLevel");
        roomName = model.createProperty(TRAVEL_NS, "roomName");
        roomType = model.createProperty(TRAVEL_NS, "roomType");
        price = model.createProperty(TRAVEL_NS, "price");
        attractionName = model.createProperty(TRAVEL_NS, "attractionName");
        ticketPrice = model.createProperty(TRAVEL_NS, "ticketPrice");
        transportNo = model.createProperty(TRAVEL_NS, "transportNo");
        transportType = model.createProperty(TRAVEL_NS, "transportType");
        departure = model.createProperty(TRAVEL_NS, "departure");
        destination = model.createProperty(TRAVEL_NS, "destination");
        hasRoom = model.createProperty(TRAVEL_NS, "hasRoom");

        // 定义类型（资源）
        cityClass = model.createResource(TRAVEL_NS + "City");
        hotelClass = model.createResource(TRAVEL_NS + "Hotel");
        roomClass = model.createResource(TRAVEL_NS + "Room");
        attractionClass = model.createResource(TRAVEL_NS + "Attraction");
        transportClass = model.createResource(TRAVEL_NS + "Transport");
    }

    /**
     * 将城市数据添加到模型
     */
    private static void addCitiesToModel(List<City> cities) {
        for (City city : cities) {
            Resource cityResource = model.createResource(TRAVEL_NS + "City_" + city.cityId);
            cityResource.addProperty(RDF.type, cityClass)
                    .addProperty(RDFS.label, city.cityName);
        }
    }

    /**
     * 将酒店数据添加到模型
     */
    private static void addHotelsToModel(List<Hotel> hotels) {
        for (Hotel hotel : hotels) {
            Resource hotelResource = model.createResource(TRAVEL_NS + "Hotel_" + hotel.hotelId);
            Resource cityResource = model.createResource(TRAVEL_NS + "City_" + hotel.cityId);

            hotelResource.addProperty(RDF.type, hotelClass)
                    .addProperty(hotelName, hotel.hotelName)
                    .addProperty(locatedIn, cityResource)
                    .addProperty(rating, model.createTypedLiteral(hotel.rating))
                    .addProperty(starLevel, model.createTypedLiteral(hotel.starLevel));
        }
    }

    /**
     * 将房型数据添加到模型
     */
    private static void addRoomsToModel(List<Room> rooms) {
        for (Room room : rooms) {
            Resource roomResource = model.createResource(TRAVEL_NS + "Room_" + room.roomId);
            Resource hotelResource = model.createResource(TRAVEL_NS + "Hotel_" + room.hotelId);

            roomResource.addProperty(RDF.type, roomClass)
                    .addProperty(roomName, room.roomName)
                    .addProperty(roomType, room.roomType)
                    .addProperty(price, model.createTypedLiteral(room.price));

            // 建立 酒店 -> 房型 的关联
            hotelResource.addProperty(hasRoom, roomResource);
        }
    }

    /**
     * 将景点数据添加到模型
     */
    private static void addAttractionsToModel(List<Attraction> attractions) {
        for (Attraction attr : attractions) {
            Resource attrResource = model.createResource(TRAVEL_NS + "Attr_" + attr.attrId);
            Resource cityResource = model.createResource(TRAVEL_NS + "City_" + attr.cityId);

            attrResource.addProperty(RDF.type, attractionClass)
                    .addProperty(attractionName, attr.attrName)
                    .addProperty(locatedIn, cityResource)
                    .addProperty(ticketPrice, model.createTypedLiteral(attr.ticketPrice));
        }
    }

    /**
     * 将交通数据添加到模型
     */
    private static void addTransportsToModel(List<Transport> transports) {
        for (Transport trans : transports) {
            Resource transResource = model.createResource(TRAVEL_NS + "Trans_" + trans.transportNo);
            Resource depCityResource = model.createResource(TRAVEL_NS + "City_" + trans.depCityId);
            Resource destCityResource = model.createResource(TRAVEL_NS + "City_" + trans.destCityId);

            transResource.addProperty(RDF.type, transportClass)
                    .addProperty(transportNo, trans.transportNo)
                    .addProperty(transportType, trans.transportType)
                    .addProperty(departure, depCityResource)
                    .addProperty(destination, destCityResource)
                    .addProperty(price, model.createTypedLiteral(trans.price));
        }
    }

    /**
     * 将 RDF 模型写入文件
     */
    private static void writeModelToFile(String filePath) {
        try {
            model.write(Files.newOutputStream(Paths.get(filePath)), "TURTLE");
        } catch (IOException e) {
            System.err.println("写入 RDF 文件失败: " + e.getMessage());
        }
    }

    // ==========================================
    // 数据模型 (严格对应 CSV 字段)
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
    // CSV 数据读取方法
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
}

