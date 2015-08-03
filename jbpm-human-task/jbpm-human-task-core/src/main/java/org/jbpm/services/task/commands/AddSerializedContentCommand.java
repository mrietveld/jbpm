/*
 * Copyright 2012 JBoss by Red Hat.
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
package org.jbpm.services.task.commands;

import java.io.IOException;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;

import org.jbpm.services.task.impl.TaskContentRegistry;
import org.jbpm.services.task.impl.util.KryoUtil;
import org.jbpm.services.task.impl.util.SerializableUtil;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.kie.api.task.model.Content;
import org.kie.internal.command.Context;
import org.kie.internal.task.api.ContentMarshallerContext;
import org.kie.internal.task.api.TaskModelProvider;
import org.kie.internal.task.api.model.ContentData;
import org.kie.internal.task.api.model.InternalContent;


@XmlRootElement(name="add-content-command")
@XmlAccessorType(XmlAccessType.NONE)
public class AddSerializedContentCommand extends TaskCommand<Long> {

	private static final long serialVersionUID = -1295175858745522756L;

	@XmlElement
	private String deploymentId;
	
	@XmlElement
    @XmlSchemaType(name="base64Binary")
    private byte[] kryoContent = null;

	@XmlElement
    @XmlSchemaType(name="base64Binary")
    private byte[] serializedContent = null;

	@XmlElement
    @XmlSchemaType(name="base64Binary")
    private byte[] byteContent = null;

    public AddSerializedContentCommand() {
    }

    public AddSerializedContentCommand(Long taskId) {
    	this.taskId = taskId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId( String deploymentId ) {
        this.deploymentId = deploymentId;
    }

    public byte[] getKryoContent() {
        return kryoContent;
    }

    public void setKryoContent( byte[] kryoContent ) {
        this.kryoContent = kryoContent;
    }

    public byte[] getSerializedContent() {
        return serializedContent;
    }

    public void setSerializedContent( byte[] serializedContent ) {
        this.serializedContent = serializedContent;
    }

    public byte[] getByteContent() {
        return byteContent;
    }

    public void setByteContent( byte[] byteContent ) {
        this.byteContent = byteContent;
    }

    public Long execute(Context cntxt) {
        TaskContext context = (TaskContext) cntxt;
    
        assert ( kryoContent != null && serializedContent == null && byteContent == null ) ||  
            ( kryoContent == null && serializedContent != null && byteContent == null ) ||  
            ( kryoContent == null && serializedContent == null && byteContent != null );
   
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if( this.deploymentId != null ) { 
            classLoader = TaskContentRegistry.get().getMarshallerContext(deploymentId).getClassloader();
        }
       
        Object contentObject = null;
        if( kryoContent != null ) { 
           contentObject = KryoUtil.deserialize(kryoContent, classLoader);
        } else if( serializedContent != null ) { 
            try { 
                contentObject = SerializableUtil.deserialize(serializedContent, classLoader);
            } catch( IOException ioe ) { 
               throw new RuntimeException("Unable to deserialize byte array of Serializable instance", ioe); 
            } catch( ClassNotFoundException cnfe ) { 
               throw new RuntimeException("Unable to deserialize byte array of Serializable instance", cnfe); 
            }
        } else { 
            contentObject = byteContent;
        }
      
        
        boolean stringObjectMap = true;
        if( contentObject instanceof Map ) { 
          for( Object key : ((Map) contentObject).keySet() ) { 
            if( !(key instanceof String) ) { 
               stringObjectMap = false;
               break;
            }
          }
        }
           
        if (stringObjectMap) {
        	return context.getTaskContentService().addContentFromUser(taskId, userId,(Map<String,Object>) contentObject);
        } else {        
            ContentMarshallerContext mContext = TaskContentRegistry.get().getMarshallerContext(deploymentId);
            ContentData outputContentData = ContentMarshallerHelper.marshal(contentObject, mContext.getEnvironment());
            Content content = TaskModelProvider.getFactory().newContent();
            ((InternalContent) content).setContent(outputContentData.getContent());
            
        	return context.getTaskContentService().addContentFromUser(taskId, userId, content);
        }
    }

}
