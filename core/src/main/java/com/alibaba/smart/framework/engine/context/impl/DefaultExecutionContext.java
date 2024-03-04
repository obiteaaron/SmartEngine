package com.alibaba.smart.framework.engine.context.impl;

import com.alibaba.smart.framework.engine.configuration.ProcessEngineConfiguration;
import com.alibaba.smart.framework.engine.context.ExecutionContext;
import com.alibaba.smart.framework.engine.model.assembly.BaseElement;
import com.alibaba.smart.framework.engine.model.assembly.ProcessDefinition;
import com.alibaba.smart.framework.engine.model.assembly.Transition;
import com.alibaba.smart.framework.engine.model.instance.ActivityInstance;
import com.alibaba.smart.framework.engine.model.instance.ExecutionInstance;
import com.alibaba.smart.framework.engine.model.instance.ProcessInstance;
import lombok.Data;

import java.util.Map;

/**
 *  Created by ettear on 16-4-19.
 */
@Data
public class DefaultExecutionContext implements ExecutionContext {

    private ExecutionContext parent;

    private ProcessInstance processInstance;

    private ExecutionInstance executionInstance;

    private BaseElement baseElement;

    private ActivityInstance activityInstance;

    private ProcessDefinition processDefinition;

    private ProcessEngineConfiguration processEngineConfiguration;

    private Map<String, Object> request;

    private Map<String, Object> response;

    private boolean needPause;

    private boolean nested;

    private Long blockId;

    private Transition transition;


}
