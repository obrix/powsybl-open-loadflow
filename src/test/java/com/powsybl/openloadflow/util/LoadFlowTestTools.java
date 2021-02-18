package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Map;

import static com.powsybl.openloadflow.util.LoadFlowAssert.DELTA_POWER;
import static org.hamcrest.MatcherAssert.assertThat;

public class LoadFlowTestTools {
    protected MatrixFactory matrixFactory;
    protected AcLoadFlowParameters acParameters;
    protected LfNetwork lfNetwork;
    protected VariableSet variableSet;
    protected EquationSystem equationSystem;
    protected JacobianMatrix jacobianMatrix;

    public LoadFlowTestTools(Network network) {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        matrixFactory = new SparseMatrixFactory();
        acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, parameters, OpenLoadFlowProvider.getParametersExt(parameters), false);
        lfNetwork = AcloadFlowEngine.createNetworks(network, acParameters).get(0);
        AcEquationSystemCreationParameters acEquationSystemCreationParameters = new AcEquationSystemCreationParameters(true, false, false, true);
        variableSet = new VariableSet();
        equationSystem = AcEquationSystem.create(lfNetwork, variableSet, acEquationSystemCreationParameters);
        equationSystem.createStateVector(new UniformValueVoltageInitializer());
        jacobianMatrix = new JacobianMatrix(equationSystem, matrixFactory);
    }

    public static boolean isClosedLine(Line line) {
        Terminal terminalONE = line.getTerminal(Branch.Side.ONE);
        if (Double.isNaN(terminalONE.getQ())) {
            return false;
        }
        Terminal terminalTWO = line.getTerminal(Branch.Side.TWO);
        if (Double.isNaN(terminalTWO.getQ())) {
            return false;
        }
        return true;
    }

    public static <N extends Enum<N>, P extends Enum<P>>void shouldHaveValidSumOfQinLines(LoadFlowRunResults<N, P> loadFlowRunResults) {
        for (N n : loadFlowRunResults.getNetworkResultByRunningParametersAndNetworkDescription().keySet()) {
            Map<P, Network> networkResultByRunningParameters = loadFlowRunResults.getNetworkResultByRunningParametersAndNetworkDescription().get(n);
            for (P p : networkResultByRunningParameters.keySet()) {
                Network network = loadFlowRunResults.getLoadFlowReport(n, p);
                for (Bus bus : network.getBusView().getBuses()) {
                    double linesTerminalQ = 0;
                    double sumItemBusQ = 0;
                    for (Generator generator : bus.getGenerators()) {
                        sumItemBusQ -= generator.getTerminal().getQ();
                    }
                    for (Load load : bus.getLoads()) {
                        sumItemBusQ -= load.getTerminal().getQ();
                    }
                    for (StaticVarCompensator staticVarCompensator : bus.getStaticVarCompensators()) {
                        sumItemBusQ -= staticVarCompensator.getTerminal().getQ();
                    }
                    for (ShuntCompensator shuntCompensator : bus.getShuntCompensators()) {
                        sumItemBusQ -= shuntCompensator.getTerminal().getQ();
                    }
                    for (Line line : bus.getLines()) {
                        Terminal terminal = line.getTerminal(bus.getId().substring(0, bus.getId().indexOf("_")));
                        linesTerminalQ += terminal.getQ();
                    }
                    assertThat("sum Q of bus items should be equals to Q line", sumItemBusQ,
                            new LoadFlowAssert.EqualsTo(linesTerminalQ, DELTA_POWER));
                }
            }
        }
    }

    public AcLoadFlowParameters getAcParameters() {
        return acParameters;
    }

    public LfNetwork getLfNetwork() {
        return lfNetwork;
    }

    public VariableSet getVariableSet() {
        return variableSet;
    }

    public EquationSystem getEquationSystem() {
        return equationSystem;
    }

    public JacobianMatrix getJacobianMatrix() {
        return jacobianMatrix;
    }
}
