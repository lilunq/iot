package com.sysware.config;

import com.sysware.core.report.ReportTool;
import com.sysware.core.subAgent.SubAgentTool;
import com.sysware.tools.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class MCPToolConfig {

    @Bean
    public List<ToolCallback> toolCallbacks(ReportTool reportTool, RagFlowTool ragFlowTool, ScadaTool scadaTool, AdminTool adminTool, VideoTool videoTool, IfimTool ifimTool, DashboardTool dashboardTool, SubAgentTool subAgentTool,XadTool xadTool) {
        return List.of(ToolCallbacks.from(reportTool,videoTool, ragFlowTool, scadaTool, adminTool, ifimTool, dashboardTool, subAgentTool,xadTool));
    }

    //    @Bean
//    public List<ToolCallback> toolCallbacks(ScadaTool scadaTool, AdminTool adminTool, IemsTool iemsTool, VideoTool videoTool, XIANTool xianTool, TrafficTool trafficTool,ProductionTool productionTool) {
//        return List.of(ToolCallbacks.from( scadaTool, adminTool, iemsTool, videoTool,xianTool, trafficTool,productionTool));
//    }
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    /**
     * 用于调用聊天接口，长超时时间
     *
     */
    @Bean
    public RestClient subAgentRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(180_000); // 3 分钟
        return builder.requestFactory(factory).build();
    }

}
