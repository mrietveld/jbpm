package org.jbpm.workflow.instance.node.queue;

import org.kie.api.runtime.process.NodeInstance;

public interface EntryActionExceptionHandlingNodeInstance {

    void afterEntryActions(NodeInstance from, String type);

}
