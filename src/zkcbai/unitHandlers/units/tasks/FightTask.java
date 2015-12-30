/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class FightTask extends Task {

    private AIFloat3 target;
    private int errors = 0;
    private AITroop lastUnit;
    private int timeout;

    public FightTask(AIFloat3 target, TaskIssuer issuer) {
        this(target, -1, issuer);
    }

    public FightTask(AIFloat3 target, int timeout, TaskIssuer issuer) {
        super(issuer);
        this.target = target;
        this.timeout = timeout;
    }

    public AITroop getLastExecutingUnit() {
        return lastUnit;
    }

    @Override
    public boolean execute(AITroop u) {
        if (errors > 10) {
            completed(u);
            issuer.abortedTask(this);
            return true;
        }
        lastUnit = u;
        if ((u.distanceTo(target) < 40 && timeout < 0) || (u.getCommand().getCurrentFrame() >= timeout)) {
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        target.x += Math.random() * 30 - 15;
        target.z += Math.random() * 30 - 15;
        if (u.distanceTo(target) < 100) {
            target.x += Math.random() * 80 - 40;
            target.z += Math.random() * 80 - 40;
        }
        u.patrolTo(target, (short) 0, Math.min(timeout, u.getCommand().getCurrentFrame() + 30));
        return false;
    }

    @Override
    public void moveFailed(AITroop u) {
        errors++;
        target.x += Math.random() * 200 - 100;
        target.z += Math.random() * 200 - 100;
    }

    @Override
    public FightTask clone(){
        FightTask as = new FightTask(target, timeout, issuer);
        as.queued = this.queued;
        return as;
    }
    
    @Override
    public Object getResult() {
        return null;
    }
    
    @Override
    public void cancel(){
        
    }

}
