package com.uwaterloo.wala.workshop.analyzers;

import java.util.*;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.uwaterloo.wala.workshop.utils.ScopeUtil;

import java.io.FileWriter;




public class FrameworkAnalyzer {
    public static void launch() throws Exception {
        AnalysisScope scope = ScopeUtil.makeScope();
        /*
         * Start the framework analysis from here
         */

        
        // Making scope here
        ClassHierarchy cha = ClassHierarchyFactory.make(scope);
        AnalysisCacheImpl cache = new AnalysisCacheImpl(new DexIRFactory());

        ArrayList<DefaultEntrypoint> entrypoints = new ArrayList<>();

        System.out.println("Starting.....");
        for (IClass c : cha){

//             if(!c.getName().toString().contains("BluetoothService")) continue;

            entrypoints = new ArrayList<>();
            IClass binderClass = null;
            IClass binderSuperClass = null; 
            IClass serviceClass = null;
            ArrayList<String> apiNames = new ArrayList<>();
            ArrayList<String> interfaceMethods = new ArrayList<>();


            // System.out.println("Class: " + c.getName());
            try{
                // Taking out all the entrypoints that have publishBinderService/ addService where binder classes are being registered
                for(IMethod m : c.getAllMethods()){
                    IBytecodeMethod method = null;

                    try{
                        method = (IBytecodeMethod)m;
                    } catch (Exception e){
//                        System.out.println("HERREE 100");

                        continue;
                    }

//                    System.out.println("HERREE 1");

//                    System.out.println("METHODDDDD " + method.toString());
                    if(method == null){
//                        System.out.println("METHODDD ISSSS NULLL");
                    }else{
//                        System.out.println("METHODDD ISSSS NOTTTT NULLL");

                    }


                    if(method.getName().toString().equals("getPluginsPath")){
                        continue;
                    }
                    if(method.getInstructions() == null){
//                        System.out.println("METHODDD GET INSTRUCTION IS NULL");
                    }else{
//                        System.out.println("METHODDD GET INSTRUCTION IS NOTTT NULL");
//                        System.out.println("PROOOOF " + method.getInstructions().toString());

                    }
                    if(method!=null && method.getInstructions()!=null){
//                        System.out.println("HERREE 2");
                        for(Object instruct : method.getInstructions()){
//                            System.out.println("HERREE 3");
                            if(instruct.toString().contains("addService") || instruct.toString().contains("publishBinderService")){
                                System.out.println("--------------------------------------------------------");
                                System.out.println("Class: " + c.getName().toString());
                                System.out.println("Method: " + m.getName().toString());
                                System.out.println("Instruction: " + instruct.toString());

                                DefaultEntrypoint de = new DefaultEntrypoint(m, cha);
                                entrypoints.add(de);
                            }
                        }
                    }
                }
            }
            catch (Exception e){
//                System.out.println("ERROR HERE: " + e.toString());
            }
//            System.out.println("ENTRYPOINTS COLLECTED ");

            if(entrypoints.size() < 1) continue;

            // An array for the APIs that are extracted
            ArrayList<DefaultEntrypoint> apis = new ArrayList<>();

            // Making a call graph of all the entry points to find services
            // System.out.println("Building call graph...");
            AnalysisOptions options = new AnalysisOptions();
            options.setAnalysisScope(scope);
            options.setEntrypoints(entrypoints);
            options.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);
            CallGraphBuilder cgb = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, cache, cha);
            CallGraph cg = null;
            try{
                cg = cgb.makeCallGraph(options, null);
            }catch(Exception e){
                continue;
            }
    
             System.out.println("Call Graph Made");
    
    
            for(CGNode node : cg){
                if(node.getIR() == null){
                    continue;
                }
    
                IR ir = node.getIR();

                // Going to through instructions to find the target method --> publishBinderService/addService
                for(Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();){
                    SSAInstruction s = it.next();

                    // Getting the specific call to addServie/publishBinderService
                    if (s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction){
                        com.ibm.wala.ssa.SSAAbstractInvokeInstruction call = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction) s;
                        if(call.getCallSite().getDeclaredTarget().getName().toString().equals("addService") 
                        || call.getCallSite().getDeclaredTarget().getName().toString().equals("publishBinderService")){

                            int paraindex = 1;

                            try{
                                int paramValueNumber = ((SSAAbstractInvokeInstruction) s).getUse(paraindex);
                                SymbolTable st = node.getIR().getSymbolTable();
                                String permission = st.getStringValue(paramValueNumber);
                                System.out.println("Service String: " + permission.toString());
                            }catch(Exception e){
                                // do nothing
                            }

                            //if not static then the 1st paramter will be this.
                            if(call.toString().contains("invokevirtual")){
                                paraindex++;
                            }

                            DefUse du = node.getDU();

                            if(du == null){
                                continue;
                            }
                            SSAInstruction second = null;
                            
                            try{
                                second = du.getDef(call.getUse(paraindex));
                            }catch(Exception e){
                                second = null;
                            }


                            if(second == null){
                                continue;
                            }
                            // System.out.println("SECOND: "+ second);
                            String key = null;
                            String value = null;
                            
                            if(second instanceof com.ibm.wala.ssa.SSAGetInstruction){
                                com.ibm.wala.ssa.SSAGetInstruction secondcall = (com.ibm.wala.ssa.SSAGetInstruction) second;
//                                 System.out.println("HEREEE");
                                if(secondcall.getDeclaredFieldType().getName().toString().equals("Landroid/os/IBinder")){
//                                     System.out.println("HIIII");

                                    serviceClass = node.getMethod().getDeclaringClass();
                                    String servicename = serviceClass.getName().toString();

                                    IClassHierarchy iCH = serviceClass.getClassHierarchy();
                                    Iterator<IClass> iterator = iCH.iterator();

                                    while(iterator.hasNext()){
                                        IClass cls = iterator.next();
                                        if(cls.getName().toString().contains(servicename) && cls.getSuperclass().getName().toString().contains("Stub")){
                                            String binderName = cls.getName().toString();
                                            key = binderName;
                                            value = servicename;
                                        }
                                    }
                                }
                                else{
                                    // System.out.println(secondcall.getDeclaredFieldType().getName().toString());
                                    // System.out.println(node.getMethod().getDeclaringClass().getName().toString());
                                    key = secondcall.getDeclaredFieldType().getName().toString();
                                    value = node.getMethod().getDeclaringClass().getName().toString();
                                }
                            }
                            else if(second instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction){
                                com.ibm.wala.ssa.SSAAbstractInvokeInstruction secondcall = (com.ibm.wala.ssa.SSAAbstractInvokeInstruction)second;
                                key = secondcall.getCallSite().getDeclaredTarget().getDeclaringClass().getName().toString();
                                value = node.getMethod().getDeclaringClass().getName().toString();
                            }
                            else if(second instanceof com.ibm.wala.ssa.SSANewInstruction){
                                com.ibm.wala.ssa.SSANewInstruction secondcall = (com.ibm.wala.ssa.SSANewInstruction)second;
                                key = secondcall.getConcreteType().getName().toString();
                                value = node.getMethod().getDeclaringClass().getName().toString();

                            }

                          

                            for (IClass c1 : cha){
                                if(c1.getName().toString().equals(key)){
                                    binderClass = c1;
                                    binderSuperClass = c1.getSuperclass();
                                    System.out.println("Binder Class: "+ binderClass.getName().toString());
                                    System.out.println("Binder Superclass: " + binderClass.getSuperclass().getName().toString());
                                    // Collection<IClass> interfaces = binderClass.getAllImplementedInterfaces();

                                    // for(IClass interf : interfaces){
                                    //     System.out.println("Interface?:" + interf.toString());
                                    // }

                                }

                                if(c1.getName().toString().equals(value)){
                                    serviceClass = c1;
                                    System.out.println("Service Class: " + serviceClass.getName().toString());
                                    System.out.println("Service SuperClass: " + serviceClass.getSuperclass().getName().toString());
                                }

                                if(binderClass!=null && serviceClass!=null){
                                    break;
                                }
                            }   

                            if(binderClass != null){    
                                for(IMethod m : binderClass.getDeclaredMethods() ){
                                    if(m.isPublic()){
//                                         System.out.println("API Found: " + m.getName().toString());
                                        DefaultEntrypoint de1 = new DefaultEntrypoint(m, cha);
                                        apis.add(de1);
                                        apiNames.add(m.getName().toString());
                                    }
                                }
                            }
                        }
    
                    }
                }
    
       
            }

            // Continue if class has no methods
            if(apiNames.size() < 1){continue;}

            // Collecting binderClass stub methods
            if(binderSuperClass.toString().contains("<")){
//                System.out.println("IN HEREEEEE");

                interfaceMethods = getClassStringMethods(binderSuperClass.toString().split(",")[1].split(">")[0], cha);
            }else{
                interfaceMethods = getClassStringMethods(binderSuperClass.getName().toString(), cha);
            }

//            for(String kk : interfaceMethods){
//                System.out.println("INTERFACE METHODS: " + kk);
//            }

            // Eliminating apis which are not in Stub
            apiNames.retainAll(interfaceMethods);


            // Adding API entrypoints
            ArrayList<DefaultEntrypoint> apientrypoints = new ArrayList<>();

            for(IMethod currMethod : binderClass.getAllMethods()){
               if(apiNames.contains(currMethod.getName().toString())){
                   apientrypoints.add(new DefaultEntrypoint(currMethod, cha));
               }
            }

            if(apientrypoints.size()<1){continue;}

            AnalysisOptions options1 = new AnalysisOptions();
            options1.setAnalysisScope(scope);
            options1.setEntrypoints(apientrypoints);
            options1.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);
            CallGraphBuilder cgb1 = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options1, cache, cha);
            CallGraph cg1 = cgb1.makeCallGraph(options1, null);




            for(CGNode node : cg1){
                if(apiNames.contains(node.getMethod().getName().toString())){
                    if(node.getMethod().getName().toString().contains("init")){continue;}

                    if(!(binderClass.getName().toString().equals(node.getMethod().getDeclaringClass().getName().toString()))){continue;}

                    Set<String> apiMethodsInvoked = new HashSet<String>();

                    if(node.getMethod().getName().toString().contains("ShareTargets")){
                        continue;
                    }

                    System.out.println("********************");


                    System.out.println("API Name: " + node.getMethod().getName().toString());





                    System.out.println("No of Parameters: " + node.getMethod().getNumberOfParameters());
                    ArrayList<String> params = new ArrayList<>();

                    for(int i =0; i < node.getMethod().getNumberOfParameters(); i++){
                        // System.out.println("Type of Parameters: " + node.getMethod().getParameterType(i));
                        params.add(node.getMethod().getParameterType(i).toString());
                    }
                    if(params.size() >= 1){
                        System.out.println("Parameter Types: " + params.toString());
                    }
                    if(node == null){
                        continue;
                    }
                    if(node.getIR() == null){
                        continue;
                    }
                    ArrayList<String> permissions = new ArrayList<>();

                    for(Iterator<SSAInstruction> it = node.getIR().iterateAllInstructions(); it.hasNext();){
                        SSAInstruction s = it.next();
                        if(s instanceof com.ibm.wala.ssa.SSAAbstractInvokeInstruction){
                            apiMethodsInvoked.add(((SSAAbstractInvokeInstruction) s).getCallSite().getDeclaredTarget().getName().toString());

                            if(s.toString().contains("enforceCallingOrSelfPermission")){
                                // System.out.println("hereeeeeee");
                                int paramIndex =0;

                                if(((SSAAbstractInvokeInstruction)s).toString().contains("invokevirtual")){
                                    paramIndex ++;
                                }

                                int paramValueNumber = ((SSAAbstractInvokeInstruction) s).getUse(paramIndex);
                                SymbolTable st = node.getIR().getSymbolTable();
                                String permission = st.getStringValue(paramValueNumber);
                                // System.out.println("Permission: " + permission.toString());
                                permissions.add(permission);

                            }
                        }
                    }
                    if(permissions.size() >= 1){
                        System.out.println("Permissions: " + permissions.toString());
                    }
                    if(!apiMethodsInvoked.isEmpty()){
                        for(String methodName : apiMethodsInvoked){
                            System.out.println("Method Invoked: " + methodName);
                        }
                    }
                    System.out.println("********************");

                }

            }

    
        }

 

        
    }

    public static ArrayList<String> getClassStringMethods(String className, ClassHierarchy cha){
        ArrayList<String> returnMethods = new ArrayList<String>();

        for(IClass c : cha){
            if(c.getName().toString().equals(className)){
                if(c.getDirectInterfaces().isEmpty()){continue;}
                IClass directInterface = c.getDirectInterfaces().iterator().next();
                for(IMethod m : directInterface.getDeclaredMethods()){
                    returnMethods.add(m.getName().toString());
                }
            }
        }


        return returnMethods;
    }
}
