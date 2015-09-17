package org.jbpm.test.persistence;

import java.io.File;
import java.io.InputStream;
import java.net.URLClassLoader;

import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class AlternateORMClassloaderTestRunner extends BlockJUnit4ClassRunner {

    public AlternateORMClassloaderTestRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    public void run( RunNotifier notifier ) {
        ClassLoader origClassLoader = Thread.currentThread().getContextClassLoader();

        File [] hibDepFiles = Maven.resolver()
                .loadPomFromFile("target/test-classes/alternate-hibernate-pom.xml")
                .importCompileAndRuntimeDependencies()
                .resolve()
                .withTransitivity()
                .as(File.class);

        Asset [] hipDepAssets = new Asset[hibDepFiles.length];
        for( int i = 0; i < hibDepFiles.length; ++i ) {
           hipDepAssets[i] = convertJarFileToAsset(hibDepFiles[i], origClassLoader);
        }
        ShrinkWrap.create(GenericArchive.class);

        Thread.currentThread().setContextClassLoader(null);
        super.run(notifier);
    }

    private Asset convertJarFileToAsset(File resource, ClassLoader classLoader) {
        String jarFilePath = resourceAdjustedPath(resource);
        final InputStream in = classLoader.getResourceAsStream(jarFilePath);
        if (in != null) {
            return new ByteArrayAsset(in);
        } else {
            throw new IllegalStateException("Unable to get input stream for " + jarFilePath);
        }
    }
    /**
     * Stolen from the ShrinkWrap ContainerBase classs
     */

    private String resourceAdjustedPath(final File resource) {
        final String path = resource.getPath();
        final String adjustedPath = path.substring(path.indexOf("!" + File.separator) + 2, path.length());
        return adjustedPath.replace(File.separator, "/");
    }

    private static Class<?> getFromTestClassloader(Class<?> clazz) throws InitializationError {
        try {
            ClassLoader testClassLoader = null;
            return Class.forName(clazz.getName(), true, testClassLoader);
        } catch (ClassNotFoundException e) {
            throw new InitializationError(e);
        }
    }

    public static class TestClassLoader extends URLClassLoader {
        public TestClassLoader() {
            super(((URLClassLoader)getSystemClassLoader()).getURLs());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("org.mypackages.")) {
                return super.findClass(name);
            }
            return super.loadClass(name);
        }
    }
}
