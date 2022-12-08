package com.uwaterloo.wala.workshop.utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ScopeUtil {

    public static AnalysisScope makeScope() throws IOException, ClassHierarchyException {
        ValidationUtils.validateDexParent();
        String dexPath = PropertyUtils.getPath();
        File dexDir = new File(dexPath);

        AnalysisScope scope = initScope();
        addDexFilesToScope(scope, dexDir);
        return scope;
    }

    public static AnalysisScope initScope() throws IOException {

        String rootDir = System.getProperty("user.dir");
        String android_path = String.format("%s/lib/android.java.jar", rootDir);
        String android_nonjava_path = String.format("%s/lib/android.nonJava.jar", rootDir);
        String exclusion_path = String.format("%s/dat/AndroidRegressionExclusions.txt", rootDir);

        AnalysisScope scope = AnalysisScopeReader.instance.readJavaScope("primordial.txt", new File(exclusion_path), AnalysisScopeReader.class.getClassLoader());
        scope.addInputStreamForJarToScope(ClassLoaderReference.Primordial, new FileInputStream(android_path));
        scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
        scope.addInputStreamForJarToScope(ClassLoaderReference.Application, new FileInputStream(android_nonjava_path));

        return scope;
    }

    public static void addDexFilesToScope(AnalysisScope scope, File dexDir) throws ClassHierarchyException, IOException {

        String rootDir = System.getProperty("user.dir");
        String android_path = String.format("%s/lib/android.java.jar", rootDir);
        String exclusion_path = String.format("%s/dat/AndroidRegressionExclusions.txt", rootDir);

        ClassHierarchy cha = ClassHierarchyFactory.make(scope);

        List<Path> dexPaths = FileUtils.retrievePathsMatchingExtFromParent(dexDir.getAbsolutePath(), ".dex");
        for(Path p : dexPaths) {
            if(p.getFileName().toString().toLowerCase().contains("framework") || p.getFileName().toString().toLowerCase().contains("service")) {
                System.out.println("ADDING " + p.toString());
                DexFileModule dexModule = DexFileModule.make(new File(p.toString()));

                AnalysisScope appScope = AnalysisScopeReader.instance.readJavaScope("primordial.txt", new File(exclusion_path), AnalysisScopeReader.class.getClassLoader());
                appScope.addInputStreamForJarToScope(ClassLoaderReference.Primordial, new FileInputStream(android_path));
                appScope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
                appScope.addToScope(ClassLoaderReference.Application, dexModule);
                ClassHierarchy appCha = ClassHierarchyFactory.make(appScope);

                IClassLoader appLoader = appCha.getFactory().getLoader(ClassLoaderReference.Application, appCha, appScope);
                Set<IClass> toRemove = new HashSet<>();
                Iterator<IClass> appIter = appLoader.iterateAllClasses();
                while (appIter.hasNext()) {
                    IClass k = appIter.next();
                    toRemove.add(k);
                }

                IClassLoader oriLoader = cha.getFactory().getLoader(ClassLoaderReference.Application, cha, scope);
                oriLoader.removeAll(toRemove);

                scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
                scope.addToScope(ClassLoaderReference.Application, dexModule);
                ArrayList<Module> list = new ArrayList<>();
                list.add(dexModule);
                oriLoader.init(list);
                cha = ClassHierarchyFactory.make(scope, cha.getFactory());

                appScope = null;
                appCha = null;
                appLoader = null;
                appCha = null;

            }
        }
    }
}
