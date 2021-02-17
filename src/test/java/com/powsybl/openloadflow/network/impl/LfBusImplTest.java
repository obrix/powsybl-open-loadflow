package com.powsybl.openloadflow.network.impl;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.openloadflow.ac.util.NetworkBuilder;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.util.LoadFlowTestTools;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.LoadFlowAssert.DELTA_POWER;

class LfBusImplTest {
    private Bus createBus() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        StaticVarCompensator svc1 = vl1.newStaticVarCompensator()
                .setId("svc1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.006)
                .setBmax(0.006)
                .add();
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.01)
                .add();
        StaticVarCompensator svc2 = vl1.newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.001)
                .setBmax(0.001)
                .add();
        svc2.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.015)
                .add();
        StaticVarCompensator svc3 = vl1.newStaticVarCompensator()
                .setId("svc3")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.00075)
                .setBmax(0.00075)
                .add();
        svc3.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.02)
                .add();
        return bus;
    }

    @Test
    void updateGeneratorsStateTest() {
        Bus bus = createBus();
        LfBusImpl lfBus = new LfBusImpl(bus, 385, 0);
        LfNetworkLoadingReport lfNetworkLoadingReport = new LfNetworkLoadingReport();
        for (StaticVarCompensator staticVarCompensator : bus.getStaticVarCompensators()) {
            lfBus.addStaticVarCompensator(staticVarCompensator, 1.0, lfNetworkLoadingReport);
        }
        double generationQ = -6.412103131789854;
        lfBus.updateGeneratorsState(generationQ * PerUnit.SB, true);
        double sumQ = lfBus.getGenerators().stream().mapToDouble(LfGenerator::getCalculatedQ).sum();
        Assertions.assertEquals(generationQ, sumQ, DELTA_POWER, "sum of generators calculatedQ should be equals to qToDispatch");
    }

    @Test
    void setVoltageControlTest() {
        Bus bus = createBus();
        LfBusImpl lfBus = new LfBusImpl(bus, 385, 0);
        lfBus.setVoltageControl(true);
        lfBus.setVoltageControl(true);
        lfBus.setVoltageControl(false);
        lfBus.setVoltageControl(false);
        Assertions.assertEquals(1, lfBus.getVoltageControlSwitchOffCount());
    }

    @Test
    void setControlledBus() {
        NetworkBuilder networkBuilder = new NetworkBuilder();
        LoadFlowTestTools loadFlowTestTools = new LoadFlowTestTools(networkBuilder.addNetworkWithGenOnBus1AndSvcOnBus2().build());
        LfBusImpl bus1 = (LfBusImpl) loadFlowTestTools.getLfNetwork().getBusById("vl1_0");
        LfBusImpl bus2 = (LfBusImpl) loadFlowTestTools.getLfNetwork().getBusById("vl2_0");
        Optional<LfBus> controlledBus = bus1.getControlledBus();
        bus1.setControlledBus(bus1);
        bus1.setControlledBus(bus1);
        Assertions.assertThrows(PowsyblException.class, () -> bus1.setControlledBus(bus2));
    }

    @Test
    void getFixedLoadTargetQTest() {
        NetworkBuilder networkBuilder = new NetworkBuilder();
        LoadFlowTestTools loadFlowTestTools = new LoadFlowTestTools(networkBuilder.
                addNetworkWithGenOnBus1AndSvcOnBus2().addLoadOnBus2().setLoadDetailOnBus2().build());
        LfBusImpl bus1 = (LfBusImpl) loadFlowTestTools.getLfNetwork().getBusById("vl1_0");
        LfBusImpl bus2 = (LfBusImpl) loadFlowTestTools.getLfNetwork().getBusById("vl2_0");
        Assertions.assertEquals(0, bus1.getFixedLoadTargetQ());
        Assertions.assertEquals(1.5, bus2.getFixedLoadTargetQ());
    }

    @Test
    void dispatchQAccordingToSvcSlopeTest() {
        NetworkBuilder networkBuilder = new NetworkBuilder();
        LoadFlowTestTools loadFlowTestTools = new LoadFlowTestTools(networkBuilder.addNetworkWithGenOnBus1AndSvcOnBus2().setSvcVoltageAndSlopeOnBus2().build());
        LfBusImpl bus2 = (LfBusImpl) loadFlowTestTools.getLfNetwork().getBusById("vl2_0");
        bus2.setV(385 / bus2.getNominalV());

        List<LfStaticVarCompensatorImpl> staticVarCompensators = bus2.getGenerators().stream().filter(lfGenerator -> lfGenerator instanceof LfStaticVarCompensatorImpl)
                .map(LfStaticVarCompensatorImpl.class::cast)
                .collect(Collectors.toList());
        LfStaticVarCompensatorImpl lfStaticVarCompensator = staticVarCompensators.get(0);

        // empty svc list
        bus2.dispatchQAccordingToSvcSlope(new ArrayList<>(), true, 0);

        // no qToDispatch
        lfStaticVarCompensator.setCalculatedQ(0);
        bus2.dispatchQAccordingToSvcSlope(staticVarCompensators, true, 0);
        Assertions.assertEquals(0, lfStaticVarCompensator.getCalculatedQ());

        // with a qToDispatch not null
        lfStaticVarCompensator.setCalculatedQ(0);
        bus2.dispatchQAccordingToSvcSlope(staticVarCompensators, false, 1);
        Assertions.assertEquals(1, lfStaticVarCompensator.getCalculatedQ());

        // with a qToDispatch not null and limit checked and not exceeded
        lfStaticVarCompensator.setCalculatedQ(0);
        bus2.dispatchQAccordingToSvcSlope(staticVarCompensators, true, 1);
        Assertions.assertEquals(1, lfStaticVarCompensator.getCalculatedQ());

        // with a qToDispatch not null and limit checked and exceeded
        lfStaticVarCompensator.setCalculatedQ(0);
        lfStaticVarCompensator.getSvc().setBmin(-0.0001);
        lfStaticVarCompensator.getSvc().setBmax(0.0001);
        bus2.dispatchQAccordingToSvcSlope(staticVarCompensators, true, 1);
        Assertions.assertEquals(0.148225, lfStaticVarCompensator.getCalculatedQ());
    }
}
