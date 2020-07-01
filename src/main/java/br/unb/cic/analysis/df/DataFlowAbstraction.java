package br.unb.cic.analysis.df;

import br.unb.cic.analysis.model.Statement;
import soot.Local;
import soot.jimple.internal.JInstanceFieldRef;

import java.util.Objects;

/**
 * Information wee keep while traversing
 * the control flow.
 */
public class DataFlowAbstraction {

    private Local local;
    private JInstanceFieldRef localInstanceField;
    private Statement stmt;

    public DataFlowAbstraction(Local local, Statement stmt) {
        this.local = local;
        this.stmt = stmt;
    }

    public DataFlowAbstraction(JInstanceFieldRef localInstanceField, Statement stmt) {
        this.localInstanceField = localInstanceField;
        this.stmt = stmt;
    }

    public Local getLocal() {
        return local;
    }

    public JInstanceFieldRef getJInstanceFieldRef() {
        return localInstanceField;
    }

    public Statement getStmt() {
        return stmt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFlowAbstraction that = (DataFlowAbstraction) o;
        return Objects.equals(local, that.local) &&
                Objects.equals(stmt, that.stmt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(local, stmt);
    }
}
