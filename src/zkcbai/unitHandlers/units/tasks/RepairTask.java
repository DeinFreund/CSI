/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class RepairTask extends Task implements TaskIssuer, UnitDestroyedListener {

    private AIUnit target;
    private int errors = 0;
    private Set<AIUnit> workers = new HashSet<>();
    private Command command;

    public RepairTask(AIUnit target, TaskIssuer issuer, Command command) {
        super(issuer);
        this.target = target;
        this.command = command;
        command.addUnitDestroyedListener(this);
    }

    @Override
    public boolean execute(AITroop u) {
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
        if (u.distanceTo(target.getPos()) > u.getDef().getBuildDistance()) {
            u.assignTask(new MoveTask(target.getPos(), u.getCommand().getCurrentFrame() + 60, this, u.getCommand().pathfinder.AVOID_ENEMIES, u.getCommand()).queue(this));
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
        if (u.equals(this.target)){
            completed(null);
            issuer.abortedTask(this);
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

}
