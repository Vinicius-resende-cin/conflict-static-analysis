package br.unb.cic.analysis.df;

import br.unb.cic.analysis.AbstractMergeConflictDefinition;
import br.unb.cic.analysis.model.Conflict;
import br.unb.cic.analysis.model.Statement;
import soot.Body;
import soot.Local;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JInstanceFieldRef;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;

import java.util.ArrayList;
import java.util.List;

public class OverridingAssignmentAnalysis extends ReachDefinitionAnalysis {

    /**
     * Constructor of the DataFlowAnalysis class.
     * <p>
     * According to the SOOT architecture, the constructor for a
     * flow analysis must receive as an argument a graph, set up
     * essential information and call the doAnalysis method of the
     * super class.
     *
     * @param definition a set of conflict definitions.
     */
    public OverridingAssignmentAnalysis(Body methodBody, AbstractMergeConflictDefinition definition) {
        super(methodBody, definition);
    }

    @Override
    protected FlowSet<DataFlowAbstraction> gen(Unit u, FlowSet<DataFlowAbstraction> in) {
        FlowSet<DataFlowAbstraction> res = new ArraySparseSet<>();
        if (isSourceStatement(u) || isSinkStatement(u)) {
            u.getUseAndDefBoxes().stream().filter(v -> v.getValue() instanceof Local).forEach(v -> {
                Statement stmt = isSourceStatement(u) ? findSourceStatement(u) : findSinkStatement(u);
                res.add(new DataFlowAbstraction((Local) v.getValue(), stmt));
            });
        } else if (u.getDefBoxes().size() > 0) {

            u.getDefBoxes().stream().filter(v -> v.getValue() instanceof Local).forEach(v -> {
                String localName = getLocalName((Local) v.getValue());

                for (DataFlowAbstraction defsIn: in){
                    String defInName = getLocalName(defsIn.getLocal());
                    //if u not in IN, then add it
                    if (!defInName.equals(localName)){
                        res.add(new DataFlowAbstraction(defsIn.getLocal(), defsIn.getStmt())); //update an element in IN
                        break; //Do not necessary check others elements
                    }
                }
            });
        }
        return res;
    }


    @Override
    protected void detectConflict(FlowSet<DataFlowAbstraction> in, Unit u){
        if (!(isSinkStatement(u) || isSourceStatement(u))){
            return;
        }
        List<DataFlowAbstraction> left = new ArrayList<>();
        List<DataFlowAbstraction> right = new ArrayList<>();

        u.getUseAndDefBoxes().stream().filter(v -> v.getValue() instanceof Local).forEach(v -> {
            String localName = getLocalName((Local) v.getValue());
            for (DataFlowAbstraction filterIn: in){
                String inName = getLocalName(filterIn.getLocal());

                if (filterIn.getStmt().getType().equals(Statement.Type.SOURCE) && inName.equals(localName)){
                    left.add(filterIn);
                }else if (filterIn.getStmt().getType().equals(Statement.Type.SINK) && inName.equals(localName)){
                    right.add(filterIn);
                }
            }
        });

        if(isSinkStatement(u)) {
            checkConflicts(u, left);
        }else  if (isSourceStatement(u)){
            checkConflicts(u, right);
        }
    }

    protected void checkConflicts(Unit u, List<DataFlowAbstraction> statements){
        for (DataFlowAbstraction statement : statements) {
            if (statementEquals(statement, u)) {
                Conflict c = new Conflict(statement.getStmt(), findStatement(u));
                Collector.instance().addConflict(c);
            }
        }
    }

    @Override
    protected void flowThrough(FlowSet<DataFlowAbstraction> in, Unit u, FlowSet<DataFlowAbstraction> out) {
        detectConflict(in, u);
        FlowSet<DataFlowAbstraction> temp = new ArraySparseSet<>();

        FlowSet<DataFlowAbstraction> killSet = new ArraySparseSet<>();
        for(DataFlowAbstraction item : in) {
            if (statementEquals(item, u)){
                killSet.add(item);
            }
        }
        in.difference(killSet, temp);
        temp.union(gen(u, in), out);
    }

    private String getLocalName(Local local){
        return local.getName().split("#")[0];
    }

    private boolean statementEquals(DataFlowAbstraction statement, Unit u){

        String statementName = getLocalName(statement.getLocal());
        for (ValueBox local : u.getDefBoxes()) {
            String localName = "";
            for (ValueBox field : statement.getStmt().getUnit().getDefBoxes()) {
                if (local.getValue() instanceof JInstanceFieldRef) {
                    statementName = field.getValue().toString();
                    localName = local.getValue().toString();
                }else if (local.getValue() instanceof JArrayRef) {
                    if (!(isSinkStatement(u) || isSourceStatement(u))){
                        return false;
                    }
                    statementName = ((field.getValue()) instanceof JArrayRef)
                            ? (((JArrayRef) field.getValue()).getBaseBox().getValue()).toString()
                            : field.getValue().toString();
                    localName = (((JArrayRef) local.getValue()).getBaseBox().getValue()).toString();
                }
            }
            if (localName.equals("") && local.getValue() instanceof Local) {
                localName = getLocalName((Local) local.getValue());
            }
            return statementName.contains(localName);
        }
        return false;
    }
}