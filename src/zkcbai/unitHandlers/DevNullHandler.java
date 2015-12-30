/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.ArrayList;
import java.util.List;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 * 
 */
public class DevNullHandler extends UnitHandler{

    //this class does nothing
    
    public DevNullHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }
    
    
    @Override
    public AIUnit addUnit(Unit u) {
        return new AIUnit(u,this);
    }

    @Override
    public void troopIdle(AIUnit u) {
        
    }

    @Override
    public void abortedTask(Task t) {
        
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public void removeUnit(AIUnit u) {
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void troopIdle(AISquad s) {
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }
    
}
