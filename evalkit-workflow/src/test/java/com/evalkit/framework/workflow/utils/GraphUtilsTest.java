package com.evalkit.framework.workflow.utils;

import com.evalkit.framework.workflow.exception.DAGException;
import com.evalkit.framework.workflow.model.DAG;
import com.evalkit.framework.workflow.model.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraphUtils 拓扑排序单元测试
 */
class GraphUtilsTest {

    // ─────────────── 辅助方法 ────────────────────────────────────────

    private WorkflowNode node(String id) {
        return new WorkflowNode(id) {
            @Override
            protected void doExecute() {}
        };
    }

    // ─────────────── 单节点 ───────────────────────────────────────────

    @Test
    void singleNode_topologicalSort() {
        DAG dag = new DAG();
        dag.addTask(node("A"));
        List<String> order = GraphUtils.topologicalSort(dag);
        assertEquals(1, order.size());
        assertEquals("A", order.get(0));
    }

    // ─────────────── 线性链 A → B → C ────────────────────────────────

    @Test
    void linearChain_topologicalSort_preservesOrder() {
        DAG dag = new DAG();
        WorkflowNode a = node("A"), b = node("B"), c = node("C");
        dag.addTask(a);
        dag.addTask(b);
        dag.addTask(c);
        dag.addEdge("A", "B");
        dag.addEdge("B", "C");

        List<String> order = GraphUtils.topologicalSort(dag);
        assertEquals(3, order.size());
        assertTrue(order.indexOf("A") < order.indexOf("B"),
                "A must come before B");
        assertTrue(order.indexOf("B") < order.indexOf("C"),
                "B must come before C");
    }

    // ─────────────── 菱形 A→B, A→C, B→D, C→D ─────────────────────────

    @Test
    void diamondShape_topologicalSort_allNodesPresent() {
        DAG dag = new DAG();
        WorkflowNode a = node("A"), b = node("B"), c = node("C"), d = node("D");
        dag.addTask(a); dag.addTask(b); dag.addTask(c); dag.addTask(d);
        dag.addEdge("A", "B");
        dag.addEdge("A", "C");
        dag.addEdge("B", "D");
        dag.addEdge("C", "D");

        List<String> order = GraphUtils.topologicalSort(dag);
        assertEquals(4, order.size());
        assertTrue(order.indexOf("A") < order.indexOf("B"));
        assertTrue(order.indexOf("A") < order.indexOf("C"));
        assertTrue(order.indexOf("B") < order.indexOf("D"));
        assertTrue(order.indexOf("C") < order.indexOf("D"));
    }

    // ─────────────── 无边 DAG ─────────────────────────────────────────

    @Test
    void noEdges_returnsAllNodes() {
        DAG dag = new DAG();
        dag.addTask(node("X"));
        dag.addTask(node("Y"));
        dag.addTask(node("Z"));

        List<String> order = GraphUtils.topologicalSort(dag);
        assertEquals(3, order.size());
        assertTrue(order.contains("X"));
        assertTrue(order.contains("Y"));
        assertTrue(order.contains("Z"));
    }

    // ─────────────── 空 DAG ──────────────────────────────────────────

    @Test
    void emptyDag_returnsEmptyList() {
        DAG dag = new DAG();
        List<String> order = GraphUtils.topologicalSort(dag);
        assertTrue(order.isEmpty());
    }

    // ─────────────── 有环检测 ────────────────────────────────────────

    @Test
    void cycleInDAG_throwsDAGException() {
        DAG dag = new DAG();
        WorkflowNode a = node("A"), b = node("B"), c = node("C");
        dag.addTask(a); dag.addTask(b); dag.addTask(c);
        dag.addEdge("A", "B");
        dag.addEdge("B", "C");
        dag.addEdge("C", "A"); // 形成环

        assertThrows(DAGException.class, () -> GraphUtils.topologicalSort(dag),
                "应当检测到环并抛出 DAGException");
    }

    // ─────────────── 两条独立链 ──────────────────────────────────────

    @Test
    void twoIndependentChains_allNodesPresent() {
        DAG dag = new DAG();
        dag.addTask(node("A1")); dag.addTask(node("A2")); // 链1
        dag.addTask(node("B1")); dag.addTask(node("B2")); // 链2
        dag.addEdge("A1", "A2");
        dag.addEdge("B1", "B2");

        List<String> order = GraphUtils.topologicalSort(dag);
        assertEquals(4, order.size());
        assertTrue(order.indexOf("A1") < order.indexOf("A2"));
        assertTrue(order.indexOf("B1") < order.indexOf("B2"));
    }
}

