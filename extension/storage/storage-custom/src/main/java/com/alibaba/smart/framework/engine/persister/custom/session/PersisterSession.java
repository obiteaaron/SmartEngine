/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2017 All Rights Reserved.
 */
package com.alibaba.smart.framework.engine.persister.custom.session;

import com.alibaba.smart.framework.engine.model.instance.ActivityInstance;
import com.alibaba.smart.framework.engine.model.instance.ExecutionInstance;
import com.alibaba.smart.framework.engine.model.instance.ProcessInstance;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author xuantian 2017-02-08 5:50 xuantian
 */
public class PersisterSession {

    @Getter
    private final Map<String, ProcessInstance> processInstances = new ConcurrentHashMap<String, ProcessInstance>(8);

    private final Map<String, ProcessInstance> executionInstanceIdIndex = new ConcurrentHashMap<String, ProcessInstance>(8);

    private final Map<String, ProcessInstance> activityInstanceIdIndex = new ConcurrentHashMap<String, ProcessInstance>(8);

    /**
     * 唯一静态实例
     */
    private static final PersisterSession INSTANCE = new PersisterSession();

    /**
     * default constructor.
     */
    private PersisterSession() {

    }

    public static PersisterSession create() {
        // 兼容原有方法不变
        return INSTANCE;
    }

    /**
     * @return the BizSession got from the thread local.
     */
    public static PersisterSession currentSession() {
        // 兼容原有方法不变
        return INSTANCE;
    }

    /**
     * the static method for destroy session for easy using.
     * @see #destroySession(ProcessInstance)
     */
    @Deprecated
    public static void destroySession() {
        // 兼容原有方法不变
        // 此处什么都不做
    }

    /**
     * 销毁会话，业务使用完成后需要销毁，避免内存堆积过多导致OOM或者内存泄露
     *
     * @param processInstance 流程实例
     */
    public static void destroySession(ProcessInstance processInstance) {
        if (processInstance == null || processInstance.getInstanceId() == null) {
            return;
        }
        INSTANCE.processInstances.remove(processInstance.getInstanceId());

        List<ActivityInstance> activityInstances = processInstance.getActivityInstances();
        if (activityInstances != null && !activityInstances.isEmpty()) {
            for (ActivityInstance activityInstance : activityInstances) {
                INSTANCE.activityInstanceIdIndex.remove(activityInstance.getInstanceId());
                List<ExecutionInstance> executionInstanceList = activityInstance.getExecutionInstanceList();
                if (executionInstanceList != null && !executionInstanceList.isEmpty()) {
                    for (ExecutionInstance executionInstance : executionInstanceList) {
                        INSTANCE.executionInstanceIdIndex.remove(executionInstance.getInstanceId());
                    }
                }
            }
        }
    }

    public static void destroySessionAll() {
        INSTANCE.processInstances.clear();
        INSTANCE.activityInstanceIdIndex.clear();
        INSTANCE.executionInstanceIdIndex.clear();
    }


    public void putProcessInstance(ProcessInstance processInstance) {
        this.processInstances.put(processInstance.getInstanceId(), processInstance);

        List<ActivityInstance> activityInstances = processInstance.getActivityInstances();
        if (activityInstances != null && !activityInstances.isEmpty()) {
            for (ActivityInstance activityInstance : activityInstances) {
                INSTANCE.activityInstanceIdIndex.put(activityInstance.getInstanceId(), processInstance);
                List<ExecutionInstance> executionInstanceList = activityInstance.getExecutionInstanceList();
                if (executionInstanceList != null && !executionInstanceList.isEmpty()) {
                    for (ExecutionInstance executionInstance : executionInstanceList) {
                        INSTANCE.executionInstanceIdIndex.put(executionInstance.getInstanceId(), processInstance);
                    }
                }
            }
        }
    }

    public ProcessInstance getProcessInstance(String instanceId) {
        return this.processInstances.get(instanceId);
    }

    public ProcessInstance getProcessInstanceByExecutionInstanceId(String instanceId) {
        return executionInstanceIdIndex.get(instanceId);
    }

    public ProcessInstance getProcessInstanceByActivityInstanceId(String instanceId) {
        return activityInstanceIdIndex.get(instanceId);
    }
}