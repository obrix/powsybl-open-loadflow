package com.powsybl.openloadflow.ac.util;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;

import java.util.ArrayList;
import java.util.List;

public class NetworkBuilder {
    private Network network;
    private VoltageLevel vl1;
    private Bus bus1;
    private VoltageLevel vl2;
    private Bus bus2;
    private Line lineBetweenBus1AndBus2;
    private Generator genOnBus1;
    private Load loadOnBus2;
    private StaticVarCompensator svcOnBus2;
    private List<StaticVarCompensator> additionnalSvcOnBus2 = new ArrayList<>();
    private Generator genOnBus2;
    private ShuntCompensator shuntCompensatorOnBus2;

    public NetworkBuilder addNetworkWithGenOnBus1AndSvcOnBus2() {
        network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("s1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("s2")
                .add();
        vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("bus1")
                .add();
        genOnBus1 = vl1.newGenerator()
                .setId("bus1gen")
                .setConnectableBus(bus1.getId())
                .setBus(bus1.getId())
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("bus2")
                .add();
        svcOnBus2 = vl2.newStaticVarCompensator()
                .setId("bus2svc")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        lineBetweenBus1AndBus2 = network.newLine()
                .setId("bus1bus2line")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        return this;
    }

    public NetworkBuilder addLoadOnBus2() {
        loadOnBus2 = vl2.newLoad()
                .setId("bus2ld")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setP0(101)
                .setQ0(150)
                .add();
        return this;
    }

    public NetworkBuilder setSvcVoltageAndSlopeOnBus2() {
        svcOnBus2.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.01)
                .add();
        return this;
    }

    public NetworkBuilder setSvcRegulationModeOnBus2(StaticVarCompensator.RegulationMode regulationMode) {
        switch (regulationMode) {
            case VOLTAGE:
                svcOnBus2.setVoltageSetpoint(385)
                        .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
                break;
            case REACTIVE_POWER:
                svcOnBus2.setReactivePowerSetpoint(300)
                        .setRegulationMode(StaticVarCompensator.RegulationMode.REACTIVE_POWER);
                break;
            case OFF:
                svcOnBus2.setRegulationMode(StaticVarCompensator.RegulationMode.OFF);
                break;
        }
        return this;
    }

    public NetworkBuilder addMoreSvcWithVoltageAndSlopeOnBus2(double slope) {
        StaticVarCompensator svc = vl2.newStaticVarCompensator()
                .setId("bus2svc2")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        svc.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(slope)
                .add();
        additionnalSvcOnBus2.add(svc);
        return this;
    }

    public NetworkBuilder addGenWithoutVoltageRegulatorOnBus2() {
        genOnBus2 = bus2.getVoltageLevel()
                .newGenerator()
                .setId("bus2gen")
                .setBus(bus2.getId())
                .setConnectableBus(bus2.getId())
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(100)
                .setMaxP(300)
                .setTargetP(0)
                .setTargetQ(300)
                .setVoltageRegulatorOn(false)
                .add();
        return this;
    }

    public NetworkBuilder setGenRegulationModeOnBus2(boolean voltageRegulatorOn) {
        if (voltageRegulatorOn) {
            genOnBus2.setTargetV(385);
        } else {
            genOnBus2.setTargetQ(300);
        }
        genOnBus2.setVoltageRegulatorOn(voltageRegulatorOn);
        return this;
    }

    public NetworkBuilder addShuntCompensatorOnBus2() {
        shuntCompensatorOnBus2 = bus2.getVoltageLevel().newShuntCompensator()
                .setId("bus2sc")
                .setBus(bus2.getId())
                .setConnectableBus(bus2.getId())
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(Math.pow(10, -4))
                .setMaximumSectionCount(1)
                .add()
                .add();
        return this;
    }

    /**
     *
     * @param bus bus1 or bus2
     * @return
     */
    private NetworkBuilder addOpenLine(String bus) {
        network.newLine()
                .setId(bus + "openLine")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus.equals("bus1") ? bus1.getId() : null)
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus.equals("bus2") ? bus2.getId() : null)
                .setConnectableBus2(bus2.getId())
                .setR(1)
                .setX(3)
                .setG1(1E-6d)
                .setG2(1E-6d)
                .setB1(1E-6d)
                .setB2(1E-6d)
                .add();
        return this;
    }

    public NetworkBuilder addOpenLineOnBus1() {
        return addOpenLine("bus1");
    }

    public NetworkBuilder addOpenLineOnBus2() {
        return addOpenLine("bus2");
    }

    public Network build() {
        return network;
    }

    public VoltageLevel getVl1() {
        return vl1;
    }

    public Bus getBus1() {
        return bus1;
    }

    public VoltageLevel getVl2() {
        return vl2;
    }

    public Bus getBus2() {
        return bus2;
    }

    public Line getLineBetweenBus1AndBus2() {
        return lineBetweenBus1AndBus2;
    }

    public Generator getGenOnBus1() {
        return genOnBus1;
    }

    public StaticVarCompensator getSvcOnBus2() {
        return svcOnBus2;
    }

    public List<StaticVarCompensator> getAdditionnalSvcOnBus2() {
        return additionnalSvcOnBus2;
    }

    public Generator getGenOnBus2() {
        return genOnBus2;
    }

    public ShuntCompensator getShuntCompensatorOnBus2() {
        return shuntCompensatorOnBus2;
    }
}
