/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class NanoHandler extends UnitHandler{

    public NanoHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        aiunits.put(u.getUnitId(), new AIUnit(u, this));
        troopIdle(aiunits.get(u.getUnitId()));
        return aiunits.get(u.getUnitId());
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.hashCode());
    }

    @Override
    public void troopIdle(AIUnit u) {
        AIUnit closest = null;
        for (AIUnit fac : command.getFactoryHandler().getUnits()){
            if (closest == null || closest.distanceTo(u.getPos()) > fac.distanceTo(u.getPos())){
                closest = fac;
            }
        }
        if (closest != null){
            u.getUnit().guard(closest.getUnit(), (short)0, Integer.MAX_VALUE);
        }
        /*
        AIFloat3 offset = new AIFloat3(u.getPos());
        offset.add(new AIFloat3(10,0,10));
        u.patrolTo(offset, -1);*/
    }

    @Override
    public void troopIdle(AISquad s) {
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public void reportSpam() {
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }
    
}
