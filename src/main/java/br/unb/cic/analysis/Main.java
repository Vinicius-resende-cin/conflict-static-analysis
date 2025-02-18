package br.unb.cic.analysis;

import br.unb.cic.analysis.cd.CDAnalysisSemanticConflicts;
import br.unb.cic.analysis.cd.CDIntraProcedural;
import br.unb.cic.analysis.df.ConfluentAnalysis;
import br.unb.cic.analysis.df.ConfluentTaintedAnalysis;
import br.unb.cic.analysis.df.ReachDefinitionAnalysis;
import br.unb.cic.analysis.df.TaintedAnalysis;
import br.unb.cic.analysis.df.pessimistic.PessimisticTaintedAnalysis;
import br.unb.cic.analysis.dfp.DFPAnalysisSemanticConflicts;
import br.unb.cic.analysis.dfp.DFPInterProcedural;
import br.unb.cic.analysis.dfp.DFPIntraProcedural;
import br.unb.cic.analysis.io.DefaultReader;
import br.unb.cic.analysis.io.MergeConflictReader;
import br.unb.cic.analysis.model.Conflict;
import br.unb.cic.analysis.model.Statement;
import br.unb.cic.analysis.oa.OverrideAssignment;
import br.unb.cic.analysis.oa.OverrideAssignmentWithPointerAnalysis;
import br.unb.cic.analysis.oa.OverrideAssignmentWithoutPointerAnalysis;
import br.unb.cic.analysis.pdg.PDGAnalysisSemanticConflicts;
import br.unb.cic.analysis.pdg.PDGIntraProcedural;
import br.unb.cic.analysis.reachability.ReachabilityAnalysis;
import br.unb.cic.analysis.svfa.SVFAAnalysis;
import br.unb.cic.analysis.svfa.SVFAInterProcedural;
import br.unb.cic.analysis.svfa.SVFAIntraProcedural;
import br.unb.cic.analysis.svfa.confluence.DFPConfluenceAnalysis;
import br.unb.cic.diffclass.DiffClass;
import com.google.common.base.Stopwatch;
import org.apache.commons.cli.*;
import scala.collection.JavaConverters;
import soot.Body;
import soot.BodyTransformer;
import soot.PackManager;
import soot.Transform;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {

    private Options options;
    private CommandLine cmd;
    private AbstractMergeConflictDefinition definition;
    private Set<String> targetClasses;
    private List<String> conflicts = new ArrayList<>();
    private List<String> JSONconflicts = new ArrayList<>();
    private ReachDefinitionAnalysis analysis;
    public static Stopwatch stopwatch;

    public static void main(String args[]) {
        Main m = new Main();
        try {
            m.createOptions();

            CommandLineParser parser = new DefaultParser();
            m.cmd = parser.parse(m.options, args);
            CommandLine cmd = m.cmd;
            String mode = "dataflow"; 
            if (cmd.hasOption("mode")) {
                mode = cmd.getOptionValue("mode");
            }
            if (cmd.hasOption("repo") && cmd.hasOption("commit")) {
                DiffClass module = new DiffClass();
                module.getGitRepository(cmd.getOptionValue("repo"));
                module.diffAnalysis(cmd.getOptionValue("commit"));
                m.loadDefinitionFromDiffAnalysis(module);
            } else {
                m.loadDefinition(cmd.getOptionValue("csv"));
            }
            m.runAnalysis(mode, m.parseClassPath(cmd.getOptionValue("cp")));

            m.exportResults();

        } catch (ParseException e) {
            System.out.println("Error: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java Main", m.options);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String parseClassPath(String cp) {
        File f = new File(cp);
        String res = cp;
        if (f.exists() && f.isDirectory()) {
            for (File file : f.listFiles()) {
                if (file.getName().endsWith(".jar")) {
                    res += ":";
                    res += file.getAbsolutePath();
                }
            }
        }
        return res;
    }

    private void exportResults() throws Exception {
        System.out.println(" Analysis results");
        System.out.println("----------------------------");

        if (conflicts.size() == 0) {
            System.out.println(" No conflicts detected");
            System.out.println("----------------------------");
            return;
        }

        System.out.println(" Number of conflicts: " + conflicts.size());
        final String out = "out.txt";
        final FileWriter fw = new FileWriter(out);
        conflicts.forEach(c -> {
            try {
                fw.write(c + "\n\n");
            } catch (Exception e) {
                System.out.println("error exporting the results " + e.getMessage());
            }
        });
        fw.close();
        System.out.println(" Results exported to " + out);

        final String outJSON = "out.json";
        final FileWriter fwJSON = new FileWriter(outJSON);
        fwJSON.write("[\n");
        JSONconflicts.forEach(c -> {
            try {
                fwJSON.write(c);
                fwJSON.write(JSONconflicts.indexOf(c) == JSONconflicts.size() - 1 ? "\n" : ",\n");
            } catch (Exception e) {
                System.out.println("error exporting the results " + e.getMessage());
            }
        });
        fwJSON.write("\n]");
        fwJSON.close();
        System.out.println(" JSON Results exported to " + outJSON);

        System.out.println("----------------------------");
    }

    private void createOptions() {
        options = new Options();
        Option classPathOption = Option.builder("cp").argName("class-path")
                .required().hasArg().desc("the classpath used in the analysis")
                .build();

        Option inputFileOption = Option.builder("csv").argName("csv")
                .hasArg().desc("the input csv files with the list of changes")
                .build();

        Option analysisOption = Option.builder("mode").argName("mode")
                .hasArg().desc("analysis mode [data-flow, tainted, reachability, svfa-{interprocedural | intraprocedural}" +
                        ", svfa-confluence-{interprocedural | intraprocedural}, pessimistic-dataflow]")
                .build();

        Option repoOption = Option.builder("repo").argName("repo")
                .hasArg().desc("the path or url of git repository")
                .build();

        Option commitOption = Option.builder("commit").argName("commit")
                .hasArg().desc("the commit merge to analysis")
                .build();

        Option verboseOption = Option.builder("verbose").argName("verbose").hasArg().desc("run in the verbose mode").build();

        Option recursiveOption = Option.builder("recursive").argName("recursive").hasArg()
                .desc("run using the recursive strategy for mapping sources and sinks")
                .build();

        Option depthLimitOption = Option.builder("depthLimit").argName("depthLimit").hasArg()
                .desc("sets the depth limit on accessing methods when performing Overriding Assignment " +
                        "Interprocedural analysis")
                .build();

        Option depthMethodsVisitedSVFAOption = Option.builder("printDepthSVFA").argName("printDepthSVFA").hasArg()
                .desc("sets depthMethodsVisited from SVFA")
                .build();

        Option entrypointsOption = Option.builder("entrypoints").argName("entrypoints").hasArg()
                .desc("entrypoints")
                .build();
        Option oaPointerAnalysisOption = Option.builder("oaPointerAnalysis").argName("oaPointerAnalysis").hasArg()
                .desc("enable pointer analysis in overloading assignment")
                .build();

        options.addOption(classPathOption);
        options.addOption(inputFileOption);
        options.addOption(analysisOption);
        options.addOption(repoOption);
        options.addOption(commitOption);
        options.addOption(verboseOption);
        options.addOption(recursiveOption);
        options.addOption(depthLimitOption);
        options.addOption(depthMethodsVisitedSVFAOption);
        options.addOption(entrypointsOption);
        options.addOption(oaPointerAnalysisOption);
    }

    private void runAnalysis(String mode, String classpath) {
        switch (mode) {
            case "svfa-interprocedural":
                runSparseValueFlowAnalysis(classpath, true);
                break;
            case "svfa-intraprocedural":
                runSparseValueFlowAnalysis(classpath, false);
                break;
            case "dfp-confluence-interprocedural":
                runDFPConfluenceAnalysis(classpath, true);
                break;
            case "dfp-confluence-intraprocedural":
                runDFPConfluenceAnalysis(classpath, false);
                break;
            case "reachability":
                runReachabilityAnalysis(classpath);
                break;
            case "overriding-interprocedural":
                runOverrideAssignmentAnalysis(classpath, true);
                break;
            case "overriding-intraprocedural":
                runOverrideAssignmentAnalysis(classpath, false);
                break;
            case "dfp-intra":
                runDFPAnalysis(classpath, false);
                break;
            case "dfp-inter":
                runDFPAnalysis(classpath, true);
                break;
            case "pdg":
                runPDGAnalysis(classpath, true);
                break;
            case "cd":
                runCDAnalysis(classpath, true);
                break;
            case "pdg-e":
                runPDGAnalysis(classpath, false);
                break;
            case "cd-e":
                runCDAnalysis(classpath, false);
                break;
            case "pessimistic-dataflow":
                runPessimisticDataFlowAnalysis(classpath);
                break;
            default:
                runDataFlowAnalysis(classpath, mode);
        }
    }

    private void runPessimisticDataFlowAnalysis(String classpath) {
        PackManager.v().getPack("jtp").add(
                new Transform("jtp.analysis", new BodyTransformer() {
                    @Override
                    protected void internalTransform(Body body, String s, Map<String, String> map) {
                        PessimisticTaintedAnalysis analysis = new PessimisticTaintedAnalysis(body, definition);

                        conflicts.addAll(
                                analysis
                                        .getConflicts()
                                        .stream()
                                        .map(Conflict::toString)
                                        .collect(Collectors.toList()));

                        JSONconflicts.addAll(
                                analysis
                                        .getConflicts()
                                        .stream()
                                        .map(Conflict::toJSON)
                                        .collect(Collectors.toList()));
                    }
                })
        );
        SootWrapper.builder()
                .withClassPath(classpath)
                .addClass(targetClasses.stream().collect(Collectors.joining(" ")))
                .build()
                .execute();

    }

    private void runDataFlowAnalysis(String classpath, String mode) {
        PackManager.v().getPack("jtp").add(
                new Transform("jtp.analysis", new BodyTransformer() {
                    @Override
                    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
                        switch (mode) {
                            case "dataflow":
                                analysis = new ReachDefinitionAnalysis(body, definition);
                                break;
                            case "tainted":
                                analysis = new TaintedAnalysis(body, definition);
                            case "confluence":
                                analysis = new ConfluentAnalysis(body, definition);
                                break;
                            case "confluence-tainted":
                                analysis = new ConfluentTaintedAnalysis(body, definition);
                                break;
                            default: {
                                System.out.println("Error: " + "invalid mode " + mode);
                                System.exit(-1);
                            }
                        }
                    }
                }));
        SootWrapper.builder()
                .withClassPath(classpath)
                .addClass(targetClasses.stream().collect(Collectors.joining(" ")))
                .build()
                .execute();
        if (analysis != null) {
            conflicts.addAll(analysis.getConflicts().stream().map(c -> c.toString()).collect(Collectors.toList()));
            JSONconflicts.addAll(analysis.getConflicts().stream().map(c -> c.toJSON()).collect(Collectors.toList()));
        }
    }

    private void runOverrideAssignmentAnalysis(String classpath, Boolean interprocedural) {
        int depthLimit = Integer.parseInt(cmd.getOptionValue("depthLimit", "5"));
        boolean oaPointerAnalysis = Boolean.parseBoolean(cmd.getOptionValue("oaPointerAnalysis", "true"));
        List<String> entrypoints = convertStringEntrypointsToList(cmd.getOptionValue("entrypoints"));

        stopwatch = Stopwatch.createStarted();

        OverrideAssignment overrideAssignment = oaPointerAnalysis
                ? new OverrideAssignmentWithPointerAnalysis(definition, depthLimit, interprocedural, entrypoints)
                : new OverrideAssignmentWithoutPointerAnalysis(definition, depthLimit, interprocedural, entrypoints);

        SootWrapper.configureSootOptionsToRunInterproceduralOverrideAssignmentAnalysis(classpath);

        overrideAssignment.configureEntryPoints();

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", overrideAssignment));
        System.out.println("Depth limit: " + overrideAssignment.getDepthLimit());

        saveExecutionTime("Configure Soot OA " + (interprocedural ? "Inter" : "Intra"));

        SootWrapper.applyPackages();

        conflicts.addAll(overrideAssignment.getConflicts().stream().map(c -> c.toString()).collect(Collectors.toList()));
        JSONconflicts.addAll(overrideAssignment.getConflicts().stream().map(c -> c.toJSON()).collect(Collectors.toList()));
        saveExecutionTime("Time to perform OA " + (interprocedural ? "Inter" : "Intra"));

        int visitedMethods = overrideAssignment.getVisitedMethodsCount();
        System.out.println("OA " + (interprocedural ? "Inter" : "Intra") + " Visited methods: " + visitedMethods);

        saveVisitedMethods("OA " + (interprocedural ? "Inter" : "Intra"), (visitedMethods + ""));

        saveConflictsLog("OA " + (interprocedural ? "Inter" : "Intra"), conflicts.toString());

    }

    /*
     * After discussing this algorithm with the researchers at
     * UFPE, we decided that we should not support this analysis
     * any more. It might lead to a huge number of false-positives.
     */
    @Deprecated
    private void runReachabilityAnalysis(String classpath) {
        ReachabilityAnalysis analysis = new ReachabilityAnalysis(definition);

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", analysis));
        soot.options.Options.v().setPhaseOption("cg.spark", "on");
        soot.options.Options.v().setPhaseOption("cg.spark", "verbose:true");

        SootWrapper.builder()
                .withClassPath(classpath)
                .addClass(targetClasses.stream().collect(Collectors.joining(" ")))
                .build()
                .execute();

        conflicts.addAll(analysis.getConflicts().stream().map(c -> c.toString()).collect(Collectors.toList()));
        JSONconflicts.addAll(analysis.getConflicts().stream().map(c -> c.toJSON()).collect(Collectors.toList()));
    }

    private void runPDGAnalysis(String classpath, Boolean omitExceptingUnitEdges) {
        List<String> entrypoints = convertStringEntrypointsToList(cmd.getOptionValue("entrypoints"));
        PDGAnalysisSemanticConflicts analysis = new PDGIntraProcedural(classpath, definition, entrypoints);
        CDAnalysisSemanticConflicts cd = new CDIntraProcedural(classpath, definition, entrypoints);
        cd.setOmitExceptingUnitEdges(omitExceptingUnitEdges);
        DFPAnalysisSemanticConflicts dfp = new DFPIntraProcedural(classpath, definition, entrypoints);

        String type_analysis = omitExceptingUnitEdges ? "" : "e";

        stopwatch = Stopwatch.createStarted();
        analysis.configureSoot();
        saveExecutionTime("Configure Soot PDG" + type_analysis);

        stopwatch = Stopwatch.createStarted();

        analysis.buildPDG(cd, dfp);

        conflicts.addAll(JavaConverters.asJavaCollection(analysis.reportConflictsPDG())
                .stream()
                .map(p -> formatConflict(p.toString()))
                .collect(Collectors.toList()));

        saveExecutionTime("Time to perform PDG"+type_analysis);

        System.out.println("CONFLICTS: "+conflicts.toString());

        saveConflictsLog("PDG"+type_analysis, conflicts.toString());
    }

    private void runDFPAnalysis(String classpath, Boolean interprocedural) {
        int depthLimit = Integer.parseInt(cmd.getOptionValue("depthLimit", "5"));
        List<String> entrypoints = convertStringEntrypointsToList(cmd.getOptionValue("entrypoints"));

        definition.setRecursiveMode(options.hasOption("recursive"));
        DFPAnalysisSemanticConflicts analysis = interprocedural
                ? new DFPInterProcedural(classpath, definition, depthLimit, entrypoints)
                : new DFPIntraProcedural(classpath, definition, entrypoints);

        boolean depthMethodsVisited = Boolean.parseBoolean(cmd.getOptionValue("printDepthSVFA", "false"));
        analysis.setPrintDepthVisitedMethods(depthMethodsVisited);
        String type_analysis = interprocedural ? "Inter" : "Intra";
        stopwatch = Stopwatch.createStarted();

        analysis.configureSoot();

        saveExecutionTime("Configure Soot DFP "+type_analysis);

        stopwatch = Stopwatch.createStarted();

        analysis.buildDFP();

        conflicts.addAll(JavaConverters.asJavaCollection(analysis.reportConflictsSVG())
                .stream()
                .map(p -> formatConflict(p.toString()))
                .collect(Collectors.toList()));

        JSONconflicts.addAll(JavaConverters.asJavaCollection(analysis.reportConflictsSVGJSON()));

        saveExecutionTime("Time to perform DFP "+type_analysis);
        System.out.println("Depth limit: "+analysis.getDepthLimit());

        System.out.print("CONFLICTS: ");

        List<String> conflicts_report = analysis.reportDFConflicts();

        conflicts_report.add(conflicts.toString());

        System.out.println(conflicts.toString());

        System.out.println("Visited methods: "+ analysis.getNumberVisitedMethods());
        saveVisitedMethods("DFP "+type_analysis, (analysis.getNumberVisitedMethods()+","+analysis.svg().graph().size()+","+analysis.svg().edges().size()));

        saveConflictsLog("DFP "+type_analysis, conflicts_report.toString());

    }

    private void runCDAnalysis(String classpath, Boolean omitExceptingUnitEdges) {
        List<String> entrypoints = convertStringEntrypointsToList(cmd.getOptionValue("entrypoints"));
        CDAnalysisSemanticConflicts analysis = new CDIntraProcedural(classpath, definition, entrypoints);
        String type_analysis = omitExceptingUnitEdges ? "" : "e";

        analysis.setOmitExceptingUnitEdges(omitExceptingUnitEdges);
        stopwatch = Stopwatch.createStarted();
        analysis.configureSoot();
        saveExecutionTime("Configure Soot CD" + type_analysis);

        stopwatch = Stopwatch.createStarted();

        analysis.buildCD();

        conflicts.addAll(JavaConverters.asJavaCollection(analysis.reportConflictsCD())
                .stream()
                .map(p -> formatConflict(p.toString()))
                .collect(Collectors.toList()));

        saveExecutionTime("Time to perform CD"+type_analysis);

        System.out.println("CONFLICTS: "+conflicts.toString());

        saveConflictsLog("CD"+type_analysis, conflicts.toString());
    }

    private void runSparseValueFlowAnalysis(String classpath, boolean interprocedural) {
        List<String> entrypoints = convertStringEntrypointsToList(cmd.getOptionValue("entrypoints"));
        definition.setRecursiveMode(cmd.hasOption("recursive"));

        SVFAAnalysis analysis = interprocedural
                ? new SVFAInterProcedural(classpath, definition, entrypoints)
                : new SVFAIntraProcedural(classpath, definition, entrypoints);

        boolean depthMethodsVisited = Boolean.parseBoolean(cmd.getOptionValue("printDepthSVFA", "false"));
        analysis.setPrintDepthVisitedMethods(depthMethodsVisited);

        String type_analysis = interprocedural ? "Inter" : "Intra";

        stopwatch = Stopwatch.createStarted();
        analysis.configureSoot();
        saveExecutionTime("Configure Soot DF "+type_analysis);

        stopwatch = Stopwatch.createStarted();

        analysis.buildSparseValueFlowGraph();

        conflicts.addAll(JavaConverters.asJavaCollection(analysis.reportConflictsSVG())
                .stream()
                .map(p -> formatConflict(p.toString()))
                .collect(Collectors.toList()));

        JSONconflicts.addAll(JavaConverters.asJavaCollection(analysis.reportConflictsSVGJSON()));

        saveExecutionTime("Time to perform DF "+type_analysis);

        System.out.println("CONFLICTS: "+conflicts.toString());

        saveConflictsLog("DF "+type_analysis, conflicts.toString());
    }

    private void runDFPConfluenceAnalysis(String classpath, boolean interprocedural) {
        int depthLimit = Integer.parseInt(cmd.getOptionValue("depthLimit", "5"));
        List<String> entrypoints = convertStringEntrypointsToList(cmd.getOptionValue("entrypoints"));
        String type_analysis = interprocedural ? "Inter" : "Intra";

        definition.setRecursiveMode(options.hasOption("recursive"));
        DFPConfluenceAnalysis analysis = new DFPConfluenceAnalysis(classpath, this.definition, interprocedural, depthLimit, entrypoints);
        boolean depthMethodsVisited = Boolean.parseBoolean(cmd.getOptionValue("printDepthSVFA", "false"));

        analysis.execute(false);
        System.out.println("Depth limit: "+analysis.getDepthLimit());
        conflicts.addAll(analysis.getConfluentConflicts()
                .stream()
                .map(p -> formatConflict(p.toString()))
                .collect(Collectors.toList()));

        System.out.println("CONFLICTS: "+conflicts.toString());
        saveVisitedMethods("Confluence "+type_analysis, (analysis.getVisitedMethods()+","+analysis.getGraphSize()));
        saveConflictsLog("Confluence "+type_analysis, analysis.reportConflictsConfluence().toString().replace("\n", ""));
    }

    private void loadDefinition(String filePath) throws Exception {
        MergeConflictReader reader = new DefaultReader(filePath);
        List<ClassChangeDefinition> changes = reader.read();
        Map<String, List<Integer>> sourceDefs = new HashMap<>();
        Map<String, List<Integer>> sinkDefs = new HashMap<>();
        targetClasses = new HashSet<>();
        for (ClassChangeDefinition change : changes) {
            if (change.getType().equals(Statement.Type.SOURCE)) {
                addChange(sourceDefs, change);
            } else {
                addChange(sinkDefs, change);
            }
            targetClasses.add(change.getClassName());
        }
        definition = new AbstractMergeConflictDefinition() {
            @Override
            protected Map<String, List<Integer>> sourceDefinitions() {
                return sourceDefs;
            }

            @Override
            protected Map<String, List<Integer>> sinkDefinitions() {
                return sinkDefs;
            }
        };
    }

    private void addChange(Map<String, List<Integer>> map, ClassChangeDefinition change) {
        if (map.containsKey(change.getClassName())) {
            map.get(change.getClassName()).add(change.getLineNumber());
        } else {
            List<Integer> lines = new ArrayList<>();
            lines.add(change.getLineNumber());
            map.put(change.getClassName(), lines);
        }
    }

    private void loadDefinitionFromDiffAnalysis(DiffClass module) {
        ArrayList<Entry<String, Integer>> sourceClasses = module.getSourceModifiedClasses();
        ArrayList<Entry<String, Integer>> sinkClasses = module.getSinkModifiedClasses();
        Map<String, List<Integer>> sourceDefs = new HashMap<>();
        Map<String, List<Integer>> sinkDefs = new HashMap<>();
        targetClasses = new HashSet<>();
        for (Entry<String, Integer> change : sourceClasses) {
            addChangeFromDiffAnalysis(sourceDefs, change);
            targetClasses.add(change.getKey());
        }
        for (Entry<String, Integer> change : sinkClasses) {
            addChangeFromDiffAnalysis(sinkDefs, change);
            targetClasses.add(change.getKey());
        }

        definition = new AbstractMergeConflictDefinition() {
            @Override
            protected Map<String, List<Integer>> sourceDefinitions() {
                return sourceDefs;
            }

            @Override
            protected Map<String, List<Integer>> sinkDefinitions() {
                return sinkDefs;
            }
        };
    }

    private void addChangeFromDiffAnalysis(Map<String, List<Integer>> map, Entry<String, Integer> change) {
        if (map.containsKey(change.getKey())) {
            map.get(change.getKey()).add(change.getValue());
        } else {
            List<Integer> lines = new ArrayList<>();
            lines.add(change.getValue());
            map.put(change.getKey(), lines);
        }
    }

    public void saveExecutionTime(String description){

        NumberFormat formatter = new DecimalFormat("#0.00000");

        long time = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        try {
            FileWriter myWriter = new FileWriter("time.txt", true);
            myWriter.write(description+";"+formatter.format(time/1000d)+"\n");
            System.out.println(description+" "+formatter.format(time/1000d));
            myWriter.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void saveVisitedMethods(String description, String visited_methods){
        try {
            FileWriter myWriter = new FileWriter("visited_methods.txt", true);
            myWriter.write(description+"; "+visited_methods+"\n");
            myWriter.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void saveConflictsLog(String description, String log_message){
        try {
            FileWriter myWriter = new FileWriter("conflicts_log.txt", true);
            myWriter.write(description+" log => "+log_message+"\n");
            myWriter.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public String formatConflict(String p) {
        return p.replace("), Node", ") => Node");
    }

    private List<String> convertStringEntrypointsToList(String str) {
        if (str == null) {
            return Collections.emptyList();
        }

        String trimmedStr = str.substring(1, str.length() - 1);
        String[] elements = trimmedStr.split(", ");

        List<String> entrypointsList = new ArrayList<>();
        for (String element : elements) {
            entrypointsList.add(extractMethodSignature(element));
        }

        return entrypointsList;
    }

    private String extractMethodSignature(String fullMethodSignature) {
        int lastColonIndex = fullMethodSignature.lastIndexOf(':');
        if (lastColonIndex != -1) {
            String methodSignature = fullMethodSignature.substring(lastColonIndex + 1).trim();
            if (methodSignature.endsWith(">")) {
                methodSignature = methodSignature.substring(0, methodSignature.length() - 1);
            }
            return methodSignature;
        } else {
            return "";
        }
    }
}