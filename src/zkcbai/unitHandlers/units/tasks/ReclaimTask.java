/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Feature;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class ReclaimTask extends Task implements TaskIssuer{

    private AIFloat3 target;
    private float range;
    private int errors = 0;
    private Set<AIUnit> workers = new HashSet<>();
    private Command command;
    private boolean finished = false;

    public ReclaimTask(AIFloat3 target, float range, TaskIssuer issuer, Command command) {
        super(issuer);
        this.target = target;
        this.range = range;
        this.command = command;
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
        if (command.areaManager.getArea(target).getReclaim() < 0.1f || (u.distanceTo(target) < u.getDef().getBuildDistance() * 1.5 && command.getCallback().getFeaturesIn(target, range).isEmpty()) ) {
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        if (u.distanceTo(target) > u.getDef().getBuildDistance()) {
            u.assignTask(new MoveTask(target, u.getCommand().getCurrentFrame() + 60, this, u.getCommand().pathfinder.AVOID_ENEMIES, u.getCommand()).queue(this));
            return false;
        }
        u.reclaimArea(target, range, u.getCommand().getCurrentFrame() + 60);
        return false;
    }

    public AIFloat3 getTarget() {
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
    public ReclaimTask clone() {
        ReclaimTask as = new ReclaimTask(target, range, issuer, command);
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

}
