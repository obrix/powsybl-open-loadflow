/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcLoadFlowVscTest {

    @Test
    void test() {
        Network network = HvdcNetworkFactory.createVsc();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                                             .setDistributedSlack(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);

        Bus bus2 = network.getBusView().getBus("vl2_0");
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116917, bus2);

        Bus bus3 = network.getBusView().getBus("vl3_0");
        assertVoltageEquals(383, bus3);
        assertAngleEquals(0, bus3);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-102.56, g1.getTerminal());
        assertReactivePowerEquals(-615.733, g1.getTerminal());

        VscConverterStation cs2 = network.getVscConverterStation("cs2");
        assertActivePowerEquals(50.55, cs2.getTerminal());
        assertReactivePowerEquals(598.046, cs2.getTerminal());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(-49.90, cs3.getTerminal());
        assertReactivePowerEquals(-10.0, cs3.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(103.112, l12.getTerminal1());
        assertReactivePowerEquals(615.733, l12.getTerminal1());
        assertActivePowerEquals(-100.55, l12.getTerminal2());
        assertReactivePowerEquals(-608.046, l12.getTerminal2());
    }

    @Test
    void testOpen() {
        Network network = HvdcNetworkFactory.createVsc();
        network.getHvdcLine("hvdc23").getConverterStation2().getTerminal().disconnect();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);

        Bus bus2 = network.getBusView().getBus("vl2_0");
        assertVoltageEquals(389.794, bus2);
        assertAngleEquals(-0.052765, bus2);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-50.017, g1.getTerminal());
        assertReactivePowerEquals(-10.051, g1.getTerminal());

        VscConverterStation cs2 = network.getVscConverterStation("cs2");
        assertActivePowerEquals(0, cs2.getTerminal());
        assertReactivePowerEquals(0, cs2.getTerminal());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertTrue(Double.isNaN(cs3.getTerminal().getP()));
        assertTrue(Double.isNaN(cs3.getTerminal().getQ()));

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(50.017, l12.getTerminal1());
        assertReactivePowerEquals(10.051, l12.getTerminal1());
        assertActivePowerEquals(-50, l12.getTerminal2());
        assertReactivePowerEquals(-10, l12.getTerminal2());
    }
}
