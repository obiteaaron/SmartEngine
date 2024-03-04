package com.alibaba.smart.framework.engine.bpmn.assembly.gateway;

import com.alibaba.smart.framework.engine.bpmn.constant.BpmnNameSpaceConstant;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.xml.namespace.QName;

/**
 * 包容网关，可以处理更多复杂场景
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InclusiveGateway extends AbstractGateway {

    public final static QName qtype = new QName(BpmnNameSpaceConstant.NAME_SPACE, "inclusiveGateway");
    /**
     *
     */
    private static final long serialVersionUID = 5754815434014251702L;

    @Override
    public String toString() {
        return super.getId();
    }

}
