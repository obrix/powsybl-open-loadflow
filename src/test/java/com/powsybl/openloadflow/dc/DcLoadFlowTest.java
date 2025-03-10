/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.usefultoys.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sylvain Leclerc <sylvain.leclerc at rte-france.com>
 */
class DcLoadFlowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowTest.class);

    private LoadFlowParameters parameters;

    private OpenLoadFlowProvider loadFlowProvider;

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters()
                .setDc(true);
        parameters.addExtension(OpenLoadFlowParameters.class, new OpenLoadFlowParameters()
                .setSlackBusSelector(new FirstSlackBusSelector()));
        loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        loadFlowRunner = new LoadFlow.Runner(loadFlowProvider);
    }

    /**
     * Check behaviour of the load flow for simple manipulations on eurostag example 1 network.
     * - line opening
     * - load change
     */
    @Test
    void tuto1Test() {
        Network network = EurostagTutorialExample1Factory.create();
        Line line1 = network.getLine("NHV1_NHV2_1");
        Line line2 = network.getLine("NHV1_NHV2_2");

        assertEquals(Double.NaN, line1.getTerminal1().getP(), 0);
        assertEquals(Double.NaN, line1.getTerminal2().getP(), 0);
        assertEquals(Double.NaN, line2.getTerminal1().getP(), 0);
        assertEquals(Double.NaN, line2.getTerminal2().getP(), 0);

        loadFlowRunner.run(network, parameters);

        assertEquals(300, line1.getTerminal1().getP(), 0.01);
        assertEquals(-300, line1.getTerminal2().getP(), 0.01);
        assertEquals(300, line2.getTerminal1().getP(), 0.01);
        assertEquals(-300, line2.getTerminal2().getP(), 0.01);

        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();

        loadFlowRunner.run(network, parameters);

        assertTrue(Double.isNaN(line1.getTerminal1().getP()));
        assertEquals(0, line1.getTerminal2().getP(), 0);
        assertEquals(600, line2.getTerminal1().getP(), 0.01);
        assertEquals(-600, line2.getTerminal2().getP(), 0.01);

        network.getLine("NHV1_NHV2_1").getTerminal1().connect();
        network.getLine("NHV1_NHV2_1").getTerminal2().disconnect();

        loadFlowRunner.run(network, parameters);

        assertEquals(0, line1.getTerminal1().getP(), 0);
        assertTrue(Double.isNaN(line1.getTerminal2().getP()));
        assertEquals(600, line2.getTerminal1().getP(), 0.01);
        assertEquals(-600, line2.getTerminal2().getP(), 0.01);

        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();
        network.getLoad("LOAD").setP0(450);

        loadFlowRunner.run(network, parameters);

        assertTrue(Double.isNaN(line1.getTerminal1().getP()));
        assertTrue(Double.isNaN(line1.getTerminal2().getP()));
        assertEquals(450, line2.getTerminal1().getP(), 0.01);
        assertEquals(-450, line2.getTerminal2().getP(), 0.01);
    }

    @Test
    void fourBusesTest() {
        Network network = FourBusNetworkFactory.create();

        loadFlowRunner.run(network, parameters);

        Line l14 = network.getLine("l14");
        Line l12 = network.getLine("l12");
        Line l23 = network.getLine("l23");
        Line l34 = network.getLine("l34");
        Line l13 = network.getLine("l13");

        assertEquals(0.25, l14.getTerminal1().getP(), 0.01);
        assertEquals(-0.25, l14.getTerminal2().getP(), 0.01);
        assertEquals(0.25, l12.getTerminal1().getP(), 0.01);
        assertEquals(-0.25, l12.getTerminal2().getP(), 0.01);
        assertEquals(1.25, l23.getTerminal1().getP(), 0.01);
        assertEquals(-1.25, l23.getTerminal2().getP(), 0.01);
        assertEquals(-1.25, l34.getTerminal1().getP(), 0.01);
        assertEquals(1.25, l34.getTerminal2().getP(), 0.01);
        assertEquals(1.5, l13.getTerminal1().getP(), 0.01);
        assertEquals(-1.5, l13.getTerminal2().getP(), 0.01);
    }

    @Test
    void phaseShifterTest() {
        Network network = PhaseShifterTestCaseFactory.create();
        Line l1 = network.getLine("L1");
        Line l2 = network.getLine("L2");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger().getStep(0).setAlpha(5);
        ps1.getPhaseTapChanger().getStep(2).setAlpha(5);

        loadFlowRunner.run(network, parameters);

        assertEquals(50, l1.getTerminal1().getP(), 0.01);
        assertEquals(-50, l1.getTerminal2().getP(), 0.01);
        assertEquals(50, l2.getTerminal1().getP(), 0.01);
        assertEquals(-50, l2.getTerminal2().getP(), 0.01);
        assertEquals(50, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-50, ps1.getTerminal2().getP(), 0.01);

        ps1.getPhaseTapChanger().setTapPosition(2);

        loadFlowRunner.run(network, parameters);

        assertEquals(18.5, l1.getTerminal1().getP(), 0.01);
        assertEquals(-18.5, l1.getTerminal2().getP(), 0.01);
        assertEquals(81.5, l2.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, l2.getTerminal2().getP(), 0.01);
        assertEquals(81.5, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, ps1.getTerminal2().getP(), 0.01);

        // check we have same result if we consider phase shift as a variable with a fixed value
        loadFlowProvider.setForcePhaseControlOffAndAddAngle1Var(true);

        loadFlowRunner.run(network, parameters);

        assertEquals(18.5, l1.getTerminal1().getP(), 0.01);
        assertEquals(-18.5, l1.getTerminal2().getP(), 0.01);
        assertEquals(81.5, l2.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, l2.getTerminal2().getP(), 0.01);
        assertEquals(81.5, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, ps1.getTerminal2().getP(), 0.01);
    }
}
