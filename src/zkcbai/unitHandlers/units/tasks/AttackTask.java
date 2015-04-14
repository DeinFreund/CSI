/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.helpers.CostSupplier;
import zkcbai.helpers.CounterAvoidance;
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
    private TaskIssuer issuer;
    private int timeout;
    private CostSupplier costSupplier;
    private Command command;

    public AttackTask(Enemy target, TaskIssuer issuer, Command cmd) {
        this(target, Integer.MAX_VALUE, issuer, cmd);
    }

    public AttackTask(Enemy target, int timeout, TaskIssuer issuer, Command cmd) {
        this(target, timeout, issuer, null, cmd);
    }

    public AttackTask(Enemy target, int timeout, TaskIssuer issuer, CostSupplier costSupplier, Command cmd) {
        this.target = target;
        this.issuer = issuer;
        this.timeout = timeout;
        this.costSupplier = costSupplier;
        this.command = cmd;
        command.addUnitDestroyedListener(this);
    }

    @Override
    public boolean execute(AITroop u) {
        if (target == null || (u.getCommand().getCurrentFrame() >= timeout)) {
            issuer.finishedTask(this);
            command.removeUnitDestroyedListener(this);
            return true;
        }
        if (errors > 15) {
            command.removeUnitDestroyedListener(this);
            issuer.abortedTask(this);
            return true;
        }
        if (u.distanceTo(target.getPos()) > u.getMaxRange() * 1.5) {
            if (u.distanceTo(target.getPos()) > u.getMaxRange() * 2.5) {
                u.assignTask(new MoveTask(target.getPos(), command.getCurrentFrame()+80, this, new CounterAvoidance(u.getDef(),command), command));
                u.queueTask(this);
            }else{
                u.moveTo(target.getPos(), command.getCurrentFrame()+50);
            }
            return false;
        }/*
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

        u.setTarget(target.getUnitId());
        
        //if (target.isBuilding()) {
        //    u.attack(target.getUnit(), command.getCurrentFrame() + 25);
        //} else {
            u.fight(target.getPos(), command.getCurrentFrame() + 25);
        //}
        //u.moveTo(npos, Integer.MAX_VALUE);
        return false;
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

}
