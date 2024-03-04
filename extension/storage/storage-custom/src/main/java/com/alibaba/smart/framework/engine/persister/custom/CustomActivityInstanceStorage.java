package com.alibaba.smart.framework.engine.persister.custom;

import com.alibaba.smart.framework.engine.configuration.ProcessEngineConfiguration;
import com.alibaba.smart.framework.engine.exception.EngineException;
import com.alibaba.smart.framework.engine.extension.annoation.ExtensionBinding;
import com.alibaba.smart.framework.engine.extension.constant.ExtensionConstant;
import com.alibaba.smart.framework.engine.instance.storage.ActivityInstanceStorage;
import com.alibaba.smart.framework.engine.model.instance.ActivityInstance;
import com.alibaba.smart.framework.engine.model.instance.ProcessInstance;
import com.alibaba.smart.framework.engine.persister.custom.session.PersisterSession;

import java.util.List;

import static com.alibaba.smart.framework.engine.persister.common.constant.StorageConstant.NOT_IMPLEMENT_INTENTIONALLY;

/**
 * Created by 高海军 帝奇 74394 on 2017 February  11:54.
 */
@ExtensionBinding(group = ExtensionConstant.COMMON, bindKey = ActivityInstanceStorage.class)
public class CustomActivityInstanceStorage implements ActivityInstanceStorage {


    @Override
    public void insert(ActivityInstance instance,
                       ProcessEngineConfiguration processEngineConfiguration) {
    }

    @Override
    public ActivityInstance update(ActivityInstance instance,
                                   ProcessEngineConfiguration processEngineConfiguration) {
        throw new EngineException(NOT_IMPLEMENT_INTENTIONALLY);
    }

    @Override
    public ActivityInstance find(String activityInstanceId,
                                 ProcessEngineConfiguration processEngineConfiguration) {

        ProcessInstance processInstance = PersisterSession.currentSession().getProcessInstanceByActivityInstanceId(activityInstanceId);

        ActivityInstance matchedActivityInstance = null;

        List<ActivityInstance> activityInstances = processInstance.getActivityInstances();

        for (ActivityInstance activityInstance : activityInstances) {
            if (activityInstance.getInstanceId().equals(activityInstanceId)) {
                matchedActivityInstance = activityInstance;
                break;
            }
        }


        return matchedActivityInstance;
    }

    @Override
    public ActivityInstance findWithShading(String processInstanceId, String activityInstanceId,
            ProcessEngineConfiguration processEngineConfiguration) {
        throw new EngineException(NOT_IMPLEMENT_INTENTIONALLY);
    }


    @Override
    public void remove(String instanceId,
                       ProcessEngineConfiguration processEngineConfiguration) {
        throw new EngineException(NOT_IMPLEMENT_INTENTIONALLY);
    }

    @Override
    public List<ActivityInstance> findAll(String processInstanceId,
                                          ProcessEngineConfiguration processEngineConfiguration) {
        ProcessInstance processInstance= PersisterSession.currentSession().getProcessInstance(processInstanceId);
        return null == processInstance ? null : processInstance.getActivityInstances();
    }
}
