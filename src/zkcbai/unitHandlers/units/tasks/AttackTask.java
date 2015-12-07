/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class AttackTask extends Task implements TaskIssuer, UnitDestroyedListener {

    private Enemy target;
    private int errors = 0;
    private int timeout;
    private Command command;
    private boolean ignoreTimeOut;

    public AttackTask(Enemy target, TaskIssuer issuer, Command cmd) {
        this(target, Integer.MAX_VALUE, issuer, cmd);
    }

    public AttackTask(Enemy target, int timeout, TaskIssuer issuer, Command cmd) {
        this(target, timeout, issuer, false, cmd);
    }

    /**
     *
     * @param target
     * @param timeout
     * @param issuer
     * @param ignoreTimeOut ignore when then target.isTimedOut()
     * @param cmd
     */
    public AttackTask(Enemy target, int timeout, TaskIssuer issuer, boolean ignoreTimeOut, Command cmd) {
        super(issuer);
        this.target = target;
        this.timeout = timeout;
        this.ignoreTimeOut = ignoreTimeOut;
        this.command = cmd;
        command.addUnitDestroyedListener(this);
    }

    @Override
    public boolean execute(AITroop u) {
        if (target == null || (u.getCommand().getCurrentFrame() >= timeout) || (!ignoreTimeOut && target.isTimedOut())) {
            //command.mark(u.getPos(), "attack finished");
            if (target!= null && target.isTimedOut()){
                command.debug("finished attacktask because target is timed out");
            }
            completed(u);
            issuer.finishedTask(this);
            cleanup();
            return true;
        }
        if (errors > 15) {
            //command.mark(u.getPos(), "attack aborted");
            completed(u);
            cleanup();
            issuer.abortedTask(this);
            return true;
        }
        //if (u.distanceTo(target.getPos()) > u.getMaxRange() * 1.5) {
        if (u.distanceTo(target.getPos()) > Math.max(u.getMaxRange() * 1.7, 70) || (!target.isVisible() && u.distanceTo(target.getPos()) > 60)) {
            u.assignTask(new MoveTask(target.getPos(), command.getCurrentFrame() + 80, this,u.getDef(),command).queue(this));
            return false;
        }
        /*
         AIFloat3 tpos = target.getPos();
         AIFloat3 vel = new AIFloat3(target.getUnit().getVel());
         vel.scale(10);
         tpos.add(vel);
         AIFloat3 npos = u.getPos();
         npos.sub(tpos);
         double arc = (Math.atan2(npos.z, npos.x) + 1*(Math.random()));
         float range = u.getUnit().getMaxRange()*0.35f;
         npos = new AIFloat3(range*(float)Math.cos(arc),0,range*(float)Math.sin(arc));
         npos.add(tpos);
         */

        //u.setTarget(target.getUnitId());
        //command.mark(u.getPos(), "attack");
        if (target.isBuilding() && u.getDef().isAbleToCloak() && u.distanceTo(target.getPos()) < u.getMaxRange() /2){
            AIFloat3 pos  = new AIFloat3(target.getPos());
            pos.sub(u.getPos());
            pos.normalize();
            pos.scale(-70);
            pos.add(u.getPos());
            u.moveTo(pos, command.getCurrentFrame() + 25);
        }
        if (u.getMaxRange() < 400 && u.getDef().getSpeed()<  110) {
            u.attack(target.getUnit(), command.getCurrentFrame() + 30);
        } else {
            u.fight(target.getPos(), command.getCurrentFrame() + 70);
        }
        //u.moveTo(npos, Integer.MAX_VALUE);
        return false;
    }
    
    @Override
    public AttackTask clone(){
        AttackTask as = new AttackTask(target, timeout, issuer, ignoreTimeOut, command);
        as.queued = this.queued;
        return as;
    }

    @Override
    public void moveFailed(AITroop u) {
        command.debug("pathFindingError @AttackTask");
        errors++;
    }

    @Override
    public Object getResult() {
        return null;
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        if (e.equals(target)) {
            target = null;

        }
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
        
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    protected void cleanup() {

        command.removeUnitDestroyedListener(this);
    }
    
    @Override
    public void cancel(){
        cleanup();
    }
    
}
