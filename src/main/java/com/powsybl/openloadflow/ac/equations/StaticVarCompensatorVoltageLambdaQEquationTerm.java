package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;

import java.util.*;

public class StaticVarCompensatorVoltageLambdaQEquationTerm extends AbstractNamedEquationTerm {

    private final List<LfStaticVarCompensatorImpl> lfStaticVarCompensators;

    private final LfBus bus;

    private final EquationSystem equationSystem;

    private final Variable vVar;

    private final Variable phiVar;

    private final List<Variable> variables;

    private double x;

    private double dfdU;

    private double dfdph;

    public StaticVarCompensatorVoltageLambdaQEquationTerm(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus bus, VariableSet variableSet, EquationSystem equationSystem) {
        this.lfStaticVarCompensators = Objects.requireNonNull(lfStaticVarCompensators);
        this.bus = Objects.requireNonNull(bus);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        Objects.requireNonNull(variableSet);
        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        phiVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_PHI);
        variables = Arrays.asList(vVar, phiVar);
    }

    @Override
    public SubjectType getSubjectType() {
        return SubjectType.BUS;
    }

    @Override
    public int getSubjectNum() {
        return bus.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        this.x = x[vVar.getRow()];
    }

    private double evalQsvc(Equation branchReactiveEquation) {
        double value = 0;
        for (EquationTerm equationTerm : branchReactiveEquation.getTerms()) {
            if (equationTerm.isActive() &&
                    (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                            || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm)) {
                value += equationTerm.eval();
                if (equationTerm.hasRhs()) {
                    value -= equationTerm.rhs();
                }
            }
        }
        return value;
    }

    private double derQsvc(Equation branchReactiveEquation, Variable partialDerivativeVariable) {
        double value = 0;
        for (EquationTerm equationTerm : branchReactiveEquation.getTerms()) {
            if (equationTerm.isActive() &&
                    (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                            || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm)) {
                value += equationTerm.der(vVar);
            }
        }
        return value;
    }

    @Override
    public double eval() {
        if (lfStaticVarCompensators.size() > 1) {
            // TODO : comment calculer v si il y a plusieurs StaticVarCompensator dans le bus
            throw new PowsyblException("Bus PVLQ (" + bus.getId() + ") with multiple staticVarCompensator is not supported");
        }
        LfStaticVarCompensatorImpl lfStaticVarCompensator = lfStaticVarCompensators.get(0);
        double slope = lfStaticVarCompensator.getVoltagePerReactivePowerControl().getSlope();

        System.out.println("StaticVarCompensator "
                + lfStaticVarCompensator.getId()
                + " : terminal.getQ = " + lfStaticVarCompensator.getSvc().getTerminal().getQ()
                + " ; targetQ = " + lfStaticVarCompensator.getTargetQ()
                + " ; calculatedQ = " + lfStaticVarCompensator.getCalculatedQ());
        Equation branchReactiveEquation = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);

        // TODO : ? pour lambda * Q(U, theta), utiliser EquationTerm.multiply(terme Q(U, theta), lambda)
        // f(U, theta) = U + lambda * Q(U, theta)
        double v = x + slope * evalQsvc(branchReactiveEquation);
        // dfdU = 1 + lambda dQdU
        dfdU = 1 + slope * derQsvc(branchReactiveEquation, vVar);
        // dfdtheta = lambda * dQdtheta
        dfdph = slope * derQsvc(branchReactiveEquation, phiVar);
        return v;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dfdU;
        } else if (variable.equals(phiVar)) {
            return dfdph;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs() {
        return 0;
    }

    @Override
    protected String getName() {
        return "ac_static_var_compensator";
    }
}
