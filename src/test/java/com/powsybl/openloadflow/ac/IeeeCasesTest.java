/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.io.table.AsciiTableFormatter;
import com.powsybl.commons.io.table.Column;
import com.powsybl.commons.io.table.TableFormatter;
import com.powsybl.commons.io.table.TableFormatterConfig;
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
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class IeeeCasesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(IeeeCasesTest.class);

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    private static LoadFlowParameters createParameters(String slackBusId) {
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector(slackBusId))
                .setDistributedSlack(false);
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

    private void testIeee(Network network, String slackBusId, double epsV, double epsAngle) {
        Map<String, Pair<Double, Double>> initialVoltages = extractVoltages(network);
        LoadFlowParameters parameters = createParameters(slackBusId);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        Map<String, Pair<Double, Double>> olfVoltages = extractVoltages(network);
        StringWriter writer = new StringWriter();
        try (TableFormatter tableFormatter = new AsciiTableFormatter(writer, "Voltage diff", new TableFormatterConfig(),
                     new Column("bus ID"), new Column("v0"), new Column("v"), new Column("dv"),
                new Column("a0"), new Column("a"), new Column("da"))) {
            for (Map.Entry<String, Pair<Double, Double>> e : initialVoltages.entrySet()) {
                String busId = e.getKey();
                double initialV = e.getValue().getLeft();
                double initialAngle = e.getValue().getRight();
                double v = olfVoltages.get(busId).getLeft();
                double angle = olfVoltages.get(busId).getRight();
//                assertEquals(initialV, v, epsV);
//                assertEquals(initialAngle, angle, epsAngle);
                tableFormatter.writeCell(busId)
                        .writeCell(initialV)
                        .writeCell(v)
                        .writeCell(v - initialV)
                        .writeCell(initialAngle)
                        .writeCell(angle)
                        .writeCell(angle - initialAngle);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        writer.flush();
        LOGGER.info(writer.toString());
    }

    @Test
    public void testIeee9() {
        testIeee(IeeeCdfNetworkFactory.create9(), "VL1_0", 10e-2, 10e-4);
    }

    @Test
    @Ignore
    public void testIeee14() {
        testIeee(IeeeCdfNetworkFactory.create14(), "VL1_0", 10e-4, 10e-4);
    }

    @Test
    @Ignore
    public void testIeee30() {
        testIeee(IeeeCdfNetworkFactory.create30(), "VL1_0", 10e-2, 10e-4);
    }

    @Test
    @Ignore
    public void testIeee57() {
        testIeee(IeeeCdfNetworkFactory.create57(), "VL1_0", 10e-4, 10e-4);
    }

    @Test
    @Ignore
    public void testIeee118() {
        testIeee(IeeeCdfNetworkFactory.create118(), "VL69_0", 10e-4, 10e-4);
    }

    @Test
    @Ignore
    public void testIeee300() {
        testIeee(IeeeCdfNetworkFactory.create300(), "VL1_0", 10e-2, 10e-4);
    }
}
