/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public abstract class Task {

    public abstract boolean execute(AIUnit u);

    public abstract void pathFindingError(AIUnit u);

    private String info = "";

    public Task setInfo(String info) {
        this.info = info;
        return this;
    }

    public String getInfo() {
        return this.info;
    }

    public abstract Object getResult();
}
