/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.helpers.CostSupplier;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class AttackTask extends Task implements TaskIssuer,UnitDestroyedListener{

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
    }

    @Override
    public boolean execute(AIUnit u) {
        if (target == null || (u.getCommand().getCurrentFrame() >= timeout)) {
            issuer.finishedTask(this);
            return true;
        }
        if (errors > 15) {
            issuer.abortedTask(this);
            return true;
        }
        if (u.distanceTo(target.getPos()) > 300){
            u.assignTask(new MoveTask(target.getPos(),command.getCurrentFrame()+20,this,costSupplier,command));
            u.queueTask(this);
            return false;
        }
        AIFloat3 npos = u.getPos();
        npos.z+= Math.random()*40-20;
        npos.x+= Math.random()*40-20;
        u.moveTo(npos, command.getCurrentFrame()+40);
        u.attack(target.getUnit(),AIUnit.OPTION_SHIFT_KEY, command.getCurrentFrame()+40);
        return false;
    }

    @Override
    public void pathFindingError(AIUnit u) {
        command.debug("pathFindingError @AttackTask");
        errors++;
    }

    @Override
    public Object getResult() {
        return null;
    }

    @Override
    public void unitDestroyed(AIUnit u) {
    }

    @Override
    public void unitDestroyed(Enemy e) {
        if (e.equals(target)){
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
