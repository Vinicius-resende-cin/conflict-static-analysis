package br.unb.cic.analysis.samples;

// Not Conflict
public class OverridingAssignmentArrayNotConflictInterProceduralSample {
    public void m() {
        foo(); // LEFT
        bar(); // RIGHT
    }

    private void foo() {
        int[] arr = {0};
    }

    private void bar() {
        int[] arr = {1};
    }


}