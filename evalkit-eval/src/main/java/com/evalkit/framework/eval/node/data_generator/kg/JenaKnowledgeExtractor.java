package com.evalkit.framework.eval.node.data_generator.kg;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jena 知识提取器
 */
public class JenaKnowledgeExtractor {
    private final Model model;

    public JenaKnowledgeExtractor(String ttlData) {
        // 初始化内存图谱并加载 TTL 数据
        this.model = ModelFactory.createDefaultModel();
        this.model.read(new StringReader(ttlData), null, "TTL");
    }

    /**
     * 执行 SPARQL 查询，返回符合条件的实体属性映射列表
     */
    public List<Map<String, String>> extractData(String sparql) {
        List<Map<String, String>> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(sparql, model)) {
            ResultSet resultSet = qexec.execSelect();
            List<String> resultVars = resultSet.getResultVars();

            while (resultSet.hasNext()) {
                QuerySolution soln = resultSet.nextSolution();
                Map<String, String> row = new HashMap<>();
                for (String var : resultVars) {
                    if (soln.contains(var)) {
                        row.put(var, soln.get(var).toString());
                    }
                }
                results.add(row);
            }
        }
        return results;
    }
}
