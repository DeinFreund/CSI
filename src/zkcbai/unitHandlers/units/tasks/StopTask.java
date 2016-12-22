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
public class StopTask extends Task {

    private int timeout;

    public StopTask(int timeout, TaskIssuer issuer) {
        super(issuer);
        this.timeout = timeout;
    }


    @Override
    public boolean execute(AITroop u) {
        if ((u.getCommand().getCurrentFrame() >= timeout)) {
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        for (AIUnit au : u.getUnits()){
            au.getUnit().stop((short)0, Integer.MAX_VALUE);
        }
        u.wait(timeout);
        return false;
    }

    @Override
    public void moveFailed(AITroop u) {
    }

    @Override
    public StopTask clone(){
        StopTask as = new StopTask(timeout, issuer);
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
