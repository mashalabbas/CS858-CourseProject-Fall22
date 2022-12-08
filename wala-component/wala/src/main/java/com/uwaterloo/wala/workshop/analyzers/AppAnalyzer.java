package com.uwaterloo.wala.workshop.analyzers;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.uwaterloo.wala.workshop.parsers.ManifestParser;
import com.uwaterloo.wala.workshop.utils.ScopeUtil;

public class AppAnalyzer {
    public static void launch() {
        String appParentPath = System.getProperty("path");
        ManifestParser.parseManifest(appParentPath);
        /*
         * Start the app analysis from here
         */
    }
}
