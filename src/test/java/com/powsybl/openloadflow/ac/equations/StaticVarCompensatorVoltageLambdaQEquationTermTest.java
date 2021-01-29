package com.powsybl.openloadflow.ac.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.StaticVarCompensator;
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
                loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().getEquationTerm(SubjectType.BUS, lfBus2SvcVoltageWithSlope.getNum(), StaticVarCompensatorVoltageLambdaQEquationTerm.class);
    }

    @Test
    void staticVarCompensatorVoltageLambdaQEquationTermTest() {
        Assertions.assertThrows(PowsyblException.class, () -> new StaticVarCompensatorVoltageLambdaQEquationTerm(null, lfBus2SvcVoltageWithSlope, variableSet, equationSystem));
        Assertions.assertThrows(PowsyblException.class, () -> new StaticVarCompensatorVoltageLambdaQEquationTerm(new ArrayList<LfStaticVarCompensatorImpl>(), lfBus2SvcVoltageWithSlope, variableSet, equationSystem));
        Assertions.assertThrows(PowsyblException.class, () -> new StaticVarCompensatorVoltageLambdaQEquationTerm(lfStaticVarCompensators, lfBus1GenVoltage, variableSet, equationSystem));
        lfStaticVarCompensators.get(0).getVoltagePerReactivePowerControl().setSlope(0);
        Assertions.assertThrows(PowsyblException.class, () -> new StaticVarCompensatorVoltageLambdaQEquationTerm(lfStaticVarCompensators, lfBus2SvcVoltageWithSlope, variableSet, equationSystem));
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
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(null));
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(new ArrayList<LfStaticVarCompensatorImpl>()));
        Assertions.assertEquals(0.0025, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));
        lfStaticVarCompensators.add(lfStaticVarCompensators.get(0));
        Assertions.assertEquals(0.00125, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));
        networkBuilder.addMoreSvcWithVoltageAndSlopeOnBus2(0.02);
        StaticVarCompensator bus2svc2 = networkBuilder.getAdditionnalSvcOnBus2().get(0);
        lfStaticVarCompensators.add(LfStaticVarCompensatorImpl.create(bus2svc2, (AbstractLfBus) lfBus2SvcVoltageWithSlope));
        Assertions.assertEquals(0.001, staticVarCompensatorVoltageLambdaQEquationTerm.computeSlopeStaticVarCompensators(lfStaticVarCompensators));
    }

    @Test
    void hasToEvalAndDerTermTest() {
        // build or get equation terms
        Equation equation = loadFlowTestToolsSvcVoltageWithSlope.getEquationSystem().createEquation(lfBus2SvcVoltageWithSlope.getNum(), EquationType.BUS_Q);
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
    void rhsTest() {
        Assertions.assertEquals(0, staticVarCompensatorVoltageLambdaQEquationTerm.rhs());
    }

    @Test
    void getNameTest() {
        Assertions.assertEquals("ac_static_var_compensator_with_slope", staticVarCompensatorVoltageLambdaQEquationTerm.getName());
    }
}
