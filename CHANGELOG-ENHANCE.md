# 3.0.0
新增：  
1. 增加包容网关支持，且支持暂停
2. storage-custom模块的实现不使用ThreadLocal，且只实现了基本流程执行的必要接口，避免在Java21中虚拟线程无法使用，改用内存缓存+ID索引实现。注：Mysql模块其实也可以换成H2嵌入式数据库，但不方便直接在该模块中做H2数据清理，如果遇到大量任务执行，可能会占用内存过大。
3. ExecutionContext增加Transition读写定义，方便业务知道当前节点的来源连线是哪一条

优化：  
1. 排他网关支持暂停
2. 修改并行网关，支持多入多出，且支持暂停和重新触发执行。增加同步锁，避免多线程快速执行时出现异常。

建议：
1. 使用并行网关时，不建议使用多线程，理由如下：
   1. 单线程在绝大多数情况下足够使用了。如果一定需要并行，建议业务将并行网关Fork下游节点实现为异步逻辑，可以暂停、恢复，并行网关暂停恢复已经在增强版支持。
   2. 多线程会无法带入上下文信息，导致TraceId丢失。关于子线程的 TraceId 如何传递的问题，建议将TraceId写入Request中，并且在实现 JavaDelegation 和 Listener 的时候增加顶级抽象类，用于组转业务参数，以及读取 Request 中的 TraceId 并尽早写入到 MDC 或者子线程能取到的地方。
   3. 并行网关多层嵌套时，如果太多层，会出现死锁，如果一定要用多线程，建议采用ForkJoinPool，也可以采用ThreadPoolExecutor，用SynchronousQueue+CallerRunsPolicy，但依然不能嵌套太多层，避免StackOverflow。