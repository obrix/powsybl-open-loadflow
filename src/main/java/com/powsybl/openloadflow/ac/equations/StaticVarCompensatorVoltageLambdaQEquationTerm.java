package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StaticVarCompensatorVoltageLambdaQEquationTerm extends AbstractNamedEquationTerm {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticVarCompensatorVoltageLambdaQEquationTerm.class);

    private final List<LfStaticVarCompensatorImpl> lfStaticVarCompensators;

    private final LfBus bus;

    private final EquationSystem equationSystem;

    private final Variable vVar;

    private final Variable phiVar;

    private final List<Variable> variables;

    private double targetV;

    private double dfdv;

    private double dfdph;

    private final double sumQgeneratorsWithoutVoltageRegulator;

    public StaticVarCompensatorVoltageLambdaQEquationTerm(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus bus, VariableSet variableSet, EquationSystem equationSystem) {
        // checklist
        if (lfStaticVarCompensators == null || lfStaticVarCompensators.isEmpty()) {
            throw new PowsyblException("Bus PVLQ (" + bus.getId() + ") require at least one StaticVarCompensator !");
        }
        for (LfGenerator lfGenerator : bus.getGenerators()) {
            if (lfGenerator instanceof LfStaticVarCompensatorImpl) {
                LfStaticVarCompensatorImpl lfStaticVarCompensatorImpl = (LfStaticVarCompensatorImpl) lfGenerator;
                if (!lfStaticVarCompensatorImpl.hasVoltageControl() || lfStaticVarCompensatorImpl.getVoltagePerReactivePowerControl() == null
                        || lfStaticVarCompensatorImpl.getVoltagePerReactivePowerControl().getSlope() == 0) {
                    throw new PowsyblException("Bus PVLQ (" + bus.getId() + ") contains an invalid StaticVarCompensator (" + lfStaticVarCompensatorImpl.getId() + ")");
                }
            } else {
                if (lfGenerator.hasVoltageControl()) {
                    throw new PowsyblException("Bus PVLQ (" + bus.getId() + ") contains a Generator (" + lfGenerator.getId() + ") with a voltage regulator !");
                }
            }
        }

        // here we go
        this.lfStaticVarCompensators = lfStaticVarCompensators;
        this.bus = bus;
        this.equationSystem = equationSystem;
        vVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_V);
        phiVar = variableSet.getVariable(bus.getNum(), VariableType.BUS_PHI);
        variables = Arrays.asList(vVar, phiVar);

        sumQgeneratorsWithoutVoltageRegulator = getSumQgeneratorsWithoutVoltageRegulator();
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
        double slopeStaticVarCompensators = computeSlopeStaticVarCompensators(lfStaticVarCompensators);
        Equation reactiveEquation = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);

        EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ = evalAndDerOnTermsFromEquationBUSQ(x, reactiveEquation);

        // given : QbusMinusShunts = QstaticVarCompensators - QloadsAndBatteries + Qgenerators
        // then : Q(U, theta) = QstaticVarCompensators =  QbusMinusShunts + QloadsAndBatteries - Qgenerators
        double qStaticVarCompensators = evalAndDerOnTermsFromEquationBUSQ.qBusMinusShunts + bus.getLoadTargetQ() - sumQgeneratorsWithoutVoltageRegulator;
        // f(U, theta) = U + lambda * Q(U, theta)
        targetV = x[vVar.getRow()] + slopeStaticVarCompensators * qStaticVarCompensators;
        // dfdU = 1 + lambda dQdU
        // Q remains constant for loads, batteries and generators, then derivative of Q is zero for this items
        dfdv = 1 + slopeStaticVarCompensators * evalAndDerOnTermsFromEquationBUSQ.dQdVbusMinusShunts;
        // dfdtheta = lambda * dQdtheta
        dfdph = slopeStaticVarCompensators * evalAndDerOnTermsFromEquationBUSQ.dQdPHbusMinusShunts;
    }

    public double computeSlopeStaticVarCompensators(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators) {
        if (lfStaticVarCompensators == null || lfStaticVarCompensators.isEmpty()) {
            return 0;
        } else if (lfStaticVarCompensators.size() == 1) {
            return lfStaticVarCompensators.get(0).getSlope();
        } else {
            double sumFrac = 0;
            for (LfStaticVarCompensatorImpl lfStaticVarCompensator : lfStaticVarCompensators) {
                sumFrac += 1 / lfStaticVarCompensator.getSlope();
            }
            return 1 / sumFrac;
        }
    }

    public boolean hasToEvalAndDerTerm(EquationTerm equationTerm) {
        return equationTerm.isActive() &&
                (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof ShuntCompensatorReactiveFlowEquationTerm);
    }

    public boolean hasPhiVar(EquationTerm equationTerm) {
        return equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm;
    }

    private class EvalAndDerOnTermsFromEquationBUSQ {
        private final double qBusMinusShunts;
        private final double dQdVbusMinusShunts;
        private final double dQdPHbusMinusShunts;

        public EvalAndDerOnTermsFromEquationBUSQ(double qBusMinusShunts, double dQdVbusMinusShunts, double dQdPHbusMinusShunts) {
            this.qBusMinusShunts = qBusMinusShunts;
            this.dQdVbusMinusShunts = dQdVbusMinusShunts;
            this.dQdPHbusMinusShunts = dQdPHbusMinusShunts;
        }
    }

    /**
     *
     * @param x vector of variable values initialized with a VoltageInitializer
     * @return sum evaluation and derivatives on branch and shunt terms from BUS_Q equation
     */
    private EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ(double[] x, Equation reactiveEquation) {
        double qBusMinusShunts = 0;
        double dQdVbusMinusShunts = 0;
        double dQdPHbusMinusShunts = 0;

        for (EquationTerm equationTerm : reactiveEquation.getTerms()) {
            if (hasToEvalAndDerTerm(equationTerm)) {
                equationTerm.update(x);
                qBusMinusShunts += equationTerm.eval();
                dQdVbusMinusShunts += equationTerm.der(vVar);
                if (hasPhiVar(equationTerm)) {
                    dQdPHbusMinusShunts += equationTerm.der(phiVar);
                }
            }
        }
        return new EvalAndDerOnTermsFromEquationBUSQ(qBusMinusShunts, dQdVbusMinusShunts, dQdPHbusMinusShunts);
    }

    private double getSumQgeneratorsWithoutVoltageRegulator() {
        return bus.getGenerators().stream().filter(lfGenerator -> !(lfGenerator instanceof LfStaticVarCompensatorImpl) && !lfGenerator.hasVoltageControl())
                .map(LfGenerator::getTargetQ).reduce(0d, (targetQ1, targetQ2) -> targetQ1 + targetQ2);
    }

    @Override
    public double eval() {
        return targetV;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(vVar)) {
            return dfdv;
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
        return "ac_static_var_compensator_with_slope";
    }
}
