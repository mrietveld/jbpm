package org.jbpm.services.task.impl.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.jbpm.services.task.impl.model.TaskImpl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.io.OutputChunked;

public class KryoUtil {

    private KryoUtil() {
        // the Kryo instance is *not* thread-safe
        // and we will also be using it situations where we have multiple (project) classloaders
        // there's no reason to cache a Kryo instance, as of yet..
    }

    public static byte[] serialize( Object object, ClassLoader classLoader ) {
        Kryo kryo = new Kryo();
        kryo.setClassLoader(classLoader);

        return serialize(object, kryo);
    }

    public static byte[] serialize( Object object ) {
        Kryo kryo = new Kryo();

        return serialize(object, kryo);
    }

    private static byte[] serialize( Object object, Kryo kryo ) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Output output = new Output(outputStream);
        kryo.writeClassAndObject(output, object);
        output.flush();

        return outputStream.toByteArray();
    }

    public static Object deserialize( byte[] byteArray, ClassLoader classLoader ) {
        Kryo kryo = new Kryo();
        kryo.setClassLoader(classLoader);

        return deserialize(byteArray, kryo);
    }

    public static Object deserialize( byte[] byteArray ) {
        Kryo kryo = new Kryo();

        return deserialize(byteArray, kryo);
    }

    private static Object deserialize( byte[] byteArray, Kryo kryo ) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);
        Input input = new Input(inputStream);
        return kryo.readClassAndObject(input);
    }

}
