package com.alibaba.smart.framework.engine.behavior;

import com.alibaba.smart.framework.engine.context.ExecutionContext;
import com.alibaba.smart.framework.engine.pvm.PvmActivity;

/**
 * @author 高海军 帝奇  2016.11.11
 * @author ettear 2016.04.13
 */
public interface ActivityBehavior {

    /**
     * @return true 流程分支将会暂停、false 流程分支不会暂停
     */
    boolean enter(ExecutionContext context, PvmActivity pvmActivity);

    void execute(ExecutionContext context, PvmActivity pvmActivity);

    void leave(ExecutionContext context, PvmActivity pvmActivity);

}
