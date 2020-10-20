/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcloadFlowEngine implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final LfNetwork network;

    private final AcLoadFlowParameters parameters;

    private final VariableSet variableSet;

    private final EquationSystem equationSystem;

    private final JacobianMatrixCache jacobianMatrixCache;

    public AcloadFlowEngine(LfNetwork network, AcLoadFlowParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);

        this.parameters.getObserver().beforeEquationSystemCreation();

        variableSet = new VariableSet();
        AcEquationSystemCreationParameters creationParameters = new AcEquationSystemCreationParameters(parameters.isVoltageRemoteControl(), parameters.isPhaseControl());
        equationSystem = AcEquationSystem.create(network, variableSet, creationParameters);
        jacobianMatrixCache = new JacobianMatrixCache(equationSystem, getParameters().getMatrixFactory());

        this.parameters.getObserver().afterEquationSystemCreation();
    }

    public static List<LfNetwork> createNetworks(Object network, AcLoadFlowParameters parameters) {
        parameters.getObserver().beforeNetworksCreation();

        List<LfNetwork> networks = LfNetwork.load(network, new LfNetworkParameters(parameters.getSlackBusSelector(), parameters.isVoltageRemoteControl(),
                parameters.isMinImpedance(), parameters.isTwtSplitShuntAdmittance(), parameters.isBreakers()));

        parameters.getObserver().afterNetworksCreation(networks);

        return networks;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public AcLoadFlowParameters getParameters() {
        return parameters;
    }

    public VariableSet getVariableSet() {
        return variableSet;
    }

    public EquationSystem getEquationSystem() {
        return equationSystem;
    }

    private void updatePvBusesReactivePower(NewtonRaphsonResult lastNrResult, LfNetwork network, EquationSystem equationSystem) {
        if (lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            parameters.getObserver().beforePvBusesReactivePowerUpdate();

            for (LfBus bus : network.getBuses()) {
                if (bus.hasVoltageControl()) {
                    Equation q = equationSystem.createEquation(bus.getNum(), EquationType.BUS_Q);
                    bus.setCalculatedQ(q.eval());
                } else {
                    bus.setCalculatedQ(Double.NaN);
                }
            }

            parameters.getObserver().afterPvBusesReactivePowerUpdate();
        }
    }

    private static class RunningContext {

        private NewtonRaphsonResult lastNrResult;

        private final Map<String, MutableInt> outerLoopIterationByType = new HashMap<>();
    }

    private void runOuterLoop(OuterLoop outerLoop, LfNetwork network, EquationSystem equationSystem, VariableSet variableSet,
                              NewtonRaphson newtonRaphson, NewtonRaphsonParameters nrParameters, RunningContext runningContext) {
        // for each outer loop re-run Newton-Raphson until stabilization
        OuterLoopStatus outerLoopStatus;
        do {
            MutableInt outerLoopIteration = runningContext.outerLoopIterationByType.computeIfAbsent(outerLoop.getType(), k -> new MutableInt());

            parameters.getObserver().beforeOuterLoopStatusCheck(outerLoopIteration.getValue(), outerLoop.getType());

            // check outer loop status
            outerLoopStatus = outerLoop.check(new OuterLoopContext(outerLoopIteration.getValue(), network, equationSystem, variableSet, runningContext.lastNrResult));

            parameters.getObserver().afterOuterLoopStatusCheck(outerLoopIteration.getValue(), outerLoop.getType(), outerLoopStatus == OuterLoopStatus.STABLE);

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                parameters.getObserver().beforeOuterLoopBody(outerLoopIteration.getValue(), outerLoop.getType());

                // if not yet stable, restart Newton-Raphson
                runningContext.lastNrResult = newtonRaphson.run(nrParameters);
                if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                    return;
                }

                parameters.getObserver().afterOuterLoopBody(outerLoopIteration.getValue(), outerLoop.getType());

                // update PV buses reactive power some outer loops might need this information
                updatePvBusesReactivePower(runningContext.lastNrResult, network, equationSystem);

                outerLoopIteration.increment();
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE);
    }

    public AcLoadFlowResult run() {
        LOGGER.info("Start AC loadflow on network {}", network.getNum());

        parameters.getObserver().beforeLoadFlow(network);

        RunningContext runningContext = new RunningContext();
        NewtonRaphson newtonRaphson = new NewtonRaphson(network, parameters.getMatrixFactory(), parameters.getObserver(),
                                                        equationSystem, jacobianMatrixCache, parameters.getStoppingCriteria());

        NewtonRaphsonParameters nrParameters = new NewtonRaphsonParameters().setVoltageInitializer(parameters.getVoltageInitializer());

        // run initial Newton-Raphson
        runningContext.lastNrResult = newtonRaphson.run(nrParameters);

        // continue with outer loops only if initial Newton-Raphson succeed
        if (runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED) {
            updatePvBusesReactivePower(runningContext.lastNrResult, network, equationSystem);

            // re-run all outer loops until Newton-Raphson failed or no more Newton-Raphson iterations are needed
            int oldIterationCount;
            do {
                oldIterationCount = runningContext.lastNrResult.getIteration();

                // outer loops are nested: inner most loop first in the list, outer most loop last
                for (OuterLoop outerLoop : parameters.getOuterLoops()) {
                    runOuterLoop(outerLoop, network, equationSystem, variableSet, newtonRaphson, nrParameters, runningContext);

                    // continue with next outer loop only if last Newton-Raphson succeed
                    if (runningContext.lastNrResult.getStatus() != NewtonRaphsonStatus.CONVERGED) {
                        break;
                    }
                }
            } while (runningContext.lastNrResult.getIteration() > oldIterationCount
                    && runningContext.lastNrResult.getStatus() == NewtonRaphsonStatus.CONVERGED);
        }

        int nrIterations = runningContext.lastNrResult.getIteration();
        int outerLoopIterations = runningContext.outerLoopIterationByType.values().stream().mapToInt(MutableInt::getValue).sum() + 1;

        parameters.getObserver().afterLoadFlow(network);

        AcLoadFlowResult result = new AcLoadFlowResult(network, outerLoopIterations, nrIterations, runningContext.lastNrResult.getStatus(),
                runningContext.lastNrResult.getSlackBusActivePowerMismatch());

        LOGGER.info("Ac loadflow complete on network {} (result={})", network.getNum(), result);

        return result;
    }

    @Override
    public void close() {
        jacobianMatrixCache.release();
    }

    public static List<AcLoadFlowResult> run(Object network, AcLoadFlowParameters parameters) {
        return createNetworks(network, parameters)
                .stream()
                .map(n -> {
                    try (AcloadFlowEngine engine = new AcloadFlowEngine(n, parameters)) {
                        return engine.run();
                    }
                })
                .collect(Collectors.toList());
    }
}
