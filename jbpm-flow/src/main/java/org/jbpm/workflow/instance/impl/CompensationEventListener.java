/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.jbpm.workflow.instance.impl;

import static org.jbpm.process.core.context.exception.CompensationScope.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.exception.CompensationScope;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.process.instance.context.exception.CompensationScopeInstance;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.WorkflowRuntimeException;
import org.jbpm.workflow.instance.node.CompositeContextNodeInstance;
import org.jbpm.workflow.instance.node.CompositeNodeInstance;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.NodeContainer;
import org.kie.api.definition.process.Process;
import org.kie.api.runtime.process.EventListener;

class CompensationEventListener implements EventListener {

    private WorkflowProcessInstanceImpl processInstance;
    
    public CompensationEventListener(WorkflowProcessInstanceImpl instance) { 
        this.processInstance = instance;
    }
    
    private ProcessInstance getProcessInstance() { 
        return processInstance;
    }
    
    /**
     * When signaling compensation, you can do that in 1 of 2 ways: 
     * 1. signalEvent("Compensation", <node-with-compensation-handler-id>)
     *    This is specific compensation, that only possibly triggers the compensation handler
     *    attached to the node referred to by the <node-with-compensation-handler-id>.
     * 2. signalEvent("Compensation", "implicit:" + <node-container-containing-compensation-scope-id> )
     *    This is implicit or general compensation, in which you trigger all visible compensation handlers 
     *    (in the proper order, etc.) in the (sub-)process referred to by 
     *    the <node-container-containing-compensation-scope-id>. 
     */
    public void signalEvent(String compensationType, Object activityRefStr) {
        if( activityRefStr == null || ! (activityRefStr instanceof String) ) { 
            throw new WorkflowRuntimeException(null, getProcessInstance(), 
                    "Compensation can only be triggered with String events, not an event of type " 
                    + (activityRefStr == null ? "null" : activityRefStr.getClass().getSimpleName()));
        }
       
        // 1. parse the activity ref (is it general or specific compensation?)
        String activityRef = (String) activityRefStr;
        String toCompensateNodeId = activityRef;
        boolean generalCompensation = false;
        if( activityRef.startsWith(IMPLICIT_COMPENSATION_PREFIX) ) {
            toCompensateNodeId = activityRef.substring(IMPLICIT_COMPENSATION_PREFIX.length());
            generalCompensation = true;
        } 
       
        org.jbpm.process.core.Process process = (org.jbpm.process.core.Process) processInstance.getProcess();

        // 2. for specific compensation: find the node that will be compensated
        //    for general compensation: find the compensation scope container that contains all the visible compensation handlers
        Node toCompensateNode = null;
        ContextContainer compensationScopeContainer = null;
        if( generalCompensation ) { 
            if( toCompensateNodeId.equals(processInstance.getProcessId()) ) {
                compensationScopeContainer = process;
            } else { 
                compensationScopeContainer = (ContextContainer) findNode(toCompensateNodeId);
            }
        } else { 
            toCompensateNode = findNode(toCompensateNodeId);
        }
        
        // 3. If the node exists, 
        //   a. find the node container for which the compensation handler is visible
        //   b. create the compensation scope instance
        //   c. handle the exception (which also cleans up the generated node instances)
        if( toCompensateNode != null || compensationScopeContainer != null ) { 
            CompensationScope compensationScope = null;
            if( compensationScopeContainer != null  ) { 
                // general 
                compensationScope = (CompensationScope) compensationScopeContainer.getDefaultContext(COMPENSATION_SCOPE);
            } else { 
                // specific
                compensationScope = (CompensationScope) ((NodeImpl) toCompensateNode).resolveContext(COMPENSATION_SCOPE, toCompensateNodeId);
            }
            assert compensationScope != null : "Compensation scope for node [" + toCompensateNodeId + "] could not be found!";

            CompensationScopeInstance scopeInstance = (CompensationScopeInstance) processInstance.getContextInstance(compensationScope);
            scopeInstance.setCompensationScope(compensationScope); 
            
            scopeInstance.handleException(activityRef, null);     
        }
    }
    
    private Node findNode(String nodeId) { 
        Node found = null;
        Queue<Node> allProcessNodes = new LinkedList<Node>();
        allProcessNodes.addAll(Arrays.asList( processInstance.getNodeContainer().getNodes() ));
        while( ! allProcessNodes.isEmpty() ) { 
            Node node = allProcessNodes.poll();
            if( nodeId.equals(node.getMetaData().get("UniqueId")) ) {
                found = node;
                break;
            }
            if( node instanceof NodeContainer ) { 
                allProcessNodes.addAll(Arrays.asList( ((NodeContainer) node).getNodes()));
            }
        }
        return found;
    }

    private final String [] eventTypes = { "Compensation" };
    public String[] getEventTypes() {
        return eventTypes;
    }
    
}