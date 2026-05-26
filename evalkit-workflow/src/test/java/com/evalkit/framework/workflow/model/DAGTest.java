package com.evalkit.framework.workflow.model;

import com.evalkit.framework.workflow.exception.DAGException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAG 数据结构单元测试
 */
class DAGTest {

    private DAG dag;
    private WorkflowNode nodeA;
    private WorkflowNode nodeB;
    private WorkflowNode nodeC;

    @BeforeEach
    void setUp() {
        dag = new DAG();
        nodeA = new WorkflowNode("nodeA") {
            @Override
            protected void doExecute() {}
        };
        nodeB = new WorkflowNode("nodeB") {
            @Override
            protected void doExecute() {}
        };
        nodeC = new WorkflowNode("nodeC") {
            @Override
            protected void doExecute() {}
        };
    }

    // ─────────────── addTask / containsTask ──────────────────────────

    @Test
    void addTask_nodeIsStored() {
        dag.addTask(nodeA);
        assertTrue(dag.containsTask(nodeA));
    }

    @Test
    void addTask_doesNotContainUnaddedNode() {
        dag.addTask(nodeA);
        assertFalse(dag.containsTask(nodeB));
    }

    @Test
    void addTask_emptyInOutEdges() {
        dag.addTask(nodeA);
        assertTrue(dag.getInEdges("nodeA").isEmpty());
        assertTrue(dag.getOutEdges("nodeA").isEmpty());
    }

    // ─────────────── addEdge ─────────────────────────────────────────

    @Test
    void addEdge_setsInAndOutEdges() {
        dag.addTask(nodeA);
        dag.addTask(nodeB);
        dag.addEdge("nodeA", "nodeB");

        Set<String> outA = dag.getOutEdges("nodeA");
        Set<String> inB = dag.getInEdges("nodeB");

        assertTrue(outA.contains("nodeB"));
        assertTrue(inB.contains("nodeA"));
    }

    @Test
    void addEdge_missingFromNode_throwsDAGException() {
        dag.addTask(nodeB);
        assertThrows(DAGException.class, () -> dag.addEdge("notExist", "nodeB"));
    }

    @Test
    void addEdge_missingToNode_throwsDAGException() {
        dag.addTask(nodeA);
        assertThrows(DAGException.class, () -> dag.addEdge("nodeA", "notExist"));
    }

    // ─────────────── hasEdge ─────────────────────────────────────────

    @Test
    void hasEdge_existingEdge_returnsTrue() {
        dag.addTask(nodeA);
        dag.addTask(nodeB);
        dag.addEdge("nodeA", "nodeB");
        assertTrue(dag.hasEdge("nodeA", "nodeB"));
    }

    @Test
    void hasEdge_nonExistingEdge_returnsFalse() {
        dag.addTask(nodeA);
        dag.addTask(nodeB);
        assertFalse(dag.hasEdge("nodeA", "nodeB"));
    }

    // ─────────────── getAllTasks ──────────────────────────────────────

    @Test
    void getAllTasks_returnsAllAddedNodes() {
        dag.addTask(nodeA);
        dag.addTask(nodeB);
        dag.addTask(nodeC);
        Collection<WorkflowNode> all = dag.getAllTasks();
        assertEquals(3, all.size());
    }

    // ─────────────── getTask ─────────────────────────────────────────

    @Test
    void getTask_returnsCorrectNode() {
        dag.addTask(nodeA);
        assertEquals(nodeA, dag.getTask("nodeA"));
    }

    @Test
    void getTask_notAdded_returnsNull() {
        assertNull(dag.getTask("notExist"));
    }

    // ─────────────── clone ───────────────────────────────────────────

    @Test
    void clone_isDeepCopy() {
        dag.addTask(nodeA);
        dag.addTask(nodeB);
        dag.addEdge("nodeA", "nodeB");

        DAG cloned = dag.clone();

        assertNotSame(dag, cloned);
        assertNotSame(dag.getTasks(), cloned.getTasks());

        // 修改克隆不影响原始
        WorkflowNode nodeD = new WorkflowNode("nodeD") {
            @Override
            protected void doExecute() {}
        };
        cloned.addTask(nodeD);
        assertFalse(dag.containsTask(nodeD));
    }

    @Test
    void clone_preservesEdges() {
        dag.addTask(nodeA);
        dag.addTask(nodeB);
        dag.addEdge("nodeA", "nodeB");

        DAG cloned = dag.clone();
        assertTrue(cloned.hasEdge("nodeA", "nodeB"));
        assertTrue(cloned.getInEdges("nodeB").contains("nodeA"));
    }
}

