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
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class LoadUnitTask extends Task implements TaskIssuer, UnitDestroyedListener {

    private AIUnit target;
    private int errors = 0;
    private Set<AIUnit> workers = new HashSet<>();
    private Command command;
    private boolean finished = false;
    private String tdef;

    public LoadUnitTask(AIUnit target, TaskIssuer issuer, Command command) {
        super(issuer);
        this.target = target;
        this.command = command;
        command.addUnitDestroyedListener(this);
        tdef = target.getDef().getHumanName();
    }

    @Override
    public boolean execute(AITroop u) {
        if (finished) {
            return true;
        }
        workers.add((AIUnit) u);
        if (errors > 10 || u.equals(target)) {
            command.debug("Aborted LoadUnitTask trying to load " + tdef);
            issuer.abortedTask(this);
            completed(u);
            return true;
        }
        if (target.distanceTo3D(u.getPos()) < 10f) {
            issuer.finishedTask(this);
            completed(u);
            return true;
        }
        if (u.distanceTo(target.getPos()) > 200) {
            u.assignTask(new MoveTask(target.getPos(), u.getCommand().getCurrentFrame() + 60, this, u.getCommand().pathfinder.AVOID_ENEMIES, u.getCommand()).queue(this));
            return false;
        }
        u.loadUnit(target.getUnit(), command.getCurrentFrame() + 100);
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
    public LoadUnitTask clone() {
        LoadUnitTask as = new LoadUnitTask(target, issuer, command);
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
        if (target.equals(u)) {
            errors += 100;
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {

    }
    
    
    @Override
    public void unitDestroyed(Unit u, Enemy e) {
        
    }

}
