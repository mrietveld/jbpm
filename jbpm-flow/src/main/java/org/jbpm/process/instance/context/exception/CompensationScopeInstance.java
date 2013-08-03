/**
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.process.instance.context.exception;

import static org.jbpm.process.core.context.exception.CompensationScope.IMPLICIT_COMPENSATION_PREFIX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;

import org.jbpm.process.core.Context;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.exception.CompensationHandler;
import org.jbpm.process.core.context.exception.CompensationScope;
import org.jbpm.process.core.context.exception.ExceptionHandler;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.ProcessInstance;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.core.node.BoundaryEventNode;
import org.jbpm.workflow.core.node.EventSubProcessNode;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.jbpm.workflow.instance.WorkflowRuntimeException;
import org.jbpm.workflow.instance.impl.NodeInstanceFactory;
import org.jbpm.workflow.instance.impl.NodeInstanceFactoryRegistry;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.CompositeContextNodeInstance;
import org.jbpm.workflow.instance.node.EndNodeInstance;
import org.jbpm.workflow.instance.node.EventNodeInstance;
import org.jbpm.workflow.instance.node.EventSubProcessNodeInstance;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.NodeContainer;

public class CompensationScopeInstance extends ExceptionScopeInstance implements NodeInstanceContainer, ContextInstanceContainer {

    private static final long serialVersionUID = 510l;

    private Stack<NodeInstance> compensationInstances = new Stack<NodeInstance>();
    
    private List<NodeInstance> nodeInstances = new ArrayList<NodeInstance>();
    private static AtomicLong nodeInstanceIdGen = new AtomicLong(0); // preparation for asynchronous? 

    private CompensationScope compensationScope;
   
    public void setCompensationScope(CompensationScope scope) { 
        this.compensationScope = scope;
    }
    
    public String getContextType() {
        return CompensationScope.COMPENSATION_SCOPE;
    }
 
    /** 
     * Compensation/Exception scope logic
     */
    
    public void handleException(String activityRef, Object dunno) {
        assert activityRef != null : "It should not be possible for the compensation activity reference to be null here.";
        
        // broadcast/general compensation in reverse order
        if( activityRef.startsWith(IMPLICIT_COMPENSATION_PREFIX) ) { 
            activityRef = activityRef.substring(IMPLICIT_COMPENSATION_PREFIX.length());
            assert activityRef.equals(compensationScope.getContextContainerId())
            : "Compensation activity ref [" + activityRef + "] does not match" +
            " Compensation Scope container id [" + compensationScope.getContextContainerId() + "]";

            Map<String, ExceptionHandler> handlers = compensationScope.getExceptionHandlers();
            List<String> completedNodeIds = ((WorkflowProcessInstanceImpl) getProcessInstance()).getCompletedNodeIds();
            ListIterator<String> iter = completedNodeIds.listIterator(completedNodeIds.size());
            while( iter.hasPrevious() ) {
                String completedId = iter.previous();
                ExceptionHandler handler = handlers.get(completedId);
                if( handler != null ) { 
                    handleException(handler, completedId, null);
                }
            }
        } else { 
            // Specific compensation 
            ExceptionHandler handler = compensationScope.getExceptionHandler(activityRef);
            if (handler == null) {
                throw new IllegalArgumentException("Could not find CompensationHandler for " + activityRef);
            }
            handleException(handler, activityRef, null);
        }


    }
    
    public void handleException(ExceptionHandler handler, String compensationActivityRef, Object dunno) {
        WorkflowProcessInstanceImpl processInstance = (WorkflowProcessInstanceImpl) getProcessInstance();
        setupCompensationNodeContainer(processInstance);
        
        if (handler instanceof CompensationHandler) {
            CompensationHandler compensationHandler = (CompensationHandler) handler;
            try {
                Node handlerNode = compensationHandler.getnode();
                setNodeContainer(handlerNode.getNodeContainer());
                if (handlerNode instanceof BoundaryEventNode ) {
                    NodeInstance compensationHandlerNodeInstance = this.getNodeInstance(handlerNode);
                    compensationInstances.add(compensationHandlerNodeInstance); 
                    // The BoundaryEventNodeInstance.signalEvent() contains the necessary logic 
                    // to check whether or not compensation may proceed (? : (not-active + completed))
                    EventNodeInstance eventNodeInstance = (EventNodeInstance) compensationHandlerNodeInstance;
                    eventNodeInstance.signalEvent("Compensation", compensationActivityRef);
                } else if (handlerNode instanceof EventSubProcessNode ) {
                    // Check that subprocess parent has completed. 
                    List<String> completedIds = processInstance.getCompletedNodeIds();
                    if( completedIds.contains(((NodeImpl) handlerNode.getNodeContainer()).getMetaData("UniqueId")) ) { 
                        NodeInstance compensationHandlerNodeInstance = this.getNodeInstance(handlerNode);
                        compensationInstances.add(compensationHandlerNodeInstance); 
                        EventSubProcessNodeInstance eventNodeInstance = (EventSubProcessNodeInstance) compensationHandlerNodeInstance;
                        eventNodeInstance.signalEvent("Compensation", compensationActivityRef);
                    }
                } 
                assert handlerNode instanceof BoundaryEventNode || handlerNode instanceof EventSubProcessNode 
                    : "Unexpected compensation handler node type : " + handlerNode.getClass().getSimpleName();
            } catch (Exception e) {
                throwWorkflowRuntimeException(this, processInstance, "Unable to execute compensation.", e);
            }
        } else {
            Exception e = new IllegalArgumentException("Unsupported compensation handler: " + handler );
            throwWorkflowRuntimeException(this, processInstance, e.getMessage(), e);
        }
    }

    private void throwWorkflowRuntimeException(NodeInstanceContainer nodeInstanceContainer, ProcessInstance processInstance, String msg, Exception e) { 
        if( nodeInstanceContainer instanceof NodeInstance ) { 
            throw new WorkflowRuntimeException((org.kie.api.runtime.process.NodeInstance) nodeInstanceContainer, processInstance, msg, e );
        } else {
            throw new WorkflowRuntimeException(null, processInstance, msg, e );
        }
    }

    private void setupCompensationNodeContainer(WorkflowProcessInstanceImpl processInstance) { 
        setCurrentLevel(processInstance.getCurrentLevel()+1);
    }
    
    /**
     * NodeInstanceContainer logic
     */
   
    // Node instance management 
    
    @Override
    public Collection<org.kie.api.runtime.process.NodeInstance> getNodeInstances() {
        return new ArrayList<org.kie.api.runtime.process.NodeInstance>(getNodeInstances(false));
    }

    @Override
    public NodeInstance getNodeInstance(long nodeInstanceId) {
        for (NodeInstance nodeInstance: nodeInstances) {
            if (nodeInstance.getId() == nodeInstanceId) {
                return nodeInstance;
            }
        }
        return null;
    }

    @Override
    public Collection<NodeInstance> getNodeInstances(boolean recursive) {
        Collection<NodeInstance> result = nodeInstances;
        if (recursive) {
            result = new ArrayList<NodeInstance>(result);
            for (Iterator<NodeInstance> iterator = nodeInstances.iterator(); iterator.hasNext(); ) {
                NodeInstance nodeInstance = iterator.next();
                if (nodeInstance instanceof NodeInstanceContainer) {
                    result.addAll(((NodeInstanceContainer) nodeInstance).getNodeInstances(true));
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    @Override
    public NodeInstance getFirstNodeInstance(long nodeId) {
        for ( final Iterator<NodeInstance> iterator = this.nodeInstances.iterator(); iterator.hasNext(); ) {
            final NodeInstance nodeInstance = iterator.next();
            if ( nodeInstance.getNodeId() == nodeId && nodeInstance.getLevel() == getCurrentLevel()) {
                return nodeInstance;
            }
        }
        return null;
    }

    @Override
    public void addNodeInstance(NodeInstance nodeInstance) {
        ((NodeInstanceImpl) nodeInstance).setId(nodeInstanceIdGen.incrementAndGet());
        this.nodeInstances.add(nodeInstance);
    }

    @Override
    public NodeInstance getNodeInstance(Node node) {
        NodeInstanceFactory conf = NodeInstanceFactoryRegistry.getInstance(getProcessInstance().getKnowledgeRuntime().getEnvironment()).getProcessNodeInstanceFactory(node);
        if (conf == null) {
            throw new IllegalArgumentException("Illegal node type: " + node.getClass());
        }
        NodeInstanceImpl nodeInstance = (NodeInstanceImpl) conf.getNodeInstance(
                node, 
                (WorkflowProcessInstance) getProcessInstance(), 
                (org.kie.api.runtime.process.NodeInstanceContainer) this);
        if (nodeInstance == null) {
            throw new IllegalArgumentException("Illegal node type: " + node.getClass());
        }
        return nodeInstance;
    }

    @Override
    public void removeNodeInstance(final NodeInstance nodeInstance) {
        this.nodeInstances.remove(nodeInstance);
    }

    @Override
    public void nodeInstanceCompleted(NodeInstance nodeInstance, String outType) {
        Node nodeInstanceNode = nodeInstance.getNode();
        if( nodeInstanceNode != null ) { 
            Object compensationBoolObj =  nodeInstanceNode.getMetaData().get("isForCompensation");
            boolean isForCompensation = compensationBoolObj == null ? false : ((Boolean) compensationBoolObj);
            if( isForCompensation ) { 
                return;
            }
        }
        if( nodeInstance instanceof EventSubProcessNodeInstance ) {
             if (((org.jbpm.workflow.core.WorkflowProcess) getProcessInstance().getProcess()).isAutoComplete()) {
                if (nodeInstances.isEmpty()) {
                    // retrigger (compensation intermeidate throw event) action node..
                }
            }
        } else {
            throw new IllegalArgumentException("Completing a node instance that has no outgoing connection not supported.");
        }
    }

    // Node container logic

    private NodeContainer nodeContainer = null;
    
    private void setNodeContainer(NodeContainer nodeContainer) { 
        this.nodeContainer = nodeContainer;
    }
    
    @Override
    public NodeContainer getNodeContainer() {
        return nodeContainer;
    }

    // State logic
     
    private int state = ProcessInstance.STATE_ACTIVE;
            
    @Override
    public int getState() {
        return state;
    }

    @Override
    public void setState(int state) {
        this.state = state;
    }

    // Node level logic
    
    private Map<String, Integer> iterationLevels = new HashMap<String, Integer>();
    private int currentLevel = 0;
    
    @Override
    public int getLevelForNode(String uniqueID) {
        if ("true".equalsIgnoreCase(System.getProperty("jbpm.loop.level.disabled"))) {
            return 1;
        }
        Integer value = iterationLevels.get(uniqueID);
        if (value == null && currentLevel == 0) {
           value = new Integer(1);
        } else if ((value == null && currentLevel > 0) || (value != null && currentLevel > 0 && value > currentLevel)) {
            value = new Integer(currentLevel);
        } else {
            value++;
        }

        iterationLevels.put(uniqueID, value);
        return value;
    }

    @Override
    public int getCurrentLevel() {
        return currentLevel;
    }

    @Override
    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    public Map<String, Integer> getIterationLevels() {
        return iterationLevels;
    }

    // context instance container logic
   
    // OCRAM: data/variable snapshots?
    
    @Override
    public List<ContextInstance> getContextInstances(String contextId) {
        return getContextInstanceContainer().getContextInstances(contextId);
    }

    @Override
    public void addContextInstance(String contextId, ContextInstance contextInstance) {
        getContextInstanceContainer().addContextInstance(contextId, contextInstance);
    }

    @Override
    public void removeContextInstance(String contextId, ContextInstance contextInstance) {
        getContextInstanceContainer().removeContextInstance(contextId, contextInstance);
    }

    @Override
    public ContextInstance getContextInstance(String contextId, long id) {
        return getContextInstanceContainer().getContextInstance(contextId, id);
    }

    @Override
    public ContextInstance getContextInstance(Context context) {
        return getContextInstanceContainer().getContextInstance(context);
    }

    @Override
    public ContextContainer getContextContainer() {
        return compensationScope.getContextContainer();
    }

}
