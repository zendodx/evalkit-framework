package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.model.ReportData;

import java.io.IOException;

class ReporterTest {
    void test() {
        Reporter reporter = new Reporter() {
            @Override
            protected void report(ReportData reportData) throws IOException {

            }
        };
    }
}