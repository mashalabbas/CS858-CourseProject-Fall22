package com.uwaterloo.wala.workshop;

import com.uwaterloo.wala.workshop.analyzers.Analyzer;
import com.uwaterloo.wala.workshop.utils.PropertyUtils;
import com.uwaterloo.wala.workshop.utils.ValidationUtils;

public class Main {
    public static void main(String[] args) throws Exception {
        ValidationUtils.validateProperties();
        Analyzer.launch(Analyzer.Type.valueOf(PropertyUtils.getType()));
    }
}
