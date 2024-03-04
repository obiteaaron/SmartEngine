package com.alibaba.smart.framework.engine.bpmn.behavior.gateway;

import com.alibaba.smart.framework.engine.common.util.MapUtil;
import com.alibaba.smart.framework.engine.common.util.StringUtil;
import com.alibaba.smart.framework.engine.configuration.ListenerExecutor;
import com.alibaba.smart.framework.engine.context.ExecutionContext;
import com.alibaba.smart.framework.engine.exception.EngineException;
import com.alibaba.smart.framework.engine.pvm.PvmActivity;
import com.alibaba.smart.framework.engine.pvm.PvmTransition;
import com.alibaba.smart.framework.engine.pvm.event.EventConstant;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by 高海军 帝奇 74394 on  2020-09-21 18:12.
 */
@Slf4j
public class ExclusiveGatewayBehaviorHelper {

    private static final String DEFAULT = "default";

    public static void chooseOnlyOne(PvmActivity pvmActivity, ExecutionContext context, Map<String, PvmTransition> outcomeTransitions) {
        choose(pvmActivity, context, outcomeTransitions, false);
    }

    /**
     * 选择
     *
     * @param pvmActivity        pvm活动
     * @param context            上下文
     * @param outcomeTransitions 结果转换
     * @param allowMultiBranch   允许多分支
     */
    public static void choose(PvmActivity pvmActivity,
                              ExecutionContext context,
                              Map<String, PvmTransition> outcomeTransitions,
                              boolean allowMultiBranch) {

        String processDefinitionActivityId = pvmActivity.getModel().getId();

        List<PvmTransition> matchedTransitions = new ArrayList<PvmTransition>(outcomeTransitions.size());

        for (Map.Entry<String, PvmTransition> transitionEntry : outcomeTransitions.entrySet()) {

            PvmTransition pendingTransition = transitionEntry.getValue();
            boolean matched = pendingTransition.match(context);

            if (matched) {
                matchedTransitions.add(pendingTransition);
            }

        }

        //如果都没匹配到,就使用DefaultSequenceFlow
        if (matchedTransitions.isEmpty()) {

            Map<String, String> properties = pvmActivity.getModel().getProperties();
            if (MapUtil.isNotEmpty(properties)) {
                String defaultSeqFLowId = properties.get(DEFAULT);
                if (StringUtil.isNotEmpty(defaultSeqFLowId)) {
                    PvmTransition pvmTransition = outcomeTransitions.get(defaultSeqFLowId);
                    if (null != pvmTransition) {
                        matchedTransitions.add(pvmTransition);
                    } else {
                        throw new EngineException("No default sequence flow found,check activity id :" + processDefinitionActivityId);
                    }
                } else {
                    // do nothing
                }

            } else {
                throw new EngineException("properties can not be empty,  check activity id :" + processDefinitionActivityId);

            }


        }

        // 默认允许0个通过
        if (matchedTransitions.isEmpty()) {
            log.warn("No outcome transitions matched activityInstanceId={}", context.getActivityInstance().getInstanceId());
            return;
//            throw new EngineException("No Transitions matched,please check input request and condition expression,activity id :" + processDefinitionActivityId);
        }


        if (1 != matchedTransitions.size()) {
            if (!allowMultiBranch) {
                throw new EngineException("Multiple Transitions matched: " + matchedTransitions + " ,check activity id :" + processDefinitionActivityId);
            }
        }

        // 匹配成功的连线则按顺序执行
        for (PvmTransition matchedPvmTransition : matchedTransitions) {
            PvmActivity target = matchedPvmTransition.getTarget();

            // 把来源写进去，对于整个DAG图来说，记录节点流转过程很重要
            context.setTransition(matchedPvmTransition.getModel());

            // 触发take事件
            ListenerExecutor listenerExecutor = context.getProcessEngineConfiguration().getListenerExecutor();
            listenerExecutor.execute(EventConstant.take, matchedPvmTransition.getModel(), context);

            target.enter(context);
            // 避免前一个分支的暂停影响后面分支的执行
            context.setNeedPause(false);
        }
    }
}