package com.evalkit.framework.common.utils.nlp;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NLPUtils {

    private NLPUtils() {
    }

    /* 计算余弦相似度 */
    public static double cosineSimilarity(String text1, String text2) throws Exception {
        Directory dir = new RAMDirectory();
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        IndexWriter writer = new IndexWriter(dir, config);
        // 添加文档
        addDoc(writer, text1, "1");
        addDoc(writer, text2, "2");
        writer.close();
        IndexReader reader = DirectoryReader.open(dir);
        Terms terms1 = reader.getTermVector(0, "content");
        Terms terms2 = reader.getTermVector(1, "content");

        Map<String, Integer> freq1 = getTermFrequencies(terms1);
        Map<String, Integer> freq2 = getTermFrequencies(terms2);

        Set<String> allTerms = new HashSet<>();
        allTerms.addAll(freq1.keySet());
        allTerms.addAll(freq2.keySet());

        double dotProduct = 0, norm1 = 0, norm2 = 0;

        for (String term : allTerms) {
            int f1 = freq1.getOrDefault(term, 0);
            int f2 = freq2.getOrDefault(term, 0);
            dotProduct += f1 * f2;
            norm1 += f1 * f1;
            norm2 += f2 * f2;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private static void addDoc(IndexWriter writer, String content, String id) throws Exception {
        Document doc = new Document();
        FieldType type = new FieldType();
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        type.setStoreTermVectors(true);
        type.setTokenized(true);
        doc.add(new Field("content", content, type));
        doc.add(new StringField("id", id, Field.Store.YES));
        writer.addDocument(doc);
    }

    private static Map<String, Integer> getTermFrequencies(Terms terms) throws Exception {
        Map<String, Integer> termFreqMap = new HashMap<>();
        if (terms != null) {
            TermsEnum termsEnum = terms.iterator();
            BytesRef term;
            while ((term = termsEnum.next()) != null) {
                String termText = term.utf8ToString();
                int freq = (int) termsEnum.totalTermFreq();
                termFreqMap.put(termText, freq);
            }
        }
        return termFreqMap;
    }
}
