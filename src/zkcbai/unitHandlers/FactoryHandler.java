/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.List;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class FactoryHandler extends UnitHandler {

    List<AIUnit> facs = new ArrayList();

    public FactoryHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }
    
    
    @Override
    public AIUnit addUnit(Unit u) {
        facs.add(new AIUnit(u, this));
        aiunits.put(u.getUnitId(), facs.get(facs.size() - 1));
        facs.get(facs.size() - 1).idle();
        return facs.get(facs.size() - 1);
    }

    @Override
    public void unitIdle(AIUnit u) {
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }
    
    public UnitDef getNextFac(){
        return clbk.getUnitDefByName("factorycloak");
    }

}
