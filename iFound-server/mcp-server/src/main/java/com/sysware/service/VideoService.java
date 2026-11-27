package com.sysware.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysware.swcloud.domian.AjaxResultVO;
import com.sysware.swcloud.domian.video.CameraListDto;
import com.sysware.swcloud.domian.video.CameraSteamVo;
import com.sysware.swcloud.domian.video.CameraStreamDto;
import com.sysware.swcloud.feign.VideoClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 视频流相关业务服务。
 *
 * @Author: LiYanRu
 * @date: 2026-07-06
 **/
@Service
@RequiredArgsConstructor
public class VideoService {
    /**
     * 视频流请求基础url
     */
    private static final String CAMERA_STREAM_API_BASE_URL = "http://172.12.20.25/";

    /**
     * 访问视频流的IP
     */
    private static final String CAMERA_STREAM_BASE_IP = "172.12.20.50:10000";

    /**
     * JSON对象转换工具
     */
    private final ObjectMapper objectMapper;

    /**
     * HTTP调用客户端
     */
    private final RestClient restClient;

    /**
     * 视频远程调用客户端
     */
    private final VideoClient videoClient;

    /**
     * 分页查询摄像头列表。
     *
     * @param dto 查询参数
     * @return 摄像头列表查询结果
     */
    public AjaxResultVO listPage(CameraListDto dto) {
        // 下游接口将 ids 作为 JSON 数组字符串解析（JSON.parseArray），因此序列化后传参
        String ids = null;
        if (dto.getIds() != null && !dto.getIds().isEmpty()) {
            ids = toJsonArrayString(dto.getIds());
        }
        return videoClient.listPage(dto.getName(), dto.getPage(), dto.getLimit(), dto.getOnline(), ids);
    }

    /**
     * 将 ID 集合序列化为 JSON 数组字符串。
     *
     * @param ids ID 集合
     * @return JSON 数组字符串，如 ["id1","id2"]
     */
    private String toJsonArrayString(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("ids 序列化失败: " + ids, ex);
        }
    }

    public CameraSteamVo streamStart(CameraStreamDto dto) {
        // 1. 拼接请求路径
        String url = CAMERA_STREAM_API_BASE_URL + dto.getNvrId() + "/livegbs/api/v1/stream/start";
        Map<String, String> params = objectMapper.convertValue(dto, new TypeReference<>() {});
        // null 参数不传
        params.values().removeIf(Objects::isNull);
        params.remove("nvrId");

        // 2. 拼接请求参数
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        params.forEach(builder::queryParam);
        CameraSteamVo vo = restClient.get()
                .uri(builder.build(true).toUri())
                .retrieve()
                .body(CameraSteamVo.class);

        // 3. 将响应体的视频流地址替换为可访问的 FLV，HLS，WEBRTC，WS_FLV
        if (vo == null){
            throw new RuntimeException("获取摄像头视频流出错");
        }
        String flv = vo.getFlv();
        String hls = vo.getHls();
        String webrtc = vo.getWebrtc();
        String wsFlv = vo.getWsFlv();
        vo.setFlv(replaceStreamHost(flv));
        vo.setHls(replaceStreamHost(hls));
        vo.setWebrtc(replaceStreamHost(webrtc));
        vo.setWsFlv(replaceStreamHost(wsFlv));

        return vo;
    }

    /**
     * 将流地址中的主机和端口替换为系统对外暴露的流媒体访问地址。
     *
     * @param streamUrl 原始流地址
     * @return 替换后的流地址
     */
    private String replaceStreamHost(String streamUrl) {
        if (streamUrl == null || streamUrl.isBlank()) {
            return streamUrl;
        }

        URI uri = URI.create(streamUrl);
        return UriComponentsBuilder.fromUri(uri)
                .host(extractHost(CAMERA_STREAM_BASE_IP))
                .port(extractPort(CAMERA_STREAM_BASE_IP))
                .build(true)
                .toUriString();
    }

    /**
     * 从 host:port 字符串中提取主机地址。
     *
     * @param hostPort 主机和端口，格式如 172.12.20.50:10000
     * @return 主机地址
     */
    private String extractHost(String hostPort) {
        int separatorIndex = hostPort.indexOf(':');
        return separatorIndex >= 0 ? hostPort.substring(0, separatorIndex) : hostPort;
    }

    /**
     * 从 host:port 字符串中提取端口号。
     *
     * @param hostPort 主机和端口，格式如 172.12.20.50:10000
     * @return 端口号，未配置端口时返回 -1
     */
    private int extractPort(String hostPort) {
        int separatorIndex = hostPort.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == hostPort.length() - 1) {
            return -1;
        }
        return Integer.parseInt(hostPort.substring(separatorIndex + 1));
    }
}
