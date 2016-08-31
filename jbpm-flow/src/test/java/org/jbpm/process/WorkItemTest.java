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

package org.jbpm.process;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.drools.core.WorkItemHandlerNotFoundException;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.process.core.ParameterDefinition;
import org.drools.core.process.core.Work;
import org.drools.core.process.core.datatype.impl.type.IntegerDataType;
import org.drools.core.process.core.datatype.impl.type.ObjectDataType;
import org.drools.core.process.core.datatype.impl.type.StringDataType;
import org.drools.core.process.core.impl.ParameterDefinitionImpl;
import org.drools.core.process.core.impl.WorkImpl;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.impl.demo.DoNothingWorkItemHandler;
import org.jbpm.process.test.Person;
import org.jbpm.process.test.TestProcessEventListener;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.test.util.AbstractBaseTest;
import org.jbpm.workflow.core.Node;
import org.jbpm.workflow.core.impl.ConnectionImpl;
import org.jbpm.workflow.core.node.EndNode;
import org.jbpm.workflow.core.node.StartNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.EventListener;
import org.kie.api.runtime.process.ProcessInstance;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class WorkItemTest extends AbstractBaseTest {

    public void addLogger() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @Parameters(name="{0}")
    public static Collection<Object[]> useStack() {
        Object[][] execModelType = new Object[][] {
                { OLD_RECURSIVE_STACK },
                { QUEUE_BASED_EXECUTION }
                };
        return Arrays.asList(execModelType);
    };

    public WorkItemTest(String execModel) {
        this.stacklessExecution = QUEUE_BASED_EXECUTION.equals(execModel);
    }

    @Rule
    public TestName testName = new TestName();

    @Before
    public void printTestName() {
       logger.info( ">> " + testName.getMethodName() );
    }

	@Test
    public void testReachNonRegisteredWorkItemHandler() {
        String processId = "org.drools.actions";
        String workName = "Unnexistent Task";
        RuleFlowProcess process = getWorkItemProcess( processId,
                                                      workName );
        KieSession ksession = createKieSession(process);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put( "UserName",
                        "John Doe" );
        parameters.put( "Person",
                        new Person( "John Doe" ) );

        // Register listener in order to test if signals are sent..
        ProcessInstance processInstance = ksession.createProcessInstance("org.drools.actions", parameters );
        String completedEvent = "processInstanceCompleted:" + processInstance.getId();

        InternalKnowledgeRuntime kruntime = ((org.jbpm.process.instance.ProcessInstance) processInstance).getKnowledgeRuntime();
        InternalProcessRuntime processRuntime = (InternalProcessRuntime) kruntime.getProcessRuntime();

        final AtomicBoolean eventSignalled = new AtomicBoolean(false);;
        EventListener listener = new EventListener() {

            @Override
            public void signalEvent(String type, Object event) {
                eventSignalled.set(true);
            }

            @Override
            public String[] getEventTypes() {
                return new String [] { completedEvent };
            }
        };
        processRuntime.getSignalManager().addEventListener(completedEvent, listener);

        // Start process instance, and then verify stuff
        try {
            processInstance = ksession.startProcessInstance(processInstance.getId());
            Assert.fail( "should fail if WorkItemHandler for" + workName + "is not registered" );
        } catch ( Throwable e ) {
            assertTrue( "Incorrect exception msg: " + e.getMessage(),
                    e.getMessage().contains("Could not find work item handler for " + workName));
        }
        assertNotEquals("Unexpected process state: " + processInstance.getState(),
                ProcessInstance.STATE_COMPLETED, processInstance.getState());

        assertTrue("Process instance completed signal was not sent!", eventSignalled.get());
    }

	@Test
    public void testCancelNonRegisteredWorkItemHandler() {
        String processId = "org.drools.actions";
        String workName = "Unnexistent Task";
        RuleFlowProcess process = getWorkItemProcess( processId,
                                                      workName );
        KieSession ksession = createKieSession(process);
        TestProcessEventListener procEventListener = new TestProcessEventListener();
        ksession.addEventListener(procEventListener);

        ksession.getWorkItemManager().registerWorkItemHandler( workName,
                                                               new DoNothingWorkItemHandler() );

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put( "UserName",
                        "John Doe" );
        parameters.put( "Person",
                        new Person( "John Doe" ) );

        ProcessInstance processInstance = ksession.startProcess( "org.drools.actions",
                                                                  parameters );
        long processInstanceId = processInstance.getId();
        Assert.assertEquals( ProcessInstance.STATE_ACTIVE,
                           processInstance.getState() );
        ksession.getWorkItemManager().registerWorkItemHandler( workName,
                                                               null );

        try {
            ksession.abortProcessInstance( processInstanceId );
            Assert.fail( "should fail if WorkItemHandler for" + workName + "is not registered" );
        } catch ( WorkItemHandlerNotFoundException wihnfe ) {
            assertTrue( "Incorrect exception msg: " + wihnfe.getMessage(),
                    wihnfe.getMessage().contains("Could not find work item handler for " + workName));
        }

        Assert.assertEquals( ProcessInstance.STATE_ABORTED,
                             processInstance.getState() );
    }

    private RuleFlowProcess getWorkItemProcess(String processId,
                                               String workName) {
        RuleFlowProcess process = new RuleFlowProcess();
        process.setId( processId );

        List<Variable> variables = new ArrayList<Variable>();
        Variable variable = new Variable();
        variable.setName( "UserName" );
        variable.setType( new StringDataType() );
        variables.add( variable );
        variable = new Variable();
        variable.setName( "Person" );
        variable.setType( new ObjectDataType( Person.class.getName() ) );
        variables.add( variable );
        variable = new Variable();
        variable.setName( "MyObject" );
        variable.setType( new ObjectDataType() );
        variables.add( variable );
        variable = new Variable();
        variable.setName( "Number" );
        variable.setType( new IntegerDataType() );
        variables.add( variable );
        process.getVariableScope().setVariables( variables );

        StartNode startNode = new StartNode();
        startNode.setName( "Start" );
        startNode.setId( 1 );

        WorkItemNode workItemNode = new WorkItemNode();
        workItemNode.setName( "workItemNode" );
        workItemNode.setId( 2 );
        workItemNode.addInMapping( "Comment",
                                   "Person.name" );
        workItemNode.addInMapping( "Attachment",
                                   "MyObject" );
        workItemNode.addOutMapping( "Result",
                                    "MyObject" );
        workItemNode.addOutMapping( "Result.length()",
                                    "Number" );
        Work work = new WorkImpl();
        work.setName( workName );
        Set<ParameterDefinition> parameterDefinitions = new HashSet<ParameterDefinition>();
        ParameterDefinition parameterDefinition = new ParameterDefinitionImpl( "ActorId",
                                                                               new StringDataType() );
        parameterDefinitions.add( parameterDefinition );
        parameterDefinition = new ParameterDefinitionImpl( "Content",
                                                           new StringDataType() );
        parameterDefinitions.add( parameterDefinition );
        parameterDefinition = new ParameterDefinitionImpl( "Comment",
                                                           new StringDataType() );
        parameterDefinitions.add( parameterDefinition );
        work.setParameterDefinitions( parameterDefinitions );
        work.setParameter( "ActorId",
                           "#{UserName}" );
        work.setParameter( "Content",
                           "#{Person.name}" );
        workItemNode.setWork( work );

        EndNode endNode = new EndNode();
        endNode.setName( "End" );
        endNode.setId( 3 );

        connect( startNode,
                 workItemNode );
        connect( workItemNode,
                 endNode );

        process.addNode( startNode );
        process.addNode( workItemNode );
        process.addNode( endNode );

        return process;
    }

    private void connect(Node sourceNode,
                         Node targetNode) {
        new ConnectionImpl( sourceNode,
                             Node.CONNECTION_DEFAULT_TYPE,
                             targetNode,
                             Node.CONNECTION_DEFAULT_TYPE );
    }

}
