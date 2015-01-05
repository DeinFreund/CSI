/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class CommanderHandler extends UnitHandler {

    AIUnit com;
    boolean plopped = false;
    AIFloat3 startPos;

    public CommanderHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        if (com == null) {
            com = new AIUnit(u, this);
            aiunits.put(u.getUnitId(), com);
            com.idle();
            startPos = com.getPos();
            return com;
        } else {
            throw new UnsupportedOperationException("Assigned more than one com to CommanderHandler");
        }
    }

    @Override
    public void unitIdle(AIUnit u) {
        command.debug("Commander is idle");
        if (!plopped) {
            com.assignTask(new BuildTask(command.getFactoryHandler().getNextFac(), com.getPos(), this, clbk, command).setInfo("plop"));
        } else {
            if (command.areaManager.getNearestBuildableMex(startPos).distanceTo(startPos) < 700){
                com.assignTask(command.areaManager.getNearestBuildableMex(com.getPos()).createBuildTask(this));
            }else{
                com.assignTask(new BuildTask(clbk.getUnitDefByName("armwin"), com.getPos(), this, clbk, command));
            }
        }
    }

    @Override
    public void abortedTask(Task t) {
        command.debug("Commander aborted a task");
    }

    @Override
    public void finishedTask(Task t) {
        command.debug("Commander finished a task");
        switch (t.getInfo()) {
            case "plop":
                command.debug("Commander plopped fac");
                plopped = true;
                com.assignTask(command.areaManager.getNearestBuildableMex(startPos).createBuildTask(this).setInfo("startmex"));
                break;
            case "startmex":
                com.assignTask(new BuildTask(clbk.getUnitDefByName("armwin"), com.getPos(), this, clbk, command).setInfo("win1"));
                break;
            case "win1":
                com.assignTask(new BuildTask(clbk.getUnitDefByName("armwin"), com.getPos(), this, clbk, command).setInfo("win2"));
                break;
            case "win2":
                com.assignTask(new BuildTask(clbk.getUnitDefByName("corrad"), 
                        command.areaManager.getArea(startPos).getHighestArea(clbk.getUnitDefByName("corrad"), 700).getPos(), 
                        this, clbk, command).setInfo("radar"));
                break;
        }

    }

    @Override
    public void removeUnit(AIUnit u) {
    }

    @Override
    public void unitDestroyed(AIUnit u) {
    }

    @Override
    public void unitDestroyed(Enemy e) {
    }

}
