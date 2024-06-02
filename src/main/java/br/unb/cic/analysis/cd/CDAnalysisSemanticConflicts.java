package br.unb.cic.analysis.cd;

import br.ufpe.cin.soot.analysis.jimple.JCD;
import br.unb.cic.analysis.AbstractMergeConflictDefinition;
import br.unb.cic.analysis.StatementsUtil;
import br.unb.cic.soot.graph.*;
import scala.collection.JavaConverters;
import soot.SootMethod;
import soot.Unit;

import java.io.File;
import java.util.*;

/**
 * An analysis wrapper around the Sparse value
 * flow analysis implementation.
 */
public abstract class CDAnalysisSemanticConflicts extends JCD {

    private String cp;

    private StatementsUtil statementsUtils;

    /**
     * CDAnalysis constructor
     *
     * @param classPath  a classpath to the software under analysis
     * @param definition a definition with the sources and sinks unities
     */
    public CDAnalysisSemanticConflicts(String classPath, AbstractMergeConflictDefinition definition, List<String> entrypoints) {
        this.cp = classPath;
        this.statementsUtils = new StatementsUtil(definition, entrypoints);
    }

    public CDAnalysisSemanticConflicts(String classPath, AbstractMergeConflictDefinition definition) {
        this(classPath, definition, new ArrayList<>());
    }

    @Override
    public String sootClassPath() {
        //TODO: what is the role of soot classPath here??
        return cp;
    }

    @Override
    public scala.collection.immutable.List<String> getIncludeList() {
        return JavaConverters.asScalaBuffer(Arrays.asList("")).toList();
    }

    /**
     * Computes the source-sink paths
     * @return a set with a list of nodes that together builds a source-sink path.
     */
    public Set<List<LambdaNode>> findSourceSinkPaths() {
        Set<List<LambdaNode>> paths = new HashSet<>();

        JavaConverters
                .asJavaCollection(cd().findConflictingPaths())
                .forEach(p -> paths.add(new ArrayList(JavaConverters.asJavaCollection(p))));

       return paths;
    }

    @Override
    public final scala.collection.immutable.List<String> applicationClassPath() {
        String[] array = cp.split(File.pathSeparator);
        return JavaConverters.asScalaBuffer(Arrays.asList(array)).toList();
    }

    @Override
    public final scala.collection.immutable.List<SootMethod> getEntryPoints() {
        return this.statementsUtils.getEntryPoints();
    }

    @Override
    public final NodeType analyze(Unit unit) {
        if (isSource(unit)) {
            return SourceNode.instance();
        } else if (isSink(unit)) {
            return SinkNode.instance();
        }
        return SimpleNode.instance();
    }

    protected boolean isSource(Unit unit) {
        return this.statementsUtils.getDefinition().getSourceStatements()
                .stream()
                .map(stmt -> stmt.getUnit())
                .anyMatch(u -> u.equals(unit));
    }

    protected boolean isSink(Unit unit) {
        return this.statementsUtils.getDefinition().getSinkStatements()
                .stream()
                .map(stmt -> stmt.getUnit())
                .anyMatch(u -> u.equals(unit));
    }

    @Override
    public final boolean isFieldSensitiveAnalysis() {
        return true;
    }
}
