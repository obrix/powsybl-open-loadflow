package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StaticVarCompensatorVoltageLambdaQEquationTerm extends AbstractNamedEquationTerm {
    private final List<LfStaticVarCompensatorImpl> lfStaticVarCompensators;

    private final LfBus lfBus;

    private final EquationSystem equationSystem;

    private final Variable vVar;

    private final Variable phiVar;

    private final List<Variable> variables;

    private double targetV;

    private double dfdv;

    private double dfdph;

    private final double sumQgeneratorsWithoutVoltageRegulator;

    private static final String MESSAGE_PREFIX = "Bus PVLQ (";

    public StaticVarCompensatorVoltageLambdaQEquationTerm(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus lfBus, VariableSet variableSet, EquationSystem equationSystem) {
        checklist(lfStaticVarCompensators, lfBus);

        this.lfStaticVarCompensators = lfStaticVarCompensators;
        this.lfBus = lfBus;
        this.equationSystem = equationSystem;
        vVar = variableSet.getVariable(lfBus.getNum(), VariableType.BUS_V);
        phiVar = variableSet.getVariable(lfBus.getNum(), VariableType.BUS_PHI);
        variables = Arrays.asList(vVar, phiVar);

        sumQgeneratorsWithoutVoltageRegulator = getSumQgeneratorsWithoutVoltageRegulator();
    }

    static void checklist(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators, LfBus lfBus) throws PowsyblException {
        if (lfStaticVarCompensators == null || lfStaticVarCompensators.isEmpty()) {
            throw new PowsyblException(MESSAGE_PREFIX + lfBus.getId() + ") require at least one StaticVarCompensator !");
        }
        for (LfGenerator lfGenerator : lfBus.getGenerators()) {
            if (lfGenerator instanceof LfStaticVarCompensatorImpl) {
                checkLfStaticVarCompensatorImpl((LfStaticVarCompensatorImpl) lfGenerator, lfBus);
            } else {
                if (lfGenerator.hasVoltageControl()) {
                    throw new PowsyblException(MESSAGE_PREFIX + lfBus.getId() + ") cannot contains a Generator (" + lfGenerator.getId() + ") with a voltage regulator !");
                }
            }
        }
    }

    private static void checkLfStaticVarCompensatorImpl(LfStaticVarCompensatorImpl lfStaticVarCompensatorImpl, LfBus lfBus) {
        if (!lfStaticVarCompensatorImpl.hasVoltageControl()) {
            throw new PowsyblException(getMessage(lfStaticVarCompensatorImpl, lfBus, ") without Voltage Control !"));
        }
        if (lfStaticVarCompensatorImpl.getVoltagePerReactivePowerControl() == null) {
            throw new PowsyblException(getMessage(lfStaticVarCompensatorImpl, lfBus, ") without VoltagePerReactivePowerControl extension !"));
        }
        if (lfStaticVarCompensatorImpl.getVoltagePerReactivePowerControl().getSlope() == 0) {
            throw new PowsyblException(getMessage(lfStaticVarCompensatorImpl, lfBus, ") with a zero slope"));
        }
    }

    private static String getMessage(LfStaticVarCompensatorImpl lfStaticVarCompensatorImpl, LfBus lfBus, String message) {
        return MESSAGE_PREFIX + lfBus.getId() + ") contains an invalid StaticVarCompensator (" + lfStaticVarCompensatorImpl.getId() + message;
    }

    @Override
    public SubjectType getSubjectType() {
        return SubjectType.BUS;
    }

    @Override
    public int getSubjectNum() {
        return lfBus.getNum();
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v = x[vVar.getRow()];
        double slopeStaticVarCompensators = computeSlopeStaticVarCompensators(lfStaticVarCompensators);
        Equation reactiveEquation = equationSystem.createEquation(lfBus.getNum(), EquationType.BUS_Q);

        EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ = evalAndDerOnTermsFromEquationBUSQ(x, reactiveEquation);

        // given : QbusMinusShunts = QstaticVarCompensators - QloadsAndBatteries + Qgenerators
        // then : Q(U, theta) = QstaticVarCompensators =  QbusMinusShunts + QloadsAndBatteries - Qgenerators
        double qStaticVarCompensators = evalAndDerOnTermsFromEquationBUSQ.qBusMinusShunts + lfBus.getLoadTargetQ() - sumQgeneratorsWithoutVoltageRegulator;
        // f(U, theta) = U + lambda * Q(U, theta)
        targetV = v + slopeStaticVarCompensators * qStaticVarCompensators;
        // dfdU = 1 + lambda * dQdU
        // Q remains constant for loads, batteries and generators, then derivative of Q is zero for this items
        dfdv = 1 + slopeStaticVarCompensators * evalAndDerOnTermsFromEquationBUSQ.dQdVbusMinusShunts;
        // dfdtheta = lambda * dQdtheta
        dfdph = slopeStaticVarCompensators * evalAndDerOnTermsFromEquationBUSQ.dQdPHbusMinusShunts;
    }

    double computeSlopeStaticVarCompensators(List<LfStaticVarCompensatorImpl> lfStaticVarCompensators) {
        if (lfStaticVarCompensators == null || lfStaticVarCompensators.isEmpty()) {
            return 0;
        } else if (lfStaticVarCompensators.size() == 1) {
            return lfStaticVarCompensators.get(0).getSlope();
        } else {
            double sumFrac = 0;
            for (LfStaticVarCompensatorImpl lfStaticVarCompensator : lfStaticVarCompensators) {
                if (lfStaticVarCompensator.getSlope() != 0) {
                    sumFrac += 1 / lfStaticVarCompensator.getSlope();
                }
            }
            if (sumFrac != 0) {
                return 1 / sumFrac;
            } else {
                return 0;
            }
        }
    }

    boolean hasToEvalAndDerTerm(EquationTerm equationTerm) {
        return equationTerm.isActive() &&
                (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide1ReactiveFlowEquationTerm
                        || equationTerm instanceof OpenBranchSide2ReactiveFlowEquationTerm
                        || equationTerm instanceof ShuntCompensatorReactiveFlowEquationTerm);
    }

    boolean hasPhiVar(EquationTerm equationTerm) {
        return equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm
                || equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm;
    }

    class EvalAndDerOnTermsFromEquationBUSQ {
        final double qBusMinusShunts;
        final double dQdVbusMinusShunts;
        final double dQdPHbusMinusShunts;

        public EvalAndDerOnTermsFromEquationBUSQ(double qBusMinusShunts, double dQdVbusMinusShunts, double dQdPHbusMinusShunts) {
            this.qBusMinusShunts = qBusMinusShunts;
            this.dQdVbusMinusShunts = dQdVbusMinusShunts;
            this.dQdPHbusMinusShunts = dQdPHbusMinusShunts;
        }
    }

    /**
     *
     * @param x vector of variable values initialized with a VoltageInitializer
     * @param reactiveEquation
     * @return sum evaluation and derivatives on branch and shunt terms from BUS_Q equation
     */
    EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ(double[] x, Equation reactiveEquation) {
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

    double getSumQgeneratorsWithoutVoltageRegulator() {
        return lfBus.getGenerators().stream().filter(lfGenerator -> !(lfGenerator instanceof LfStaticVarCompensatorImpl) && !lfGenerator.hasVoltageControl())
                .mapToDouble(LfGenerator::getTargetQ).sum();
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
