/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class DropTask extends Task implements TaskIssuer {

    private Enemy target;
    private AIUnit payload;
    private int errors = 0;
    private Set<AIUnit> workers = new HashSet<>();
    private Command command;
    private boolean finished = false;
    private boolean loaded = false;
    private boolean dropped = false;
    private AIFloat3 home;

    public DropTask(Enemy target, AIUnit payload, TaskIssuer issuer, Command command) {
        super(issuer);
        if (target == null || payload == null) {
            throw new NullPointerException();
        }
        this.target = target;
        this.command = command;
        this.payload = payload;
        home = command.getFactoryHandler().getFacs().iterator().next().unit.getPos();
    }

    @Override
    public boolean execute(AITroop u) {
        if (finished) {
            return true;
        }
        workers.add((AIUnit) u);
        if (errors > 10) {
            command.debug("Aborted DropTask");
            issuer.abortedTask(this);
            completed(u);
            return true;
        }
        if (!loaded) {
            u.assignTask(new LoadUnitTask(payload, this, command).queue(this));
            return false;
        }
        if (dropped) {
            if (u.distanceTo(home) > 500) {
                u.moveTo(home, command.getCurrentFrame() + 30);
                return false;
            } else {
                issuer.finishedTask(this);
                completed(u);
                return true;
            }
        }
        if (target.distanceTo(u.getPos()) < 50f) {
            u.dropPayload(command.getCurrentFrame() + 10);
            command.mark(u.getPos(), "drop");
            dropped = true;
            return false;
        }
        
        u.moveTo(target.getPos(), command.getCurrentFrame() + 20);
        return false;
    }

    public Enemy getTarget() {
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
    public DropTask clone() {
        DropTask as = new DropTask(target, payload, issuer, command);
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
        errors++;
    }

    @Override
    public void finishedTask(Task t) {
        loaded = true;
        //k
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("cmon this shouldnt happen");
    }

}
