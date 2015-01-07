/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class FightTask extends Task {

    private AIFloat3 target;
    private int errors = 0;
    private TaskIssuer issuer;
    private AIUnit lastUnit;
    private int timeout;

    public FightTask(AIFloat3 target, TaskIssuer issuer) {
        this(target, -1, issuer);
    }

    public FightTask(AIFloat3 target, int timeout, TaskIssuer issuer) {
        this.target = target;
        this.issuer = issuer;
        this.timeout = timeout;
    }

    public AIUnit getLastExecutingUnit() {
        return lastUnit;
    }

    @Override
    public boolean execute(AIUnit u) {
        if (errors > 5) {
            issuer.abortedTask(this);
            u.idle();
            return true;
        }
        lastUnit = u;
        if ((u.distanceTo(target) < 40 && timeout < 0) || (u.getCommand().getCurrentFrame() >= timeout)) {
            issuer.finishedTask(this);
            return true;
        }
        target.x += Math.random() * 30 - 15;
        target.z += Math.random() * 30 - 15;
        if (u.distanceTo(target) < 100) {
            target.x += Math.random() * 80 - 40;
            target.z += Math.random() * 80 - 40;
        }
        u.fight(target, (short) 0, timeout);
        return false;
    }

    @Override
    public void pathFindingError(AIUnit u) {
        errors++;
        target.x += Math.random() * 60 - 30;
        target.z += Math.random() * 60 - 30;
    }

    @Override
    public Object getResult() {
        return null;
    }

}
