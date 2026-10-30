package com.evalkit.framework.eval.node.scorer.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 基于"会话(Session)"的Agent评估器配置
 * <p>
 * 适用于Agent协议本身是"先开一个会话/连接,再在会话内下发一轮或多轮任务指令"的场景, 此类协议下,会话创建
 * 往往伴随较重的前置开销(Skill初始化、沙箱环境准备等),若每条评测数据都独立建会话,
 * 会重复付出这部分开销、拖慢整体评测速度,会话数量也会随数据量线性增长。
 * <p>
 * 本配置提供三种由轻到重的会话管理模式,业务方按需二选一(优先级从高到低):
 * <ol>
 *   <li>{@link #existingSessionId} - 直接复用业务方提前准备好的一个会话,完全跳过创建;</li>
 *   <li>{@link #sessionPoolSize} - <b>折中方案</b>,维护一个固定大小的会话池,
 *       {@code threadNum}个并发worker从池中按需获取/创建/归还会话,同一会话同一时刻
 *       只服务一个任务,但不同会话之间互不阻塞、仍可并发执行,在"减少会话总数/前置开销"
 *       与"保留多线程并发"之间取得平衡;</li>
 *   <li>{@link #reuseSession} - 同一Scorer实例下所有评测数据共享同一个会话(懒加载,
 *       首次使用时创建),会话数量最少,但同一时刻只能有一个任务在使用该会话,
 *       多线程下会退化为串行执行(threadNum不再生效)。</li>
 * </ol>
 * 三种模式下会话都不会自动关闭,且同一会话内多轮对话上下文不会隔离
 * (Agent可能看到历史轮次内容),是否合适需业务方结合具体Agent/Skill实现自行判断。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class SessionBasedAgentScorerConfig extends AgentBasedScorerConfig {
    /**
     * 是否在多条评测数据间复用同一个会话，默认false（每条数据独立建会话）。
     * <p>
     * 开启后同一Scorer实例的所有评测数据会共享同一个会话（懒加载，首次使用时创建，
     * 此后一直复用，不会自动关闭）。若指定了 {@link #existingSessionId}，则无需再
     * 显式设置本项，效果等同于开启。
     * <p>
     * 注意：会话本质是一个持续的多轮对话，开启复用后同一会话内的多轮任务之间可能存在
     * 上下文串扰（Agent能看到之前轮次的对话历史），是否合适需业务方结合具体实现自行判断；
     * 同时同一会话同一时刻只能被一个任务使用，框架会通过加锁保证多线程下改为串行执行，
     * 因此开启复用会使该Scorer的并发效果退化为串行，threadNum配置将不再生效。
     * <p>
     * 若既想复用会话省去重复的前置开销、又不想完全牺牲并发度，可改用
     * {@link #sessionPoolSize} 折中方案（设置后本项会被忽略）。
     */
    @Builder.Default
    protected boolean reuseSession = false;

    /**
     * 指定一个已存在的会话ID直接复用，可选。
     * <p>
     * 适用于业务方已提前在该会话中完成前置步骤（如手动/脚本预热、加载好基础上下文等），
     * 评测时直接在此会话上追加多轮对话，完全跳过会话创建调用。
     * 一旦指定了本项，等同于开启 {@link #reuseSession}（无需再单独设置），且所有评测数据
     * 都会复用这一个会话。
     * <p>
     * 优先级高于 {@link #sessionPoolSize}：一旦指定，池化模式不会生效。
     */
    protected String existingSessionId;

    /**
     * 会话池大小，可选，默认0表示不启用池化（沿用 {@link #reuseSession} 的两种极端模式）。
     * <p>
     * 是"每条数据独立建会话"（{@code reuseSession=false}，会话数=数据量，
     * 前置开销重复付出但并发不受限）与"全局共享单会话"（{@code reuseSession=true}，
     * 会话数=1，几乎不付出重复前置开销但被锁退化为串行）之间的折中方案：维护一个大小固定
     * 为本值的会话池，{@code threadNum} 个并发worker从池中按需获取/创建/归还会话，
     * 同一会话在同一时刻仍只会被一个线程使用，但不同会话之间互不阻塞，从而在"减少
     * 会话总数/前置开销"与"保留多线程并发"之间取得平衡。
     * <p>
     * 使用建议：设置为与 {@code threadNum} 相等（如 {@code threadNum=5} 时设为
     * {@code 5}），可在完全保留原有并发度的前提下，把会话总数从"数据条数"降到"线程数"；
     * 也可设置得比 {@code threadNum} 更小以进一步减少会话数量，但会因会话不足导致部分
     * worker需要排队等待空闲会话，相当于引入一定程度的串行化，池越小串行化程度越高
     * （池大小为1时退化为等效于 {@code reuseSession=true} 的完全串行）。
     * <p>
     * 优先级：显式设置 {@link #existingSessionId} 时本项不生效；本项 &gt; 0 时优先于
     * {@link #reuseSession} 生效（即无需再关心 {@code reuseSession} 的取值）。
     * <p>
     * 同 {@link #reuseSession}，池中的会话同样不会自动关闭，且同一会话内的多轮任务
     * 之间可能存在上下文串扰，是否合适需业务方结合具体实现自行判断。
     */
    @Builder.Default
    protected int sessionPoolSize = 0;
}