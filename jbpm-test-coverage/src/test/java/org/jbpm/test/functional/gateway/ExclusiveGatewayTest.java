/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.test.functional.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.drools.core.command.runtime.process.StartProcessCommand;
import org.jbpm.process.test.TestProcessEventListener;
import org.jbpm.test.JbpmTestCoverageTestCase;
import org.jbpm.test.ParameterizedPlusQueueBased.ExecutionType;
import org.jbpm.test.listener.IterableProcessEventListener;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.runtime.KieSession;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.jbpm.test.tools.IterableListenerAssert.*;

/**
 * Exclusive gateway test. priorities, default gate, conditions (XPath, Java, MVEL)
 */
@RunWith(Parameterized.class)
public class ExclusiveGatewayTest extends JbpmTestCoverageTestCase {

    private static final String EXCLUSIVE_GATEWAY = "org/jbpm/test/functional/gateway/ExclusiveGateway.bpmn";
    private static final String EXCLUSIVE_GATEWAY_ID = "org.jbpm.test.functional.gateway.ExclusiveGateway";

    private KieSession ksession;
    private IterableProcessEventListener iterableListener;


    @Parameters(name="{0}")
    public static Collection<Object[]> parameters() {
        return new ArrayList<Object[]>() { {
                add(new Object[] { ExecutionType.RECURSIVE });
                add(new Object[] { ExecutionType.QUEUE_BASED });
            }
        };
    };

    public ExclusiveGatewayTest(ExecutionType executionType) {
        super(false);
        this.queueBasedExecution = executionType.equals(ExecutionType.QUEUE_BASED);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        ksession = createKSession(EXCLUSIVE_GATEWAY);
        iterableListener = new IterableProcessEventListener();
    }

    /**
     * Exclusive gateway test; only one gate has condition expression == true.
     * 10 > "5" > 1 => second gate should be taken
     */
    @Test
    //(timeout = 30000)
    public void testExclusive1() {
        Assume.assumeFalse(EXCLUSIVE_GATEWAY_ID.contains("ExclusiveGateway-eclipse"));
        ksession.addEventListener(iterableListener);
        ksession.addEventListener(new TestProcessEventListener());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 5);
        Element el = createTestElement("sample", "value", "test");
        params.put("element", el);
        StartProcessCommand spc = new StartProcessCommand();
        spc.setProcessId(EXCLUSIVE_GATEWAY_ID);
        spc.setParameters(params);
        ksession.execute(spc);

        assertMultipleVariablesChanged(iterableListener, "element", "x");

        assertProcessStarted(iterableListener, EXCLUSIVE_GATEWAY_ID);
        assertNextNode(iterableListener, "start");
        assertNextNode(iterableListener, "insertScript");
        assertNextNode(iterableListener, "fork1");
        assertNextNode(iterableListener, "script2");
        assertNextNode(iterableListener, "join");
        assertNextNode(iterableListener, "fork2");
        assertNextNode(iterableListener, "end1");
        assertProcessCompleted(iterableListener, EXCLUSIVE_GATEWAY_ID);
    }

    /**
     * Exclusive gateway test; two gates have condition expression == true, lower priority number is chosen.
     * "15" > 10 > 1 => gate is chosen according to priority
     */
    @Test(timeout = 30000)
    public void testExclusive2() {
        Assume.assumeFalse(EXCLUSIVE_GATEWAY_ID.contains("ExclusiveGateway-eclipse"));
        ksession.addEventListener(iterableListener);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 15);
        Element el = createTestElement("sample", "value", "test");
        params.put("element", el);
        StartProcessCommand spc = new StartProcessCommand();
        spc.setProcessId(EXCLUSIVE_GATEWAY_ID);
        spc.setParameters(params);
        ksession.execute(spc);

        assertMultipleVariablesChanged(iterableListener, "element", "x");
        assertProcessStarted(iterableListener, EXCLUSIVE_GATEWAY_ID);
        assertNextNode(iterableListener, "start");
        assertNextNode(iterableListener, "insertScript");
        assertNextNode(iterableListener, "fork1");
        assertNextNode(iterableListener, "script1");
        assertNextNode(iterableListener, "join");
        assertNextNode(iterableListener, "fork2");
        assertNextNode(iterableListener, "end1");
        assertProcessCompleted(iterableListener, EXCLUSIVE_GATEWAY_ID);
    }

    /**
     * Exclusive gateway test; no condition is satisfied, default gate should be taken.
     */
    @Test(timeout = 30000)
    public void testExclusive3() {
        Assume.assumeFalse(EXCLUSIVE_GATEWAY_ID.contains("ExclusiveGateway-eclipse"));
        ksession.addEventListener(iterableListener);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", -1);
        Element el = createTestElement("sample", "value", "test");
        params.put("element", el);
        StartProcessCommand spc = new StartProcessCommand();
        spc.setProcessId(EXCLUSIVE_GATEWAY_ID);
        spc.setParameters(params);
        ksession.execute(spc);

        assertMultipleVariablesChanged(iterableListener, "element", "x");
        assertProcessStarted(iterableListener, EXCLUSIVE_GATEWAY_ID);
        assertNextNode(iterableListener, "start");
        assertNextNode(iterableListener, "insertScript");
        assertNextNode(iterableListener, "fork1");
        assertNextNode(iterableListener, "script3");
        assertNextNode(iterableListener, "join");
        assertNextNode(iterableListener, "fork2");
        assertNextNode(iterableListener, "end1");
        assertProcessCompleted(iterableListener, EXCLUSIVE_GATEWAY_ID);
    }

    /**
     * Creates testing element with attribute.
     *
     * @param name      name
     * @param attribute attribute name
     * @param attrValue attribute value
     */
    private Element createTestElement(String name, String attribute, String attrValue) {
        Document doc;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }

        Attr attr = doc.createAttribute(attribute);
        attr.setValue(attrValue);

        Element element = doc.createElement(name);
        element.setAttributeNode(attr);

        return element;
    }

}
