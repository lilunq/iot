package com.sysware.controller;

import com.sysware.swcloud.common.core.vo.Res;
import com.sysware.swcloud.domian.*;

import com.sysware.swcloud.feign.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;

@Slf4j
@RestController
@Tag(name = "控制接口", description = "mcp接口")
public class SceneController {
    @Autowired
    private ScadaClient scadaClient;

    @Operation(summary = "查询场景", description = "可筛选:name")
    @GetMapping("/scene/scenePageList")
    public AjaxResultVO listScene(@RequestParam(name = "name", required = false) String name){
        return scadaClient.listScene(name==null?new HashMap<>():new HashMap<String, Object>(){{put("name",name);}});
    }
    /**
     *查询场景的控制参数
     * @param scene_id 场景id
     * @return
     */
    @Operation(summary = "查询某个场景的控制参数", description = "必传参数：场景id")
    @GetMapping("/scene/sceneParamList")
    public AjaxResultVO sceneParamList(@RequestParam(name = "scene_id") String scene_id){
        return scadaClient.sceneParamList(scene_id==null?new HashMap<>():new HashMap<String, Object>(){{put("scene_id",scene_id);}});
    }


}
