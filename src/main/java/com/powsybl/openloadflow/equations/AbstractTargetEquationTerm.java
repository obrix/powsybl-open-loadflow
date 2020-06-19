/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractTargetEquationTerm implements EquationTerm {

    private Equation equation;

    private final Variable var;

    private final List<Variable> variables;

    private double target;

    protected AbstractTargetEquationTerm(int num, VariableType variableType, VariableSet variableSet) {
        var = variableSet.getVariable(num, variableType);
        variables = Collections.singletonList(var);
    }

    @Override
    public Equation getEquation() {
        return equation;
    }

    @Override
    public void setEquation(Equation equation) {
        this.equation = Objects.requireNonNull(equation);
    }

    @Override
    public List<Variable> getVariables() {
        return variables;
    }

    @Override
    public void update(double[] x) {
        target = x[var.getColumn()];
    }

    @Override
    public double eval() {
        return target;
    }

    @Override
    public double der(Variable variable) {
        return 1;
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs() {
        return 0;
    }

    @Override
    public void write(Writer writer) throws IOException {
        var.write(writer);
    }
}
