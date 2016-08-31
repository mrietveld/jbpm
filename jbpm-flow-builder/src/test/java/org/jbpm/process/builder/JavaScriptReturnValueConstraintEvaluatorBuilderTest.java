package org.jbpm.process.builder;


import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.compiler.DialectCompiletimeRegistry;
import org.drools.compiler.compiler.ReturnValueDescr;
import org.drools.compiler.lang.descr.ProcessDescr;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.definitions.impl.KnowledgePackageImpl;
import org.jbpm.process.builder.dialect.javascript.JavaScriptReturnValueEvaluatorBuilder;
import org.jbpm.process.instance.impl.ReturnValueConstraintEvaluator;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.core.impl.WorkflowProcessImpl;
import org.jbpm.workflow.instance.node.SplitInstance;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.definition.KnowledgePackage;

public class JavaScriptReturnValueConstraintEvaluatorBuilderTest {
    @Test
    public void testSimpleReturnValueConstraintEvaluator() throws Exception {
        final InternalKnowledgePackage pkg = new KnowledgePackageImpl( "pkg1" );

        ProcessDescr processDescr = new ProcessDescr();
        processDescr.setClassName( "Process1" );
        processDescr.setName( "Process1" );

        WorkflowProcessImpl process = new WorkflowProcessImpl();
        process.setName( "Process1" );
        process.setPackageName("pkg1");

        ReturnValueDescr descr = new ReturnValueDescr();
        descr.setText("function validate() {return value;} validate();");

        KnowledgeBuilderImpl pkgBuilder = new KnowledgeBuilderImpl( pkg );
        DialectCompiletimeRegistry dialectRegistry = pkgBuilder.getPackageRegistry( pkg.getName() ).getDialectCompiletimeRegistry();

        ProcessBuildContext context = new ProcessBuildContext( pkgBuilder,
                pkg,
                process,
                processDescr,
                dialectRegistry,
                null );

        pkgBuilder.addPackageFromDrl(new StringReader("package pkg1;\n global Boolean value;\n"));

        ReturnValueConstraintEvaluator node = new ReturnValueConstraintEvaluator();

        final JavaScriptReturnValueEvaluatorBuilder builder = new JavaScriptReturnValueEvaluatorBuilder();
        builder.build(context,
                node,
                descr,
                null);

        final KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        List<KnowledgePackage> packages = new ArrayList<KnowledgePackage>();
        packages.add( pkgBuilder.getPackage() );
        kbase.addKnowledgePackages(packages);
        final KieSession ksession = kbase.newStatefulKnowledgeSession();


        RuleFlowProcessInstance processInstance = new RuleFlowProcessInstance();
        processInstance.setKnowledgeRuntime((InternalKnowledgeRuntime) ksession);

        SplitInstance splitInstance = new SplitInstance();
        splitInstance.setProcessInstance(processInstance);

        ksession.setGlobal("value", true);

        assertTrue(node.evaluate(splitInstance,
                        null,
                        null)
        );

        // Build second time with reutrn value evaulator returning false
        ReturnValueDescr descr2 = new ReturnValueDescr();
        descr.setText("function invalidate() {return false;} invalidate();");

        final JavaScriptReturnValueEvaluatorBuilder builder2 = new JavaScriptReturnValueEvaluatorBuilder();
        builder2.build(context,
                node,
                descr,
                null);

        assertFalse(node.evaluate(splitInstance,
                        null,
                        null)
        );
    }

}
