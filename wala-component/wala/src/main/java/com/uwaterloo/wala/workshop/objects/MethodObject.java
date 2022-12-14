package com.uwaterloo.wala.workshop.objects;

import com.ibm.wala.types.TypeReference;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class MethodObject {
    private String name;
    private String signature;
    private int numberOfParams;
    private ArrayList<TypeReference> typesOfParams;

    public MethodObject() {
        this.name = "";
        this.numberOfParams = 0;
        this.typesOfParams = new ArrayList<TypeReference>();
    }

    public MethodObject(String name, int numberOfParams, ArrayList<TypeReference> typesOfParams) {
        this.name = name;
        this.numberOfParams = numberOfParams;
        this.typesOfParams = typesOfParams;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNumberOfParams() {
        return numberOfParams;
    }

    public void setNumberOfParams(int numberOfParams) {
        this.numberOfParams = numberOfParams;
    }

    public ArrayList<TypeReference> getTypesOfParams() {
        return typesOfParams;
    }

    public TypeReference getTypeOfParam(int index){
        return typesOfParams.get(index);
    }

    public void setTypesOfParams(ArrayList<TypeReference> typesOfParams) {
        this.typesOfParams = typesOfParams;
    }

    public void appendTypesOfParams(TypeReference typeToAdd){
        typesOfParams.add(typeToAdd);
    }

}
