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
public class WaitTask extends Task {

    private int timeout;

    public WaitTask(int timeout, TaskIssuer issuer) {
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
        u.wait(timeout);
        return false;
    }

    @Override
    public void moveFailed(AITroop u) {
    }

    @Override
    public WaitTask clone(){
        WaitTask as = new WaitTask(timeout, issuer);
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
