/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class CommanderHandler extends UnitHandler{

    AIUnit com;
    boolean plopped = false;
    
    public CommanderHandler(Command cmd, OOAICallback clbk){
        super(cmd, clbk);
    }
    
    @Override
    public AIUnit addUnit(Unit u) {
        if (com == null){
            com = new AIUnit(u,this);
            aiunits.put(u.getUnitId(), com);
            com.idle();
            return com; 
        }else{
            throw new UnsupportedOperationException("Assigned more than one com to CommanderHandler");
        }
    }

    @Override
    public void unitIdle(AIUnit u) {
        if (!plopped){
            com.assignTask(new BuildTask(command.getFactoryHandler().getNextFac(), com.getPos(), this, clbk, command).setInfo("plop"));
        }
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
        switch(t.getInfo()){
            case "plop":
                plopped = true;
                break;
        }
        
    }
    
}
