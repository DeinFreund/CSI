/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.FactoryHandler.Factory;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class NanoHandler extends UnitHandler implements UpdateListener {

    Set<BuildTask> nanoTasks = new HashSet<>();

    public NanoHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 30);
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
        for (AIUnit fac : command.getFactoryHandler().getUnits()) {
            if (closest == null || closest.distanceTo(u.getPos()) > fac.distanceTo(u.getPos())) {
                closest = fac;
            }
        }
        if (closest != null) {
            //u.getUnit().guard(closest.getUnit(), (short)0, Integer.MAX_VALUE);
            u.getUnit().patrolTo(closest.getPos(), (short) 0, Integer.MAX_VALUE);
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

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    @Override
    public void update(int frame) {

        for (BuildTask bt : nanoTasks.toArray(new BuildTask[nanoTasks.size()])){
            if (bt.isAborted() || bt.isDone()){
                nanoTasks.remove(bt);
            }
        }
        if ((aiunits.size() + nanoTasks.size()) * 10 + 15 < Math.min(command.getBuilderHandler().avgMetalIncome, command.getBuilderHandler().energyIncome) ) {
            nanoTasks.add(command.getBuilderHandler().requestCaretaker(command.getFactoryHandler().getFacs().toArray(new Factory[command.getFactoryHandler().getFacs().size()])[(int) (Math.random() * command.getFactoryHandler().getFacs().size())].unit.getPos()));
            command.debug(nanoTasks.size() + " Caretakers under construction");
        }

        command.addSingleUpdateListener(this, frame + 15);
    }

}
