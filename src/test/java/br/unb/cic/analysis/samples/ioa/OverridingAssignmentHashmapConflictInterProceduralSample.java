package br.unb.cic.analysis.samples.ioa;

import java.util.HashMap;
import java.util.Map;

// Conflict: [left, m():13] --> [right, m():14]
public class OverridingAssignmentHashmapConflictInterProceduralSample {


    public void m() {
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put("Left", "left"); // LEFT
        hashMap.put("Right", "right"); // RIGHT
    }
}