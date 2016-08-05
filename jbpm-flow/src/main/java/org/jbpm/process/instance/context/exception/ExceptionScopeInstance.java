/**
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.process.instance.context.exception;

import org.jbpm.process.core.context.exception.ExceptionHandler;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.process.instance.ProcessImplementationPart;
import org.jbpm.process.instance.context.AbstractContextInstance;

public abstract class ExceptionScopeInstance extends AbstractContextInstance implements ProcessImplementationPart {

    private static final long serialVersionUID = 510l;

    public String getContextType() {
        return ExceptionScope.EXCEPTION_SCOPE;
    }
    
    public ExceptionScope getExceptionScope() {
        return (ExceptionScope) getContext();
    }
    
    public void handleException(String exception, Object params) {
        ExceptionHandler handler = getExceptionScope().getExceptionHandler(exception);
        if (handler == null) {
            throw new IllegalArgumentException("Could not find ExceptionHandler for " + exception);
        }
        if( isQueueBased() ) {
            getProcessInstance().addNewExecutionQueueToStack(false);
        }
        handleException(handler, exception, params);
    }
    
    public abstract void handleException(ExceptionHandler handler, String exception, Object params);

}
