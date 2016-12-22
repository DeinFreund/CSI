/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Collection;
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
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class NanoHandler extends UnitHandler implements UpdateListener {

    Set<BuildTask> nanoTasks = new HashSet<>();
    Set<BuildTask> repairPadTasks = new HashSet<>();

    Set<AIUnit> nanos, repairPads;
    final UnitDef nano, repairPad;

    public NanoHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        nanos = new HashSet();
        repairPads = new HashSet();
        nano = command.getCallback().getUnitDefByName("armnanotc");
        repairPad = command.getCallback().getUnitDefByName("armasp");

        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 30);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        aiunits.put(u.getUnitId(), new AIUnit(u, this));
        if (u.getDef().equals(nano)) {
            nanos.add(aiunits.get(u.getUnitId()));
        }
        if (u.getDef().equals(repairPad)) {
            repairPads.add(aiunits.get(u.getUnitId()));
        }

        troopIdle(aiunits.get(u.getUnitId()));
        return aiunits.get(u.getUnitId());
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.hashCode());
        repairPads.remove(u);
        nanos.remove(u);
    }
    
    public Collection<AIUnit> getNanos(){
        return nanos;
    }

    @Override
    public void troopIdle(AIUnit u) {
        if (u.getDef().equals(nano)) {
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
        }else if (u.getDef().equals(repairPad)){
            u.assignTask(new WaitTask(command.getCurrentFrame() + 1000, this));
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

        for (BuildTask bt : nanoTasks.toArray(new BuildTask[nanoTasks.size()])) {
            if (bt.isAborted() || bt.isDone()) {
                nanoTasks.remove(bt);
            }
        }
        if ((nanos.size() + nanoTasks.size()) * 9 + 16 < Math.min(command.getBuilderHandler().avgMetalIncome, command.getBuilderHandler().energyIncome)) {
            nanoTasks.add(command.getBuilderHandler().requestBuilding(nano,
                    command.getFactoryHandler().getFacs().toArray(new Factory[command.getFactoryHandler().getFacs().size()])[(int) (Math.random() * command.getFactoryHandler().getFacs().size())].unit.getPos()));
            command.debug(nanoTasks.size() + " Caretakers under construction");
        }
        if ((repairPadTasks.size() + repairPads.size()) * 10 + 10 < command.getAvengerHandler().getUnits().size() || (command.getAvengerHandler().getRepairingAvengers().size() > 3 || command.getDropHandler().getLichos().size() > 1 ) && repairPadTasks.isEmpty() && repairPads.isEmpty()) {
            repairPadTasks.add(command.getBuilderHandler().requestBuilding(repairPad,
                    command.getFactoryHandler().getFacs().toArray(new Factory[command.getFactoryHandler().getFacs().size()])[(int) (Math.random() * command.getFactoryHandler().getFacs().size())].unit.getPos()));
            command.debug(repairPadTasks.size() + " Repair Pads under construction");
        }

        command.addSingleUpdateListener(this, frame + 15);
    }

}
