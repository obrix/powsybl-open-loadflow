/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.MatrixFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class JacobianMatrixCache implements EquationSystemListener {

    private final EquationSystem equationSystem;

    private final MatrixFactory matrixFactory;

    private JacobianMatrix j;

    public JacobianMatrixCache(EquationSystem equationSystem, MatrixFactory matrixFactory) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        equationSystem.addListener(this);
    }

    public JacobianMatrix get() {
        if (j == null) {
            j = JacobianMatrix.create(equationSystem, matrixFactory);
        } else {
            j.update();
        }
        return j;
    }

    public void release() {
        if (j != null) {
            equationSystem.removeListener(this);
            j.cleanLU();
        }
    }

    @Override
    public void equationListChanged(Equation equation, EquationEventType eventType) {
        switch (eventType) {
            case EQUATION_CREATED:
            case EQUATION_REMOVED:
            case EQUATION_ACTIVATED:
            case EQUATION_DEACTIVATED:
                if (j != null) {
                    j.cleanLU();
                    j = null;
                }
                break;
            case EQUATION_UPDATED:
                // nothing to do
                break;
        }
    }
}
