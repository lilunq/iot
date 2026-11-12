package com.sysware.swcloud.domian;

import lombok.Data;

import java.util.List;

@Data
public class ScadaSceneAllEntity extends ScadaSceneEntity {
    List<ScadaSceneParamEntity> sceneParamList;
}
