package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.thread.OrderedBatchRunner;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.api.config.ApiCompletionConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 有序API调用,适用于同组数据按顺序执行,例如:相同CaseId的Query要用同一线程处理,并且需要保证执行顺序
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class OrderedApiCompletion extends ApiCompletion {

    /**
     * 同组 DataItem 索引缓存。key = orderKey，value = 按 comparator 排序后的同组列表。
     * 整批数据只建一次（batchInvoke 入口），后续所有轮次的查询均为 O(1) Map 查找。
     */
    private volatile Map<String, List<DataItem>> groupIndexCache;

    public OrderedApiCompletion() {
    }

    public OrderedApiCompletion(ApiCompletionConfig config) {
        super(config);
    }

    /**
     * 获取key,用于顺序执行
     *
     * @param dataItem 单条输入数据
     * @return 顺序执行key
     */
    public abstract String prepareOrderKey(DataItem dataItem);

    /**
     * 获取比较器
     * @return DataItem比较器
     */
    public abstract Comparator<DataItem> prepareComparator();

    /**
     * 批量调用
     *
     * @param dataItems 输入数据集合
     * @return 调用结果集合
     */
    @Override
    protected List<ApiCompletionResult> batchInvoke(List<DataItem> dataItems) {
        // 批量执行开始前预建分组索引，invoke 内部查询全部走 O(1) Map 查找
        buildGroupIndexIfAbsent(dataItems);
        return OrderedBatchRunner.runOrderedBatch(dataItems, this::invokeAndSetResult, this::prepareOrderKey,
                (o1, o2) -> prepareComparator().compare(o1, o2), config.getThreadNum(), size -> size * config.getBatchTimeoutSec());
    }

    /**
     * 单条调用并立即将结果回填到 dataItem。
     * <p>
     * 普通 {@link ApiCompletion#doExecute()} 是在整批 batchInvoke 完成后统一回填，
     * 但 {@link OrderedApiCompletion} 需要在同组第 N 条执行时，能通过
     * {@link #getPrevDataItems} 拿到第 1~N-1 条已完成的 apiCompletionResult。
     * 因此必须在单条 invoke 完成后立即回填，而不是等整批结束。
     *
     * @param dataItem 当前数据项
     * @return 调用结果
     */
    private ApiCompletionResult invokeAndSetResult(DataItem dataItem) {
        ApiCompletionResult result = invokeWrapper(dataItem);
        // 立即回填到 dataItem，确保后续同组轮次的 getPrevDataItems 能拿到完整结果
        if (result != null && dataItem.getApiCompletionResult() == null) {
            dataItem.setApiCompletionResult(result);
        }
        return result;
    }

    // ===================================================================
    // 多轮对话上下文访问工具方法
    //   同一 orderKey 的数据在同一线程串行执行，当前轮调用时前序轮已执行完毕，
    //   因此以下方法均可安全读取"同组已完成"的 DataItem。
    // ===================================================================

    /**
     * 获取与当前数据同组（相同 orderKey）的所有 DataItem，
     * 并按 {@link #prepareComparator()} 定义的顺序排列。
     * 底层使用预建索引，O(1) 查找。
     *
     * @param current 当前正在处理的 DataItem
     * @return 同组全部 DataItem 的有序列表（含当前条）
     */
    protected List<DataItem> getGroupDataItems(DataItem current) {
        String key = prepareOrderKey(current);
        List<DataItem> group = getGroupIndex().get(key);
        return group != null ? group : Collections.emptyList();
    }

    /**
     * 获取当前数据在同组中的上一条 DataItem（已执行完毕，含 apiCompletionResult）。
     * 若当前是第一条则返回 null。
     *
     * @param current 当前正在处理的 DataItem
     * @return 上一条 DataItem，不存在时返回 null
     */
    protected DataItem getPrevDataItem(DataItem current) {
        List<DataItem> group = getGroupDataItems(current);
        int idx = group.indexOf(current);
        if (idx <= 0) return null;
        return group.get(idx - 1);
    }

    /**
     * 获取当前数据在同组中、排在它之前的所有 DataItem（已执行完毕）。
     * 若当前是第一条则返回空列表。
     *
     * @param current 当前正在处理的 DataItem
     * @return 排在当前条之前的所有 DataItem（有序，不含当前条）
     */
    protected List<DataItem> getPrevDataItems(DataItem current) {
        List<DataItem> group = getGroupDataItems(current);
        int idx = group.indexOf(current);
        if (idx <= 0) return Collections.emptyList();
        return group.subList(0, idx);
    }

    /**
     * 获取当前数据同组中指定轮次（1-based）的单条 DataItem。
     * 例如 getGroupDataItemAt(current, 1) 返回同组第 1 轮。越界时返回 null。
     *
     * @param current 当前正在处理的 DataItem
     * @param index   目标轮次（1-based）
     * @return 对应位置的 DataItem，越界时返回 null
     */
    protected DataItem getGroupDataItemAt(DataItem current, int index) {
        List<DataItem> group = getGroupDataItems(current);
        int i = index - 1;
        if (i < 0 || i >= group.size()) return null;
        return group.get(i);
    }

    /**
     * 获取当前数据同组中指定轮次范围内的 DataItem 子列表（1-based，左闭右闭）。
     * 例如 getGroupDataItemAt(current, 1, 3) 返回第 1、2、3 轮。
     * 范围超出实际长度时自动截断，不抛异常。
     *
     * @param current   当前正在处理的 DataItem
     * @param fromIndex 起始轮次（1-based，含）
     * @param toIndex   结束轮次（1-based，含）
     * @return 指定范围内的 DataItem 列表，范围非法时返回空列表
     */
    protected List<DataItem> getGroupDataItemAt(DataItem current, int fromIndex, int toIndex) {
        List<DataItem> group = getGroupDataItems(current);
        int from = fromIndex - 1;
        int to = Math.min(toIndex, group.size());
        if (from < 0 || from >= group.size() || from >= to) return Collections.emptyList();
        return group.subList(from, to);
    }

    /**
     * 从当前条之前的所有历史轮中，按指定提取函数收集值，按顺序返回列表。
     * 自动跳过 null 值。
     *
     * @param current   当前正在处理的 DataItem
     * @param extractor 从历史 DataItem 中提取目标值的函数
     * @param <V>       提取值的类型
     * @return 历史值列表（按轮次顺序，不含当前轮）
     */
    protected <V> List<V> getHistoryValues(DataItem current, Function<DataItem, V> extractor) {
        return getPrevDataItems(current).stream()
                .map(extractor)
                .filter(v -> v != null)
                .collect(Collectors.toList());
    }

    // ===================================================================
    // 内部索引管理
    // ===================================================================

    /**
     * 预建分组索引（双重检查锁，整批只建一次）。
     * key = orderKey，value = 按 comparator 排序的同组 DataItem 列表。
     */
    private void buildGroupIndexIfAbsent(List<DataItem> dataItems) {
        if (groupIndexCache != null) return;
        synchronized (this) {
            if (groupIndexCache != null) return;
            Comparator<DataItem> comparator = prepareComparator();
            Map<String, List<DataItem>> index = new HashMap<>();
            for (DataItem item : dataItems) {
                String key = prepareOrderKey(item);
                index.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }
            if (comparator != null) {
                index.values().forEach(list -> list.sort(comparator));
            }
            groupIndexCache = index;
        }
    }

    /**
     * 获取索引，若不存在则回退到实时扫描（兜底，正常不走此路径）。
     */
    private Map<String, List<DataItem>> getGroupIndex() {
        if (groupIndexCache != null) return groupIndexCache;
        List<DataItem> all = WorkflowContextOps.getDataItems(getWorkflowContext());
        buildGroupIndexIfAbsent(all);
        return groupIndexCache;
    }
}
