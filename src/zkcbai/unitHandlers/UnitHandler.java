/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.Map;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;

/**
 *
 * @author User
 */
public abstract class UnitHandler implements TaskIssuer{
    
    Map<Integer, AIUnit> aiunits = new TreeMap();
    Command command;
    OOAICallback clbk;
    
    public UnitHandler(Command cmd, OOAICallback clbk){
        command = cmd;
        this.clbk = clbk;
    }
    
    public abstract AIUnit addUnit(Unit u);
    
    public void unitIdle(Unit u){
        unitIdle(aiunits.get(u.getUnitId()));
    }
    
    public abstract void unitIdle(AIUnit u);
    
}
