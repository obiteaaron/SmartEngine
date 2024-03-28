package com.alibaba.smart.framework.engine.bpmn.behavior.gateway;

import com.alibaba.smart.framework.engine.behavior.base.AbstractActivityBehavior;
import com.alibaba.smart.framework.engine.bpmn.assembly.gateway.ParallelGateway;
import com.alibaba.smart.framework.engine.common.util.InstanceUtil;
import com.alibaba.smart.framework.engine.common.util.MapUtil;
import com.alibaba.smart.framework.engine.common.util.MarkDoneUtil;
import com.alibaba.smart.framework.engine.configuration.ConfigurationOption;
import com.alibaba.smart.framework.engine.configuration.ParallelServiceOrchestration;
import com.alibaba.smart.framework.engine.configuration.PvmActivityTask;
import com.alibaba.smart.framework.engine.configuration.scanner.AnnotationScanner;
import com.alibaba.smart.framework.engine.context.ExecutionContext;
import com.alibaba.smart.framework.engine.context.factory.ContextFactory;
import com.alibaba.smart.framework.engine.exception.EngineException;
import com.alibaba.smart.framework.engine.extension.annoation.ExtensionBinding;
import com.alibaba.smart.framework.engine.extension.constant.ExtensionConstant;
import com.alibaba.smart.framework.engine.model.instance.ExecutionInstance;
import com.alibaba.smart.framework.engine.model.instance.ProcessInstance;
import com.alibaba.smart.framework.engine.pvm.PvmActivity;
import com.alibaba.smart.framework.engine.pvm.PvmTransition;
import com.alibaba.smart.framework.engine.pvm.event.EventConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@ExtensionBinding(group = ExtensionConstant.ACTIVITY_BEHAVIOR, bindKey = ParallelGateway.class)
public class ParallelGatewayBehavior extends AbstractActivityBehavior<ParallelGateway> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelGatewayBehavior.class);


    public ParallelGatewayBehavior() {
        super();
    }

    @Override
    public boolean enter(ExecutionContext context, PvmActivity pvmActivity) {

        // ParallelGatewayBehavior 同时承担 fork 和 join 职责。支持多入多出。

        ParallelGateway parallelGateway = (ParallelGateway) pvmActivity.getModel();

        ConfigurationOption serviceOrchestrationOption = processEngineConfiguration
                .getOptionContainer().get(ConfigurationOption.SERVICE_ORCHESTRATION_OPTION.getId());

        // 此处，针对基于并行网关的服务编排做了特殊优化处理。
        if (serviceOrchestrationOption.isEnabled()) {
            fireEvent(context, pvmActivity, EventConstant.ACTIVITY_START);
            ParallelServiceOrchestration parallelServiceOrchestration = context.getProcessEngineConfiguration().getParallelServiceOrchestration();
            parallelServiceOrchestration.orchestrateService(context, pvmActivity);
            // 由于这里仅是服务编排，所以这里直接返回`暂停`信号。
            return true;
        } else {
            return processDefaultLogic(context, pvmActivity, parallelGateway);
        }
    }

    private boolean processDefaultLogic(ExecutionContext context, PvmActivity pvmActivity, ParallelGateway parallelGateway) {

        Map<String, PvmTransition> incomeTransitions = pvmActivity.getIncomeTransitions();

        int inComeTransitionSize = incomeTransitions.size();

        // 2024年3月27日 直接支持多入多出，一个组件直接同时支持fork join，方便业务使用同一个组件完成复杂流程
        if (inComeTransitionSize == 1) {
            // 只有一个流入线，无需进行join，则直接执行流程即可
            return super.enter(context, pvmActivity);
        } else {
            // 多个流入线，进行join

            // join 时必须使用分布式锁。
            // update at 2022.10.31 这里的缩粒度不够大,在极端环境下,还是存在数据可见性的问题.
            // 比如说,当这个锁结束后, 外面还需要进行持久化数据. 理论上,另外一个线程进来执行时,可能这个持久化数据还未完成.
            // 所以这里取消掉默认锁,改为建议在生产环境使用使用分布式锁.
            // 2024年3月28日，以上为原作者的建议，并且原作者已经删除了默认的锁实现接口。但原作者描述的其实是并行网关的前置节点，多条线分别过来，在不同signal触发的情况下，数据存在可见性问题。
            // 如果当前组件只是内存执行，而且执行很快，比如单测com.alibaba.smart.framework.engine.test.parallelgateway.multi.thread.ConcurrentParallelGatewayTest.testServiceTaskParallelGateway中，
            // 由于单测什么都不做，执行非常快，并且直接通过，会出现在com.alibaba.smart.framework.engine.common.util.InstanceUtil.findActiveExecution时产生ConcurrentModificationException异常
            // 而原来的代码，在invokeAll之后，并没有再一次get。invokeAll会将内部的异常信息隐藏掉，导致异常信息并没有表现出来，所以原来单测能过，且流程并不会因为节点失败而停止，还会继续往后执行，这不符合预期。
            // 因此，这里在做join的时候，依然需要使用锁，外部signal的时候，需要对整个流程加分布式锁，避免业务数据修改版本错乱。而内部依然需要同步锁，避免多线程快速执行时流程表现为异常。
            synchronized (context.getProcessInstance()) {
                super.enter(context, pvmActivity);
                Collection<PvmTransition> inComingPvmTransitions = incomeTransitions.values();
                ProcessInstance processInstance = context.getProcessInstance();

                // 当前内存中的，新产生的 active ExecutionInstance
                List<ExecutionInstance> executionInstanceListFromMemory = InstanceUtil.findActiveExecution(processInstance);

                // 当前持久化介质中中，已产生的 active ExecutionInstance。
                List<ExecutionInstance> executionInstanceListFromDB = executionInstanceStorage.findActiveExecution(processInstance.getInstanceId(), super.processEngineConfiguration);
                LOGGER.debug("ParallelGatewayBehavior Joined, the  value of  executionInstanceListFromMemory, executionInstanceListFromDB   is {} , {} ", executionInstanceListFromMemory, executionInstanceListFromDB);

                // Merge 数据库中和内存中的EI。如果是 custom模式，则可能会存在重复记录，所以这里需要去重。 如果是 DataBase 模式，则不会有重复的EI.
                List<ExecutionInstance> mergedExecutionInstanceList = new ArrayList<ExecutionInstance>(executionInstanceListFromMemory.size());
                for (ExecutionInstance instance : executionInstanceListFromDB) {
                    if (executionInstanceListFromMemory.contains(instance)) {
                        //ignore
                    } else {
                        mergedExecutionInstanceList.add(instance);
                    }
                }
                mergedExecutionInstanceList.addAll(executionInstanceListFromMemory);

                int reachedJoinCounter = 0;
                List<ExecutionInstance> chosenExecutionInstanceList = new ArrayList<ExecutionInstance>(executionInstanceListFromMemory.size());

                for (ExecutionInstance executionInstance : mergedExecutionInstanceList) {
                    if (executionInstance.getProcessDefinitionActivityId().equals(parallelGateway.getId())) {
                        reachedJoinCounter++;
                        chosenExecutionInstanceList.add(executionInstance);
                    }
                }

                int countOfTheJoinLatch = inComingPvmTransitions.size();

                LOGGER.debug("chosenExecutionInstanceList , reachedJoinCounter,countOfTheJoinLatch  is {} , {} , {} ", chosenExecutionInstanceList, reachedJoinCounter, countOfTheJoinLatch);

                ExecutionInstance executionInstanceCurrent = context.getExecutionInstance();
                if (reachedJoinCounter == countOfTheJoinLatch) {
                    // 把当前停留在join节点的执行实例，除了当前实例外，其他的全部complete掉，主要是为了确保当前节点的execute可以正常执行和暂停。然后再持久化时，会自动忽略掉这些节点。
                    for (ExecutionInstance executionInstance : chosenExecutionInstanceList) {
                        if (executionInstanceCurrent.equals(executionInstance)) {
                            continue;
                        }
                        MarkDoneUtil.markDoneExecutionInstance(executionInstance, executionInstanceStorage, processEngineConfiguration);
                    }
                    return false;
                } else {
                    // 未完成的话,流程继续暂停
                    return true;
                }
            }
        }
    }

    @Override
    public void execute(ExecutionContext context, PvmActivity pvmActivity) {
        super.execute(context, pvmActivity);
    }

    @Override
    public void leave(ExecutionContext context, PvmActivity pvmActivity) {
        ConfigurationOption serviceOrchestrationOption = processEngineConfiguration
                .getOptionContainer().get(ConfigurationOption.SERVICE_ORCHESTRATION_OPTION.getId());

        // 此处，针对基于并行网关的服务编排做了特殊优化处理。和SmartEngine原来的实现一样，直接执行父类的实现。
        if (serviceOrchestrationOption.isEnabled()) {
            super.leave(context, pvmActivity);
        } else {
            processDefaultLogicLeave(context, pvmActivity);
        }
    }

    private void processDefaultLogicLeave(ExecutionContext context, PvmActivity pvmActivity) {
        // fork
        fireEvent(context, pvmActivity, EventConstant.ACTIVITY_END);

        Map<String, PvmTransition> outcomeTransitions = pvmActivity.getOutcomeTransitions();
        if (MapUtil.isEmpty(outcomeTransitions)) {
            LOGGER.warn("InclusiveGateway No outcome transitions activityInstanceId={}", context.getActivityInstance().getInstanceId());
            return;
        }

        // 一条线或者没有线程池，则直接同步执行；多条线且有线程池则使用多线程执行
        ExecutorService executorService = context.getProcessEngineConfiguration().getExecutorService();
        if (null == executorService || outcomeTransitions.size() == 1) {
            // 顺序执行fork
            for (Entry<String, PvmTransition> pvmTransitionEntry : outcomeTransitions.entrySet()) {
                PvmActivity target = pvmTransitionEntry.getValue().getTarget();
                // 给上下文加入连线信息
                context.setTransition(pvmTransitionEntry.getValue().getModel());
                // 进入下一个节点
                target.enter(context);
            }
        } else {
            // 并发执行fork
            AnnotationScanner annotationScanner = processEngineConfiguration.getAnnotationScanner();
            ContextFactory contextFactory = annotationScanner.getExtensionPoint(ExtensionConstant.COMMON, ContextFactory.class);

            Collection<PvmActivityTask> tasks = new ArrayList<PvmActivityTask>(outcomeTransitions.size());

            for (Entry<String, PvmTransition> pvmTransitionEntry : outcomeTransitions.entrySet()) {
                PvmActivity target = pvmTransitionEntry.getValue().getTarget();

                // 注意,ExecutionContext 在多线程情况下,必须要新建对象,防止一些变量被并发修改.
                ExecutionContext subThreadContext = contextFactory.createChildThreadContext(context);
                // 给上下文加入连线信息
                subThreadContext.setTransition(pvmTransitionEntry.getValue().getModel());
                PvmActivityTask task = context.getProcessEngineConfiguration().getPvmActivityTaskFactory().create(target, subThreadContext, pvmTransitionEntry.getValue().getModel());

                tasks.add(task);
            }

            try {
                // 2024年3月27日 原来的代码是invokeAll，且没有执行get，那么会在子线程异常，且子线程的逻辑时向上抛出异常时，丢失异常信息，形成流程阻断无法排查问题。
                // 此处修改为自己get等待结束，遇到异常则抛出
                // 注意这里的invokeAll会一直阻塞，如果遇到多层并行网关嵌套，超过ThreadPoolExecutor线程池容量的情况下，会出现线程池死锁，建议：
                // 1. 建议采用ForkJoinPool，但依然不能嵌套太多层，避免StackOverflow。
                // 2. 也可以采用ThreadPoolExecutor，用SynchronousQueue+CallerRunsPolicy，但依然不能嵌套太多层，避免StackOverflow。
                // 关于子线程的 TraceId 如何传递的问题，建议将TraceId写入Request中，并且在实现 JavaDelegation 和 Listener 的时候增加顶级抽象类，用于组转业务参数，以及读取 Request 中的 TraceId 并尽早写入到 MDC 或者子线程能取到的地方。
                List<Future<PvmActivity>> futures = executorService.invokeAll(tasks);
                for (Future<PvmActivity> future : futures) {
                    future.get();
                }
            } catch (InterruptedException e) {
                throw new EngineException(e.getMessage(), e);
            } catch (ExecutionException e) {
                throw new EngineException(e.getMessage(), e);
            }
        }
    }
}
