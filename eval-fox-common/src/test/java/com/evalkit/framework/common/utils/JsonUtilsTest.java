package com.evalkit.framework.common.utils;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilsTest {

    static class Book {
        private String title;
        private int price;

        public Book() {
        }

        public Book(String title, int price) {
            this.title = title;
            this.price = price;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getPrice() {
            return price;
        }

        public void setPrice(int price) {
            this.price = price;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Book)) return false;
            Book book = (Book) o;
            return price == book.price && Objects.equals(title, book.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, price);
        }
    }

    @Test
    void testToJsonAndFromJson() {
        Book book = new Book("Java", 100);
        String json = JsonUtils.toJson(book);
        assertTrue(json.contains("Java"));
        Book result = JsonUtils.fromJson(json, Book.class);
        assertEquals(book, result);
    }

    @Test
    void testFromJsonToList() {
        List<Book> books = Arrays.asList(new Book("A", 10), new Book("B", 20));
        String json = JsonUtils.toJson(books);
        List<Book> result = JsonUtils.fromJsonToList(json, Book.class);
        assertEquals(books, result);
    }

    @Test
    void testFromJsonToMap() {
        Map<String, Book> map = new HashMap<>();
        map.put("k1", new Book("X", 1));
        String json = JsonUtils.toJson(map);
        Map<String, Book> result = JsonUtils.fromJsonToMap(json, String.class, Book.class);
        assertEquals(map, result);
    }

    @Test
    void testFromJsonTypeReference() {
        List<Book> books = Arrays.asList(new Book("M", 30), new Book("N", 40));
        String json = JsonUtils.toJson(books);
        List<Book> result = JsonUtils.fromJson(json, new TypeReference<List<Book>>() {
        });
        assertEquals(books, result);
    }

    @Test
    void testWriteAndReadJsonFile() throws IOException {
        Book book = new Book("File", 888);
        File tempFile = File.createTempFile("book", ".json");
        JsonUtils.writeJsonFile(tempFile.getAbsolutePath(), book);
        Book result = JsonUtils.readJsonFile(tempFile, Book.class);
        assertEquals(book, result);
        tempFile.delete();
    }

    @Test
    void testReadJsonFileWithTypeReference() throws IOException {
        List<Book> books = Arrays.asList(new Book("A", 1), new Book("B", 2));
        File tempFile = File.createTempFile("books", ".json");
        JsonUtils.writeJsonFile(tempFile.getAbsolutePath(), books);
        List<Book> result = JsonUtils.readJsonFile(tempFile, new TypeReference<List<Book>>() {
        });
        assertEquals(books, result);
        tempFile.delete();
    }

    @Test
    void testReadJsonStream() throws IOException {
        Book book = new Book("Stream", 123);
        String json = JsonUtils.toJson(book);
        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        Book result = JsonUtils.readJsonStream(inputStream, new TypeReference<Book>() {
        });
        assertEquals(book, result);
    }

    @Test
    void testFromJsonWithJsonPath() {
        String json = "{ \"store\": { \"book\": [ {\"title\": \"Book A\", \"price\": 10}, {\"title\": \"Book B\", \"price\": 20} ] } }";
        // 提取第一个书籍对象
        Book book = JsonUtils.fromJson(json, "$.store.book[0]", Book.class);
        assertEquals("Book A", book.getTitle());
        assertEquals(10, book.getPrice());

        // 提取书名字段
        String title = JsonUtils.fromJson(json, "$.store.book[1].title", String.class);
        assertEquals("Book B", title);

        // 全量解析
        Map result = JsonUtils.fromJson(json, "", Map.class);
        assertTrue(result.containsKey("store"));
    }
}
