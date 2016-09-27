/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.clb.Unit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.UpdateListener;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class RepairTask extends Task implements TaskIssuer, UnitDestroyedListener, UpdateListener{

    private AIUnit target;
    private int errors = 0;
    private Set<AIUnit> workers = new HashSet<>();
    private Command command;
    private boolean finished = false;

    public RepairTask(AIUnit target, TaskIssuer issuer, Command command) {
        super(issuer);
        this.target = target;
        this.command = command;
        command.addUnitDestroyedListener(this);
        command.addSingleUpdateListener(this, command.getCurrentFrame() + 50);
    }

    @Override
    public boolean execute(AITroop u) {
        if (finished) return true;
        workers.add((AIUnit)u);
        if (errors > 10) {
            completed(u);
            issuer.abortedTask(this);
            return true;
        }
        if (target.repaired()) {
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        if (u.distanceTo(target.getPos()) > 400 || target.getArea().getZone() != Zone.own) {
            u.assignTask(new MoveTask(target.getArea().getNearestArea(command.areaManager.SAFE, target.getMovementType()).getPos(), 
                    u.getCommand().getCurrentFrame() + 60, this, u.getCommand()).queue(this));
            return false;
        }
        u.repair(target.getUnit(), (short) 0, u.getCommand().getCurrentFrame() + 60);
        return false;
    }

    public AIUnit getTarget() {
        return target;
    }

    public Collection<AIUnit> getWorkers() {
        return workers;
    }

    @Override
    public void moveFailed(AITroop u) {
        errors++;
    }

    
    public void removeWorker(AIUnit worker){
        workers.remove(worker);
    }
    
    @Override
    public RepairTask clone() {
        RepairTask as = new RepairTask(target, issuer, command);
        as.queued = this.queued;
        return as;
    }

    @Override
    public Object getResult() {
        return null;
    }

    @Override
    public void cancel() {

    }

    @Override
    public void completed(AITroop au) {
        workers.clear();
        command.removeUnitDestroyedListener(this);
        finished = true;
        super.completed(au);
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
        //k
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("cmon this shouldnt happen");
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy killer) {
        workers.remove(u);
        if (u.equals(this.target)){
            completed(null);
            issuer.abortedTask(this);
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public void update(int frame) {
        if (finished) return;
        command.addSingleUpdateListener(this, command.getCurrentFrame() + 50);
        if (target.repaired()) {
            completed(null);
            issuer.finishedTask(this);
        }
    }

    @Override
    public TaskType getTaskType(){
        return TaskType.RepairTask;
    }
    
    
    @Override
    public void unitDestroyed(Unit u, Enemy e) {
        
    }
    
}
