package com.sysware.swcloud.domian.video;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description: 获取摄像头视频流的响应体
 * @Author: LiYanRu
 * @date: 2026-06-25
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CameraSteamVo {
    /**
     * 是否启用音频
     */
    @JsonProperty("AudioEnable")
    private Boolean audioEnable;

    /**
     * CDN 地址
     */
    @JsonProperty("CDN")
    private String cdn;

    /**
     * 级联数量
     */
    @JsonProperty("CascadeSize")
    private Integer cascadeSize;

    /**
     * 通道 ID
     */
    @JsonProperty("ChannelID")
    private String channelId;

    /**
     * 通道名称
     */
    @JsonProperty("ChannelName")
    private String channelName;

    /**
     * 云台类型
     */
    @JsonProperty("ChannelPTZType")
    private Integer channelPtzType;

    /**
     * 是否开启云端录像
     */
    @JsonProperty("CloudRecord")
    private Boolean cloudRecord;

    /**
     * 解码数量
     */
    @JsonProperty("DecodeSize")
    private Integer decodeSize;

    /**
     * 设备 ID
     */
    @JsonProperty("DeviceID")
    private String deviceId;

    /**
     * 时长
     */
    @JsonProperty("Duration")
    private Long duration;

    /**
     * FLV 播放地址
     */
    @JsonProperty("FLV")
    private String flv;

    /**
     * HLS 播放地址
     */
    @JsonProperty("HLS")
    private String hls;

    /**
     * 输入码率
     */
    @JsonProperty("InBitRate")
    private Integer inBitRate;

    /**
     * 输入字节数
     */
    @JsonProperty("InBytes")
    private Long inBytes;

    /**
     * 输出路数
     */
    @JsonProperty("NumOutputs")
    private Integer numOutputs;

    /**
     * 是否按需拉流
     */
    @JsonProperty("Ondemand")
    private Boolean ondemand;

    /**
     * 输出字节数
     */
    @JsonProperty("OutBytes")
    private Long outBytes;

    /**
     * RTMP 播放地址
     */
    @JsonProperty("RTMP")
    private String rtmp;

    /**
     * RTP 包数量
     */
    @JsonProperty("RTPCount")
    private Long rtpCount;

    /**
     * RTP 丢包数量
     */
    @JsonProperty("RTPLostCount")
    private Long rtpLostCount;

    /**
     * RTP 丢包率
     */
    @JsonProperty("RTPLostRate")
    private Integer rtpLostRate;

    /**
     * RTSP 播放地址
     */
    @JsonProperty("RTSP")
    private String rtsp;

    /**
     * 录像开始时间
     */
    @JsonProperty("RecordStartAt")
    private String recordStartAt;

    /**
     * 转发数量
     */
    @JsonProperty("RelaySize")
    private Integer relaySize;

    /**
     * 流媒体服务 ID
     */
    @JsonProperty("SMSID")
    private String smsId;

    /**
     * 抓拍图片地址
     */
    @JsonProperty("SnapURL")
    private String snapUrl;

    /**
     * 源音频编码名称
     */
    @JsonProperty("SourceAudioCodecName")
    private String sourceAudioCodecName;

    /**
     * 源音频采样率
     */
    @JsonProperty("SourceAudioSampleRate")
    private Integer sourceAudioSampleRate;

    /**
     * 源视频编码名称
     */
    @JsonProperty("SourceVideoCodecName")
    private String sourceVideoCodecName;

    /**
     * 源视频帧率
     */
    @JsonProperty("SourceVideoFrameRate")
    private Integer sourceVideoFrameRate;

    /**
     * 源视频高度
     */
    @JsonProperty("SourceVideoHeight")
    private Integer sourceVideoHeight;

    /**
     * 源视频宽度
     */
    @JsonProperty("SourceVideoWidth")
    private Integer sourceVideoWidth;

    /**
     * 拉流开始时间
     */
    @JsonProperty("StartAt")
    private String startAt;

    /**
     * 流 ID
     */
    @JsonProperty("StreamID")
    private String streamId;

    /**
     * 传输协议
     */
    @JsonProperty("Transport")
    private String transport;

    /**
     * 视频帧数量
     */
    @JsonProperty("VideoFrameCount")
    private Long videoFrameCount;

    /**
     * WEBRTC 播放地址
     */
    @JsonProperty("WEBRTC")
    private String webrtc;

    /**
     * WS-FLV 播放地址
     */
    @JsonProperty("WS_FLV")
    private String wsFlv;
}

