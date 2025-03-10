/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class ActivePowerDistribution {

    /**
     * Active power residue epsilon: 10^-5 in p.u => 10^-3 in Mw
     */
    public static final double P_RESIDUE_EPS = Math.pow(10, -5);

    public interface Step {

        String getElementType();

        List<ParticipatingElement> getParticipatingElements(Collection<LfBus> buses);

        double run(List<ParticipatingElement> participatingElements, int iteration, double remainingMismatch);
    }

    public static class Result {

        private final int iteration;

        private final double remainingMismatch;

        public Result(int iteration, double remainingMismatch) {
            this.iteration = iteration;
            this.remainingMismatch = remainingMismatch;
        }

        public int getIteration() {
            return iteration;
        }

        public double getRemainingMismatch() {
            return remainingMismatch;
        }
    }

    private final ActivePowerDistribution.Step step;

    private ActivePowerDistribution(Step step) {
        this.step = Objects.requireNonNull(step);
    }

    public String getElementType() {
        return step.getElementType();
    }

    public Result run(LfNetwork network, double activePowerMismatch) {
        return run(network.getBuses(), activePowerMismatch);
    }

    public Result run(Collection<LfBus> buses, double activePowerMismatch) {
        List<ParticipatingElement> participatingElements = step.getParticipatingElements(buses);

        int iteration = 0;
        double remainingMismatch = activePowerMismatch;
        while (!participatingElements.isEmpty()
                && Math.abs(remainingMismatch) > P_RESIDUE_EPS) {

            remainingMismatch -= step.run(participatingElements, iteration, remainingMismatch);

            iteration++;
        }

        return new Result(iteration, remainingMismatch);
    }

    public static ActivePowerDistribution create(LoadFlowParameters.BalanceType balanceType, boolean loadPowerFactorConstant) {
        return new ActivePowerDistribution(getStep(balanceType, loadPowerFactorConstant));
    }

    public static Step getStep(LoadFlowParameters.BalanceType balanceType, boolean loadPowerFactorConstant) {
        Step step;
        switch (balanceType) {
            case PROPORTIONAL_TO_LOAD:
                step = new LoadActivePowerDistributionStep(false, loadPowerFactorConstant);
                break;
            case PROPORTIONAL_TO_CONFORM_LOAD:
                step = new LoadActivePowerDistributionStep(true, loadPowerFactorConstant);
                break;
            case PROPORTIONAL_TO_GENERATION_P_MAX:
                step = new GenerationActionPowerDistributionStep();
                break;
            case PROPORTIONAL_TO_GENERATION_P: // to be implemented.
                throw new UnsupportedOperationException("Unsupported balance type mode: " + balanceType);
            default:
                throw new UnsupportedOperationException("Unknown balance type mode: " + balanceType);
        }
        return step;
    }
}
