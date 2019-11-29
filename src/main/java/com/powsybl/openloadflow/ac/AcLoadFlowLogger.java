/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.nr.DefaultAcLoadFlowObserver;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowLogger extends DefaultAcLoadFlowObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowLogger.class);

    @Override
    public void beforeOuterLoopBody(int outerLoopIteration, String outerLoopName) {
        LOGGER.debug("Start outer loop iteration {} (name='{}')", outerLoopIteration, outerLoopName);
    }

    @Override
    public void beginIteration(int iteration) {
        LOGGER.debug("Start iteration {}", iteration);
    }

    @Override
    public void beforeStoppingCriteriaEvaluation(double[] mismatch, EquationSystem equationSystem, int iteration) {
        logLargestMismatches(mismatch, equationSystem, 5);
    }

    @Override
    public void afterStoppingCriteriaEvaluation(double norm, int iteration) {
        LOGGER.debug("|f(x)|={}", norm);
    }

    public void logLargestMismatches(double[] mismatch, EquationSystem equationSystem, int count) {
        if (LOGGER.isDebugEnabled()) {
            Map<Equation, Double> mismatchByEquation = new HashMap<>(equationSystem.getSortedEquationsToSolve().size());
            for (Equation equation : equationSystem.getSortedEquationsToSolve()) {
                mismatchByEquation.put(equation, mismatch[equation.getRow()]);
            }
            mismatchByEquation.entrySet().stream()
                    .filter(e -> Math.abs(e.getValue()) > Math.pow(10, -7))
                    .sorted(Comparator.comparingDouble((Map.Entry<Equation, Double> e) -> Math.abs(e.getValue())).reversed())
                    .limit(count)
                    .forEach(e -> LOGGER.debug("Mismatch for {}: {}", e.getKey(), e.getValue()));
        }
    }
}
