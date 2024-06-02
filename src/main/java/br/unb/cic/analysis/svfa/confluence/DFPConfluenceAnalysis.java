package br.unb.cic.analysis.svfa.confluence;

import br.unb.cic.analysis.AbstractMergeConflictDefinition;
import br.unb.cic.analysis.Main;
import br.unb.cic.analysis.dfp.DFPAnalysisSemanticConflicts;
import br.unb.cic.analysis.model.Statement;
import br.unb.cic.soot.graph.StatementNode;
import com.google.common.base.Stopwatch;
import soot.G;
import soot.Unit;
import soot.options.Options;

import java.util.*;

public class DFPConfluenceAnalysis {

    private String cp;
    private boolean interprocedural;
    private AbstractMergeConflictDefinition definition;
    private Set<ConfluenceConflict> confluentFlows = new HashSet<>();
    private int depthLimit;
    private List<String> entrypoints;
    private String getGraphSize;
    private int visitedMethods;

    /**
     * DFPConfluenceAnalysis constructor
     *
     * @param classPath       a classpath to the software under analysis
     * @param definition      a definition with the sources and sinks unities
     * @param interprocedural a flag indicating whether to consider interprocedural analysis.
     * @param depthLimit      the depth limit for the analysis
     * @param entrypoints     the list of entry points for the analysis
     */
    public DFPConfluenceAnalysis(String classPath, AbstractMergeConflictDefinition definition, boolean interprocedural, int depthLimit, List<String> entrypoints) {
        this.cp = classPath;
        this.definition = definition;
        this.interprocedural = interprocedural;
        this.depthLimit = depthLimit;
        this.entrypoints = entrypoints;
    }

    public DFPConfluenceAnalysis(String classPath, AbstractMergeConflictDefinition definition, boolean interprocedural) {
        this(classPath, definition, interprocedural, 5, new ArrayList<>());
    }


    /**
     * After the execute method has been called, it returns the confluent conflicts returned by the algorithm
     *
     * @return a set of confluence conflicts
     */
    public Set<ConfluenceConflict> getConfluentConflicts() {
        return this.confluentFlows;
    }

    /**
     * Executes both source -> base and sink -> base SVFA analysis intersects then populating
     * the confluentFlows attribute with the results
     */
    public void execute(boolean depthMethodsVisited) {
        DFPAnalysisSemanticConflicts sourceBaseAnalysis = sourceBaseAnalysis(interprocedural);
        String type_analysis;
        if (this.interprocedural) {
            type_analysis = "Inter";
        } else {
            type_analysis = "Intra";
        }
        Main m = new Main();
        m.stopwatch = Stopwatch.createStarted();
        sourceBaseAnalysis.setPrintDepthVisitedMethods(depthMethodsVisited);

        sourceBaseAnalysis.configureSoot();
        Options.v().ignore_resolution_errors();
        m.saveExecutionTime("Configure Soot Confluence 1 "+type_analysis);

        m.stopwatch = Stopwatch.createStarted();

        sourceBaseAnalysis.buildDFP();
        Set<List<StatementNode>> sourceBasePaths = sourceBaseAnalysis.findSourceSinkPaths();

        m.saveExecutionTime("Time to perform Confluence 1 "+type_analysis);

        m.stopwatch = Stopwatch.createStarted();

        G.v().reset();

        DFPAnalysisSemanticConflicts sinkBaseAnalysis = sinkBaseAnalysis(this.interprocedural);
        sinkBaseAnalysis.setPrintDepthVisitedMethods(depthMethodsVisited);

        sinkBaseAnalysis.configureSoot();

        m.saveExecutionTime("Configure Soot Confluence 2 "+type_analysis);

        m.stopwatch = Stopwatch.createStarted();

        sinkBaseAnalysis.buildDFP();

        Set<List<StatementNode>> sinkBasePaths = sinkBaseAnalysis.findSourceSinkPaths();

        this.confluentFlows = intersectPathsByLastNode(sourceBasePaths, sinkBasePaths);

        m.saveExecutionTime("Time to perform Confluence 2 "+type_analysis);

        System.out.println("Visited methods: "+ (sourceBaseAnalysis.getNumberVisitedMethods()+sinkBaseAnalysis.getNumberVisitedMethods()));
        setVisitedMethods(sourceBaseAnalysis.getNumberVisitedMethods()+sinkBaseAnalysis.getNumberVisitedMethods());
        setGraphSize(sourceBaseAnalysis, sinkBaseAnalysis);
    }

     public List<String> reportConflictsConfluence(){
        List<String> conflicts_report = new ArrayList<>();
         List<Integer> left_lines = new ArrayList<>();
         List<Integer> right_lines = new ArrayList<>();
         List<Integer> cf_lines = new ArrayList<>();

         for (ConfluenceConflict conflict : this.confluentFlows) {

             StatementNode df1 = conflict.getSourceNodePath().get(0);
             StatementNode df2 = conflict.getSinkNodePath().get(0);

             StatementNode confluence = conflict.getSinkNodePath().get(conflict.getSinkNodePath().size() - 1);

             Integer left_line = df1.getPathVisitedMethods().head().line();
             Integer right_line = df2.getPathVisitedMethods().head().line();
             Integer cf_line = confluence.getPathVisitedMethods().head().line();

            Boolean contains_lines = left_lines.contains(left_line)
                    && right_lines.contains(right_line)
                    && cf_lines.contains(cf_line);

            if (!contains_lines){
                System.out.println("Confluence interference in "+ df1.getPathVisitedMethods().head().getMethod().method());
                System.out.println("Confluence flows from execution of lines "+df1.getPathVisitedMethods().head().line()+" and "+df2.getPathVisitedMethods().head().line()+
                        " to line "+confluence.getPathVisitedMethods().head().line()+", defined in "+df1.getPathVisitedMethods().head().getUnit()+" and "+df2.getPathVisitedMethods().head().getUnit()+" and used in "+confluence.getPathVisitedMethods().head().getUnit());
                System.out.println("Caused by line "+left_line+ " flow: "+df1.pathVisitedMethodsToString());
                System.out.println("Caused by line "+right_line+ " flow: "+df2.pathVisitedMethodsToString());
                System.out.println("Caused by line "+cf_line+ " flow: "+confluence.pathVisitedMethodsToString());

                conflicts_report.add("Confluence interference in "+ df1.getPathVisitedMethods().head().getMethod().method());
                conflicts_report.add("Confluence flows from execution of lines "+df1.getPathVisitedMethods().head().line()+" and "+df2.getPathVisitedMethods().head().line()+
                        " to line "+confluence.getPathVisitedMethods().head().line()+", defined in "+df1.getPathVisitedMethods().head().getUnit()+" and "+df2.getPathVisitedMethods().head().getUnit()+" and used in "+confluence.getPathVisitedMethods().head().getUnit());
                conflicts_report.add("Caused by line "+left_line+ " flow: "+df1.pathVisitedMethodsToString());
                conflicts_report.add("Caused by line "+right_line+ " flow: "+df2.pathVisitedMethodsToString());
                conflicts_report.add("Caused by line "+cf_line+ " flow: "+confluence.pathVisitedMethodsToString()+"\n");

                left_lines.add(left_line);
                right_lines.add(right_line);
                cf_lines.add(cf_line);
            }


         }
        return conflicts_report;
    }

    /**
     * Intersects the list of paths looking for paths that have the same last nodes
     * also ignores redundant node (that represent different jimple lines but the same Java line)
     * @param paths1 A set of lists of nodes with at least 2 nodes
     * @param paths1 A set of lists of nodes with at least 2 nodes
     * @return A set of confluence conflicts
     */
    private Set<ConfluenceConflict> intersectPathsByLastNode(Set<List<StatementNode>> paths1, Set<List<StatementNode>> paths2) {
        Map<StatementNode, List<StatementNode>> pathEndHash = new HashMap<>();

        for (List<StatementNode> path: paths1) {
            pathEndHash.put(getLastNode(path), path);
        }

        Set<ConfluenceConflict> result = new HashSet<>();
        for (List<StatementNode> path : paths2) {
            StatementNode lastNode = getLastNode(path);

            StatementNode stmt = containsKey(pathEndHash, lastNode);
            if (stmt!= null) {
                result.add(new ConfluenceConflict(pathEndHash.get(stmt), path));
            }
        }

        return result;
    }

    public StatementNode containsKey(Map<StatementNode, List<StatementNode>> pathEndHash, StatementNode lastNode){
        for (StatementNode stmt: pathEndHash.keySet()){
            if (lastNode.equals(stmt)) {
                return stmt;
            }
        }
        return null;
    }

    /**
     * @param path A list of nodes with at least 2 nodes
     * @return The last node of the list
     */
    private StatementNode getLastNode(List<StatementNode> path) {
        int pathSize = path.size();
        assert pathSize > 1; // assume that all paths have at least one source and one sink
        return path.get(pathSize - 1);
    }

    /**
     * @return A instance of a child class of the JDFPAnalysis class that redefine source and sink as source and base
     */
    private br.unb.cic.analysis.dfp.DFPAnalysisSemanticConflicts sourceBaseAnalysis(boolean interprocedural) {
        return new br.unb.cic.analysis.dfp.DFPAnalysisSemanticConflicts(this.cp, this.definition, this.depthLimit, this.entrypoints) {

            /**
             * As in this case we want to detect flows between source and base, this methods defines isSink as all units
             * that are neither source nor sink and are inside a method body
             */
            @Override
            protected boolean isSink(Unit unit) {
                return isInMethodBody(unit) && isNotSourceOrSink(unit);
            }

            /**
             * @return true, if using inter-procedural mode.
             */
            @Override
            public boolean interprocedural() {
                return interprocedural;
            }

        };
    }

    /**
     * @return A instance of a child class of the SVFAAnalysis class that redefine source and sink as source and base
     */
    private DFPAnalysisSemanticConflicts sinkBaseAnalysis(boolean interprocedural) {
        return new DFPAnalysisSemanticConflicts(this.cp, this.definition, this.depthLimit, this.entrypoints) {

            /**
             * As in this case we want to detect flows between sink and base, this methods defines isSink as all units
             * that are neither source nor sink and are inside a method body
             */
            @Override
            protected boolean isSink(Unit unit) {
                return isInMethodBody(unit) && isNotSourceOrSink(unit);
            }

            /**
             * @return true, if using inter-procedural mode.
             */
            @Override
            public boolean interprocedural() {
                return interprocedural;
            }


        };
    }

    private boolean isInMethodBody(Unit unit) {
        /**
         * Some Jimple units actually don't represent real lines and are not in inside the method body.
         */
        return unit.getJavaSourceStartLineNumber() > 0;
    }

    private boolean isNotSourceOrSink(Unit unit) {
        return unitIsNotOnList(this.definition.getSourceStatements(), unit) &&
                unitIsNotOnList(this.definition.getSinkStatements(), unit);
    }

    private boolean unitIsNotOnList(List<Statement> statements, Unit unit) {
        return statements.stream().map(stmt -> stmt.getUnit()).noneMatch(u -> u.equals(unit));
    }

    public int getVisitedMethods() {
        return this.visitedMethods;
    }

    public void setGraphSize(DFPAnalysisSemanticConflicts source, DFPAnalysisSemanticConflicts sink){
        this.getGraphSize = (source.svg().graph().size()+","+source.svg().edges().size()+","+sink.svg().graph().size()+","+sink.svg().edges().size());
    }

    public String getGraphSize(){
        return this.getGraphSize;
    }

    public void setVisitedMethods(int visitedMethods) {
        this.visitedMethods = visitedMethods;
    }
    public void setCp(String cp) {
        this.cp = cp;
    }

    public int getDepthLimit() {
        return this.depthLimit;
    }
}
