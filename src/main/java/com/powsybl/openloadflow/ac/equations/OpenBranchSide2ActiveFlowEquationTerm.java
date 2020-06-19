/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenBranchSide2ActiveFlowEquationTerm extends AbstractOpenBranchAcFlowEquationTerm {

    private final Variable v1Var;

    private final List<Variable> variables;

    private double p1;

    private double dp1dv1;

    public OpenBranchSide2ActiveFlowEquationTerm(LfBranch branch, LfBus bus1, VariableSet variableSet) {
        super(branch);
        v1Var = variableSet.getVariable(bus1.getNum(), VariableType.BUS_V);
        variables = Collections.singletonList(v1Var);
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        Objects.requireNonNull(x);
        double v1 = x[v1Var.getColumn()];
        p1 = r1 * r1 * v1 * v1 * (g1 + y * y * g2 / shunt + (b2 * b2 + g2 * g2) * y * sinKsi / shunt);
        dp1dv1 = 2 * r1 * r1 * v1 * (g1 + y * y * g2 / shunt + (b2 * b2 + g2 * g2) * y * sinKsi / shunt);
    }

    @Override
    public double eval() {
        return p1;
    }

    @Override
    public double der(Variable variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1;
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_open_2";
    }
}
