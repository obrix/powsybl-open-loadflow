/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class IeeeCdfTest {

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    private static LoadFlowParameters createParameters(String slackBusId) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector(slackBusId))
                .setDistributedSlack(false)
                .setReactiveLimits(false);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        return parameters;
    }

    private static Map<String, Pair<Double, Double>> extractVoltages(Network network) {
        Map<String, Pair<Double, Double>> voltages = new TreeMap<>();
        for (Bus bus : network.getBusView().getBuses()) {
            voltages.put(bus.getId(), Pair.of(bus.getV(), bus.getAngle()));
        }
        return voltages;
    }

    @Test
    public void testIeee14() {
        Network network = IeeeCdfNetworkFactory.create14();
        Map<String, Pair<Double, Double>> initialVoltages = extractVoltages(network);
        LoadFlowParameters parameters = createParameters("VL1_0");
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        Map<String, Pair<Double, Double>> olfVoltages = extractVoltages(network);
        for (Map.Entry<String, Pair<Double, Double>> e : initialVoltages.entrySet()) {
            String busId = e.getKey();
            double initialV = e.getValue().getLeft();
            double initialAngle = e.getValue().getRight();
            double v = olfVoltages.get(busId).getLeft();
            double angle = olfVoltages.get(busId).getRight();
            System.out.println(busId + ": " + initialV + " " + v);
        }
    }
}
