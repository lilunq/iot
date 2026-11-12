package com.sysware.xian.swcloud.cons;

/**
 * hermes 发送的消息类型常量类
 * <p>
 * 涵盖 hermes 流式对话（SSE）过程中可能推送的所有事件类型：
 * 对话消息、工具调用、思考过程、子代理、审批请求、语音、错误等。
 *
 * @Author: LiYanRu
 * @date: 2026-08-05
 **/
public class HermesEventCons {

    // ==================== 对话消息 ====================

    /** 消息开始生成（首帧，标识本轮回复开始） */
    public static final String MESSAGE_START = "message.start";
    /** 消息增量（流式输出过程中的文本片段，可多次推送） */
    public static final String MESSAGE_DELTA = "message.delta";
    /** 消息生成完成（携带完整回复内容，本轮对话结束） */
    public static final String MESSAGE_COMPLETE = "message.complete";

    // ==================== 工具调用 ====================

    /** 工具开始调用（标识某个工具即将执行） */
    public static final String TOOL_START = "tool.start";
    /** 工具执行进度（工具执行过程中的阶段性进展） */
    public static final String TOOL_PROGRESS = "tool.progress";
    /** 工具调用完成（携带工具执行结果） */
    public static final String TOOL_COMPLETE = "tool.complete";
    /** 工具生成中（工具持续产出内容时的占位/心跳事件） */
    public static final String TOOL_GENERATING = "tool.generating";

    // ==================== 思考过程 ====================

    /** 思考内容增量（模型内部思考过程的流式输出） */
    public static final String THINKING_DELTA = "thinking.delta";
    /** 推理内容增量（推理过程的流式输出，区别于 thinking） */
    public static final String REASONING_DELTA = "reasoning.delta";
    /** 推理内容可用（推理完成，可展示给用户） */
    public static final String REASONING_AVAILABLE = "reasoning.available";

    /** 会话信息（携带 sessionKey / sessionId 等会话标识） */
    public static final String SESSION_INFO = "session.info";

    // ==================== 子代理（Subagent） ====================

    /** 子代理生成请求（主代理请求创建子代理） */
    public static final String SUBAGENT_SPAWN_REQUESTED = "subagent.spawn_requested";
    /** 子代理启动 */
    public static final String SUBAGENT_START = "subagent.start";
    /** 子代理思考中 */
    public static final String SUBAGENT_THINKING = "subagent.thinking";
    /** 子代理调用工具 */
    public static final String SUBAGENT_TOOL = "subagent.tool";
    /** 子代理执行进度 */
    public static final String SUBAGENT_PROGRESS = "subagent.progress";
    /** 子代理执行完成 */
    public static final String SUBAGENT_COMPLETE = "subagent.complete";

}
