/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.Unit;
import java.util.ArrayList;
import java.util.List;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class FighterHandler extends UnitHandler{

    
    List<AIUnit> fighters;
    
    public FighterHandler(){
        fighters = new ArrayList();
    }
    
    @Override
    public void addUnit(Unit u) {
        fighters.add(new AIUnit(u, this));
        aiunits.put(u.getUnitId(), fighters.get(fighters.size()-1));
        fighters.get(fighters.size()-1).idle();
    }

    @Override
    public void unitIdle(AIUnit u) {
        
    }

    @Override
    public void abortedTask(Task t) {
        
    }

    
}
