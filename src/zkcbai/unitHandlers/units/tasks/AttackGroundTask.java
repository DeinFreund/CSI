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
public class AttackGroundTask extends Task {

    private AIFloat3 target;
    private AITroop lastUnit;
    private int timeout;

    public AttackGroundTask(AIFloat3 target, TaskIssuer issuer) {
        this(target, -1, issuer);
    }

    public AttackGroundTask(AIFloat3 target, int timeout, TaskIssuer issuer) {
        super(issuer);
        this.target = target;
        this.timeout = timeout;
    }

    public AITroop getLastExecutingUnit() {
        return lastUnit;
    }

    @Override
    public boolean execute(AITroop u) {
        lastUnit = u;
        if (u.getCommand().getCurrentFrame() >= timeout) {
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        u.attackGround(target, (short) 0, Math.min(timeout, u.getCommand().getCurrentFrame() + 30));
        return false;
    }

    @Override
    public void moveFailed(AITroop u) {
    }

    @Override
    public AttackGroundTask clone(){
        AttackGroundTask as = new AttackGroundTask(target, timeout, issuer);
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
