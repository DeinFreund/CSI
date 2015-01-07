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
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackTask;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class CommanderHandler extends UnitHandler implements UpdateListener {

    AIUnit com;
    boolean plopped = false;
    AIFloat3 startPos;
    Task lastBuildTask = null;

    public CommanderHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 10);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        if (com == null) {
            com = new AIUnit(u, this);
            aiunits.put(u.getUnitId(), com);
            startPos = com.getPos();
            com.idle();
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
            if (lastBuildTask.getResult() != null) {
                finishedTask(lastBuildTask);
            } else {
                com.assignTask(lastBuildTask);
            }
        }
        lastBuildTask = com.getTask();
    }

    @Override
    public void abortedTask(Task t) {
        command.debug("Commander aborted a task");
        finishedTask(t);
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
                        command.areaManager.getArea(com.getPos()).getHighestArea(clbk.getUnitDefByName("corrad"), 350).getPos(),
                        this, clbk, command).setInfo("radar"));
                break;
            case "win4":
            case "radar":
                com.assignTask(command.areaManager.getNearestBuildableMex(startPos).createBuildTask(this).setInfo("mex"));
                break;
            case "mex":
                com.assignTask(new BuildTask(clbk.getUnitDefByName("armwin"), com.getPos(), this, clbk, command).setInfo("win3"));
                break;
            case "win3":
                com.assignTask(new BuildTask(clbk.getUnitDefByName("armwin"), com.getPos(), this, clbk, command).setInfo("win4"));
                break;
        }
        lastBuildTask = com.getTask();
        command.debug("New task has info " + lastBuildTask.getInfo());

    }

    @Override
    public void removeUnit(AIUnit u) {
        if (u.equals(com)) {
            com = null;
        }
    }

    @Override
    public void unitDestroyed(Enemy e) {
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void update(int frame) {

        if (com == null) {
            return;
        }
        Enemy best = null;
        float mindist = Float.MAX_VALUE;
        for (Enemy e : command.getEnemyUnitsIn(startPos, 1300)) {
            if (e.distanceTo(startPos) < mindist) {
                mindist = e.distanceTo(startPos);
                best = e;
            }
        }
        if (best != null) {
            com.assignTask(new AttackTask(best, this, command));
            com.queueTask(lastBuildTask);
        }
        command.addSingleUpdateListener(this, frame + 40);
    }

}
