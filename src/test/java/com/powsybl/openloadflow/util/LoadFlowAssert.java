/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.loadflow.LoadFlowResult;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class LoadFlowAssert {

    public static final double DELTA_ANGLE = 1E-6d;
    public static final double DELTA_V = 1E-3d;
    public static final double DELTA_POWER = 1E-3d;
    public static final double DELTA_I = 1000 * DELTA_POWER / Math.sqrt(3);
    public static final double DELTA_MISMATCH = 1E-4d;

    private LoadFlowAssert() {
    }

    public static void assertVoltageEquals(double v, Bus bus) {
        assertEquals(v, bus.getV(), DELTA_V);
    }

    public static void assertAngleEquals(double a, Bus bus) {
        assertEquals(a, bus.getAngle(), DELTA_ANGLE);
    }

    public static void assertActivePowerEquals(double p, Terminal terminal) {
        assertEquals(p, terminal.getP(), DELTA_POWER);
    }

    public static void assertReactivePowerEquals(double q, Terminal terminal) {
        assertEquals(q, terminal.getQ(), DELTA_POWER);
    }

    public static void assertUndefinedActivePower(Terminal terminal) {
        assertTrue(Double.isNaN(terminal.getP()));
    }

    public static void assertUndefinedReactivePower(Terminal terminal) {
        assertTrue(Double.isNaN(terminal.getQ()));
    }

    public static void assertLoadFlowResultsEquals(LoadFlowResult loadFlowResultExpected, LoadFlowResult loadFlowResult) {
        assertEquals(loadFlowResultExpected.isOk(), loadFlowResult.isOk(),
                "Wrong load flow result summary");
        assertEquals(loadFlowResultExpected.getComponentResults().size(),
                loadFlowResult.getComponentResults().size(),
                "Wrong sub network count");
        Iterator<LoadFlowResult.ComponentResult> componentResultIteratorExpected = loadFlowResultExpected.getComponentResults().iterator();
        Iterator<LoadFlowResult.ComponentResult> componentResultIterator = loadFlowResult.getComponentResults().iterator();
        // loop over sub networks
        while (componentResultIteratorExpected.hasNext()) {
            LoadFlowResult.ComponentResult componentResultExpected = componentResultIteratorExpected.next();
            LoadFlowResult.ComponentResult componentResult = componentResultIterator.next();
            assertEquals(componentResultExpected.getStatus(),
                    componentResult.getStatus(),
                    "Wrong load flow result status");
            assertEquals(componentResultExpected.getIterationCount(),
                    componentResult.getIterationCount(),
                    "Wrong iteration count");
            assertEquals(componentResultExpected.getSlackBusActivePowerMismatch(),
                    componentResult.getSlackBusActivePowerMismatch(), DELTA_MISMATCH,
                    "Wrong active power mismatch");
        }
    }
}
