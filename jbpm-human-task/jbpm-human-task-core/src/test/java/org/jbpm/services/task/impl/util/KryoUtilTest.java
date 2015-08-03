package org.jbpm.services.task.impl.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import org.jbpm.services.task.impl.model.TaskDataImpl;
import org.jbpm.services.task.impl.model.TaskImpl;
import org.junit.Test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class KryoUtilTest {

    @Test
    public void kryoDevTest() throws IOException { 
        Kryo kryo = new Kryo();
         
        TaskImpl task = new TaskImpl();
        task.setId(2);
        task.setName("asdf");
        TaskDataImpl taskData = new TaskDataImpl();
        taskData.setActivationTime(new Date());
        task.setTaskData(taskData);
         
//        registerAllClasses(kryo, task);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Output output = new Output(outputStream);
        kryo.writeClassAndObject(output, task);
        output.flush();

        byte [] byteArr = outputStream.toByteArray();
        assertNotNull( "null byte array", byteArr );
        assertTrue( "empty byte array", byteArr.length > 0 );
        
        Input input = new Input(byteArr);
        Object revivedTaskObj = kryo.readClassAndObject(input);
        TaskImpl revivedTask = (TaskImpl) revivedTaskObj;

    }
    
}