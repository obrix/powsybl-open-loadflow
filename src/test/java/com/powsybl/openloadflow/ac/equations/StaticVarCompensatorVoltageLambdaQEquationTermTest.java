package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.impl.AbstractLfBus;
import com.powsybl.openloadflow.network.impl.LfStaticVarCompensatorImpl;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class StaticVarCompensatorVoltageLambdaQEquationTermTest {
    private NetworkBuilder networkBuilder;
    private LoadFlowTestTools loadFlowTestToolsSvcVoltageWithSlope;
    private LfBus lfBus1GenVoltage;
    private LfBus lfBus2SvcVoltageWithSlope;
    private List<LfStaticVarCompensatorImpl> lfStaticVarCompensators;
    private VariableSet variableSet;
    private EquationSystem equationSystem;
    private StaticVarCompensatorVoltageLambdaQEquationTerm staticVarCompensatorVoltageLambdaQEquationTerm;

    @BeforeEach
    void setUp() {
        networkBuilder = new NetworkBuilder();
        loadFlowTestToolsSvcVoltageWithSlope = new LoadFlowTestTools(networkBuilder.addNetworkWithGenOnBus1AndSvcOnBus2().setSvcVoltageAndSlopeOnBus2().addLoadOnBus2().addGenWithoutVoltageRegulatorOnBus2().addShuntCompensatorOnBus2().addOpenLineOnBus1().addOpenLineOnBus2().build());
        lfBus1GenVoltage = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl1_0");
        lfBus2SvcVoltageWithSlope = loadFlowTestToolsSvcVoltageWithSlope.getLfNetwork().getBusById("vl2_0");
        lfStaticVarCompensators = lfBus2SvcVoltageWithSlope.getGenerators().stream()
                .filter(lfGenerator -> lfGenerator instanceof LfStaticVarCompensatorImpl)
                .map(LfStaticVarCompensatorImpl.class::cast)
                .collect(Collectors.toList());
        variableSet = loadFlowTestToolsSvcVoltageWithSlope.getVariableSet();
        equationSystem = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem();
        staticVarCompensatorVoltageLambdaQEquationTerm =
                equationSystem.getEquationTerm(SubjectType.BUS, lfBus2SvcVoltageWithSlope.getNum(), StaticVarCompensatorVoltageLambdaQEquationTerm.class);
    }

    @Test
    void checklistTest() {
        Assertions.assertThrows(PowsyblException.class, () -> StaticVarCompensatorVoltageLambdaQEquationTerm.checklist(null,
                lfBus2SvcVoltageWithSlope), "StaticVarCompensator list should not be null");

        ArrayList<LfStaticVarCompensatorImpl> emptyLfStaticVarCompensators = new ArrayList<LfStaticVarCompensatorImpl>();
        Assertions.assertThrows(PowsyblException.class, () -> StaticVarCompensatorVoltageLambdaQEquationTerm.checklist(emptyLfStaticVarCompensators,
                lfBus2SvcVoltageWithSlope), "StaticVarCompensator list should not be empty");

        Assertions.assertThrows(PowsyblException.class, () -> StaticVarCompensatorVoltageLambdaQEquationTerm.checklist(lfStaticVarCompensators,
                lfBus1GenVoltage), "bus cannot contain any generator with VoltageControl");

        lfStaticVarCompensators.get(0).getVoltagePerReactivePowerControl().setSlope(0);
        Assertions.assertThrows(PowsyblException.class, () -> StaticVarCompensatorVoltageLambdaQEquationTerm.checklist(lfStaticVarCompensators,
                lfBus2SvcVoltageWithSlope), "bus should not contain any slope with zero slope");

        StaticVarCompensator staticVarCompensator = networkBuilder.getSvcOnBus2();
        staticVarCompensator.setReactivePowerSetpoint(300);
        staticVarCompensator.setRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER);
        lfBus2SvcVoltageWithSlope.getGenerators().clear();
        lfBus2SvcVoltageWithSlope.getGenerators().add(LfStaticVarCompensatorImpl.create(staticVarCompensator, (AbstractLfBus) lfBus2SvcVoltageWithSlope));
        Assertions.assertThrows(PowsyblException.class, () -> StaticVarCompensatorVoltageLambdaQEquationTerm.checklist(lfStaticVarCompensators,
                lfBus2SvcVoltageWithSlope), "bus should not contain any slope with ReactivePowerControl");

        staticVarCompensator.setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        staticVarCompensator.removeExtension(VoltagePerReactivePowerControl.class);
        lfBus2SvcVoltageWithSlope.getGenerators().clear();
        lfBus2SvcVoltageWithSlope.getGenerators().add(LfStaticVarCompensatorImpl.create(staticVarCompensator, (AbstractLfBus) lfBus2SvcVoltageWithSlope));
        Assertions.assertThrows(PowsyblException.class, () -> StaticVarCompensatorVoltageLambdaQEquationTerm.checklist(lfStaticVarCompensators,
                lfBus2SvcVoltageWithSlope), "bus should not contain any slope without extension VoltagePerReactivePowerControl");
    }

    @Test
    void updateTest() {
        // getVariable and call update on term with vector of variable values
        Variable vVar = variableSet.getVariable(lfBus2SvcVoltageWithSlope.getNum(), VariableType.BUS_V);
        Variable phiVar = variableSet.getVariable(lfBus2SvcVoltageWithSlope.getNum(), VariableType.BUS_PHI);
        Variable nimpVar = variableSet.getVariable(-1, VariableType.BRANCH_RHO1);
        staticVarCompensatorVoltageLambdaQEquationTerm.update(new double[]{1, 0, 1, 0});

        // assertions
        Assertions.assertEquals(0.995842, staticVarCompensatorVoltageLambdaQEquationTerm.eval(), 1E-6d);
        Assertions.assertEquals(2.199184, staticVarCompensatorVoltageLambdaQEquationTerm.der(vVar), 1E-6d);
        Assertions.assertEquals(-0.4, staticVarCompensatorVoltageLambdaQEquationTerm.der(phiVar), 1E-6d);
        Assertions.assertThrows(IllegalStateException.class, () -> staticVarCompensatorVoltageLambdaQEquationTerm.der(nimpVar));
    }

    @Test
    void computeSlopeStaticVarCompensatorsTest() {
        // 1 svc with 0 slope
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(null));
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(new ArrayList<LfStaticVarCompensatorImpl>()));
        LfStaticVarCompensatorImpl lfStaticVarCompensator = lfStaticVarCompensators.get(0);
        lfStaticVarCompensator.getVoltagePerReactivePowerControl().setSlope(0);
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));

        // 1 svc with 0 slope
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));

        // 2 svc with 0 slope
        lfStaticVarCompensators.add(lfStaticVarCompensator);
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));

        // 2 svc with 0.01 slope
        lfStaticVarCompensator.getVoltagePerReactivePowerControl().setSlope(0.01);
        Assertions.assertEquals(0.00125, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));

        // 2 svc with 0.01 slope + 1 svc with 0.02 slope
        networkBuilder.addSvcWithVoltageAndSlopeOnBus2(1, new double[]{385}, new double[]{0.02}, new double[]{-0.008}, new double[]{0.008});
        StaticVarCompensator bus2svc2 = networkBuilder.getAdditionnalSvcOnBus2().get(0);
        lfStaticVarCompensators.add(LfStaticVarCompensatorImpl.create(bus2svc2, (AbstractLfBus) lfBus2SvcVoltageWithSlope));
        Assertions.assertEquals(0.001, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));
    }

    @Test
    void hasToEvalAndDerTermTest() {
        // build or get equation terms
        Equation equation = equationSystem.createEquation(lfBus2SvcVoltageWithSlope.getNum(), EquationType.BUS_Q);
        ShuntCompensatorReactiveFlowEquationTerm shuntCompensatorReactiveFlowEquationTerm = equation.getTerms().stream().filter(equationTerm -> equationTerm instanceof ShuntCompensatorReactiveFlowEquationTerm).map(ShuntCompensatorReactiveFlowEquationTerm.class::cast).findFirst().get();
        ClosedBranchSide1ReactiveFlowEquationTerm closedBranchSide1ReactiveFlowEquationTerm =
                new ClosedBranchSide1ReactiveFlowEquationTerm(lfBus1GenVoltage.getBranches().get(0), lfBus1GenVoltage, lfBus2SvcVoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        ClosedBranchSide2ReactiveFlowEquationTerm closedBranchSide2ReactiveFlowEquationTerm =
                new ClosedBranchSide2ReactiveFlowEquationTerm(lfBus1GenVoltage.getBranches().get(0), lfBus1GenVoltage, lfBus2SvcVoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        OpenBranchSide1ReactiveFlowEquationTerm openBranchSide1ReactiveFlowEquationTerm =
                new OpenBranchSide1ReactiveFlowEquationTerm(lfBus1GenVoltage.getBranches().get(0), lfBus2SvcVoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        OpenBranchSide2ReactiveFlowEquationTerm openBranchSide2ReactiveFlowEquationTerm =
                new OpenBranchSide2ReactiveFlowEquationTerm(lfBus1GenVoltage.getBranches().get(0), lfBus1GenVoltage, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        BusVoltageEquationTerm busVoltageEquationTerm = new BusVoltageEquationTerm(lfBus1GenVoltage, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet());

        // assertions
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(closedBranchSide1ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(closedBranchSide2ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(openBranchSide1ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(openBranchSide2ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(shuntCompensatorReactiveFlowEquationTerm));
        shuntCompensatorReactiveFlowEquationTerm.setActive(false);
        Assertions.assertFalse(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(shuntCompensatorReactiveFlowEquationTerm));
        Assertions.assertFalse(staticVarCompensatorVoltageLambdaQEquationTerm.hasToEvalAndDerTerm(busVoltageEquationTerm));
    }

    @Test
    void hasPhiVarTest() {
        // build equation terms
        ClosedBranchSide1ReactiveFlowEquationTerm closedBranchSide1ReactiveFlowEquationTerm =
                new ClosedBranchSide1ReactiveFlowEquationTerm(lfBus1GenVoltage.getBranches().get(0), lfBus1GenVoltage, lfBus2SvcVoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        ClosedBranchSide2ReactiveFlowEquationTerm closedBranchSide2ReactiveFlowEquationTerm =
                new ClosedBranchSide2ReactiveFlowEquationTerm(lfBus1GenVoltage.getBranches().get(0), lfBus1GenVoltage, lfBus2SvcVoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet(), false, false);
        ShuntCompensatorReactiveFlowEquationTerm shuntCompensatorReactiveFlowEquationTerm =
                new ShuntCompensatorReactiveFlowEquationTerm(lfBus2SvcVoltageWithSlope.getShunts().get(0), lfBus2SvcVoltageWithSlope, loadFlowTestToolsSvcVoltageWithSlope.getVariableSet());

        // assertions
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasPhiVar(closedBranchSide1ReactiveFlowEquationTerm));
        Assertions.assertTrue(staticVarCompensatorVoltageLambdaQEquationTerm.hasPhiVar(closedBranchSide2ReactiveFlowEquationTerm));
        Assertions.assertFalse(staticVarCompensatorVoltageLambdaQEquationTerm.hasPhiVar(shuntCompensatorReactiveFlowEquationTerm));
    }

    @Test
    void evalAndDerOnTermsFromEquationBusQTest() {
        // equation without term to eval
        Equation equation = equationSystem.createEquation(lfBus1GenVoltage.getNum(), EquationType.BUS_PHI);
        StaticVarCompensatorVoltageLambdaQEquationTerm.EvalAndDerOnTermsFromEquationBUSQ evalAndDerOnTermsFromEquationBUSQ = staticVarCompensatorVoltageLambdaQEquationTerm.evalAndDerOnTermsFromEquationBUSQ(new double[]{1, 0, 1, 0}, equation);
        Assertions.assertEquals(0, evalAndDerOnTermsFromEquationBUSQ.qBusMinusShunts);
        Assertions.assertEquals(0, evalAndDerOnTermsFromEquationBUSQ.dQdVbusMinusShunts);
        Assertions.assertEquals(0, evalAndDerOnTermsFromEquationBUSQ.dQdPHbusMinusShunts);
    }

    @Test
    void getSumQgeneratorsWithoutVoltageRegulatorTest() {
        // 1 svc with reactive regulation + 1 svc with voltage regulation + 1 generator without voltage regulation + 1 generator with voltage regulation
        NetworkBuilder nb = new NetworkBuilder();
        LoadFlowTestTools loadFlowTestTools = new LoadFlowTestTools(nb.addNetworkWithGenOnBus1AndSvcOnBus2().
                setSvcRegulationModeOnBus2(StaticVarCompensator.RegulationMode.REACTIVE_POWER).
                addSvcWithVoltageAndSlopeOnBus2(1, new double[]{385}, new double[]{0.01}, new double[]{-0.001}, new double[]{0.001}).
                addGenWithoutVoltageRegulatorOnBus2().addGenWithVoltageRegulatorOnBus2().build());
        equationSystem = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem();
        StaticVarCompensatorVoltageLambdaQEquationTerm equationTerm =
                equationSystem.getEquationTerm(SubjectType.BUS, loadFlowTestTools.getLfNetwork().getBusById("vl2_0").getNum(), StaticVarCompensatorVoltageLambdaQEquationTerm.class);
        Assertions.assertEquals(3, equationTerm.getSumQgeneratorsWithoutVoltageRegulator());
    }

    @Test
    void rhsTest() {
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.rhs());
    }

    @Test
    void getNameTest() {
        Assertions.assertEquals("ac_static_var_compensator_with_slope", staticVarCompensatorVoltageLambdaQEquationTerm.getName());
    }
}
