# PowSyBl Open Load Flow

[![Actions Status](https://github.com/powsybl/powsybl-open-loadflow/workflows/CI/badge.svg)](https://github.com/powsybl/powsybl-open-loadflow/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-open-loadflow&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-open-loadflow&metric=coverage)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-open-loadflow&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.powsybl%3Apowsybl-open-loadflow)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Join the community on Spectrum](https://withspectrum.github.io/badge/badge.svg)](https://spectrum.chat/powsybl)

PowSyBl (**Pow**er **Sy**stem **Bl**ocks) is an open source framework written in Java, that makes it easy to write complex
software for power systems’ simulations and analysis. Its modular approach allows developers to extend or customize its
features.

PowSyBl is part of the LF Energy Foundation, a project of The Linux Foundation that supports open source innovation projects
within the energy and electricity sectors.

<p align="center">
<img src="https://raw.githubusercontent.com/powsybl/powsybl-gse/master/gse-spi/src/main/resources/images/logo_lfe_powsybl.svg?sanitize=true" alt="PowSyBl Logo" width="50%"/>
</p>

Read more at https://www.powsybl.org !

This project and everyone participating in it is governed by the [PowSyBl Code of Conduct](https://github.com/powsybl/.github/blob/master/CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior to [powsybl-tsc@lists.lfenergy.org](mailto:powsybl-tsc@lists.lfenergy.org).

## PowSyBl vs PowSyBl Open Load Flow

PowSyBl Open Load Flow is an open source implementation of the load flow API that can be found in PowSyBl Core. It supports 
AC Newton-Raphson and linear DC calculation methods:
 - Fast and robust convergence, based on [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html) numerical solver; it supports both dense and sparse matrices;
 - Distributed slack (on generation or on load); it also supports non distributed slack; in both cases, the slack bus selector is configurable as explained [here](https://www.powsybl.org/pages/documentation/simulation/powerflow/openlf.html#parameters);
 - Generator active and reactive power limits (support of reactive capability curves);
 - Generator and static var compensator voltage remote control through PQV bus modelling; 
 - Shared voltage control involving generators and static var compensators;
 - 3 starting point modes: flat, warm and DC based;
 - Local and remote phase control: phase tap changers can regulate active power flows;
 - Local and remote voltage control by transformers. We also support shared controls. In case of a controlled bus that has both a voltage control by a generator and a transformer, we have decided in a first approach to discard the transformer control;
 - Non impedant branches support; we do not support loops of non impedant branches: in that case, a short number of non impedant lines will be treated with a minimal impedance;
 - HVDC and multiple synchronous component calculation.
 
PowSyBl Open Load Flow has also an implementation of the security analysis API that can be found in PowSyBl Core. It supports:
 - Network in node/breaker topology and in bus/breaker topology;
 - Contingency on branches only;
 - Permanent current limits violations detection on branches;
 - High and low voltage violations detection on buses;
 - Complex cases where the contingency leads to another synchronous component where a new resolution has to be performed are not supported at that stage.

PowSyBl Open Load Flow has also an implementation of the sensitivity analysis API that can be found in PowSyBl Core. It supports:
- AC calculation: a very minimal version that supports factors of type branch flow per injection increase;
- DC calculation: the version is much more successful that is fully documented [here](https://www.powsybl.org/pages/documentation/simulation/sensitivity/openlf.html).

Almost all of the code is written in Java. It only relies on native code for the [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html)
sparse linear solver. Linux, Windows and MacOS are supported.

## Native builds

A native build (no need to Java runtime) can be download here:
- [Linux](https://github.com/powsybl/powsybl-open-loadflow/releases/download/v0.9.0/olf-linux-0.9.0.zip)
- [MacOS](https://github.com/powsybl/powsybl-open-loadflow/releases/download/v0.9.0/olf-darwin-0.9.0.zip)
- [Windows](https://github.com/powsybl/powsybl-open-loadflow/releases/download/v0.9.0/olf-windows-0.9.0.zip)

To run Open Load Flow on file ieee14cdf.txt :
```bash
$ olf loadflow --case-file ieee14cdf.txt
Loading network 'ieee14cdf.txt'
loadflow results:
+--------+------------------------------------------------------+
| Result | Metrics                                              |
+--------+------------------------------------------------------+
| true   | {network_0_iterations=3, network_0_status=CONVERGED} |
+--------+------------------------------------------------------+
```

## Getting started

Running a load flow with PowSyBl Open Load Flow is easy. First let's start loading a IEEE 14 bus network. We first add a few Maven 
dependencies to respectively have access to network model, IEEE test networks, PowSyBl platform configuration and simple logging 
capabilities:

```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-iidm-impl</artifactId>
    <version>4.0.1</version>
</dependency>
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-ieee-cdf-converter</artifactId>
    <version>4.0.1</version>
</dependency>
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-config-classic</artifactId>
    <version>4.0.1</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>1.7.22</version>
</dependency>
```

We are now able to load the IEEE 14 bus:
 ```java
Network network = IeeeCdfNetworkFactory.create14();
 ```

After adding a last Maven dependency on Open Load Flow implementation:
```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-open-loadflow</artifactId>
    <version>0.9.0</version>
</dependency>
```

We can run the load flow with default parameters on the network:
```java
LoadFlow.run(network);
```

State variables and power flows computed by the load flow are have been updated inside the network model and we can for instance 
print on standard output buses voltage magnitude and angle:

```java
network.getBusView().getBusStream().forEach(b -> System.out.println(b.getId() + " " + b.getV() + " " + b.getAngle()));
```
## Contributing to PowSyBl Open Load Flow

PowSyBl Open Load Flow could support more features. The following list is not exhaustive and is an invitation to collaborate:

We are thinking about increasing features of the loadflow engine:
- A distributed slack that can be configured by country;
- Operational limits management;
- Improve the voltage regulation: switched shunts can have voltage control and it is an ongoing work. We also have to work on shared controls with different kinds of equipments as generators, static var compensators, tap changers and shunts;  
- Allow generators to regulate reactive power, locally or remotely;
- Support the extension ```VoltagePerReactivePowerControl``` of static var compensators as another alternative of voltage regulation (remotely and locally);
- Phase control: support of current limiter mode.

For more details, visit our [github!](https://github.com/powsybl/powsybl-open-loadflow/issues)