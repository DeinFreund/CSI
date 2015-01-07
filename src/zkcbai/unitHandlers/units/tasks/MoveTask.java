/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import java.util.Deque;
import zkcbai.Command;
import zkcbai.helpers.CostSupplier;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class MoveTask extends Task {

    private AIFloat3 target;
    private int errors = 0;
    private TaskIssuer issuer;
    private AIUnit lastUnit;
    private int timeout;
    private Deque<AIFloat3> path;
    private CostSupplier costSupplier;
    private int lastPath = -1000;
    private int repathTime = 90;
    private Command command;

    public MoveTask(AIFloat3 target, TaskIssuer issuer, Command cmd) {
        this(target, Integer.MAX_VALUE, issuer, cmd);
    }

    public MoveTask(AIFloat3 target, int timeout, TaskIssuer issuer, Command cmd) {
        this(target, timeout, issuer, null, cmd);
    }

    public MoveTask(AIFloat3 target, int timeout, TaskIssuer issuer, CostSupplier costSupplier, Command cmd) {
        this.target = target;
        this.issuer = issuer;
        this.timeout = timeout;
        this.costSupplier = costSupplier;
        this.command = cmd;
        cmd.areaManager.executedTask();
        if (cmd.areaManager.getExecutedTasks() > 100) {
            issuer.reportSpam();
        }
    }

    public AIUnit getLastExecutingUnit() {
        return lastUnit;
    }

    private void updatePath(AIUnit u) {
        if (costSupplier == null) {
            costSupplier = u.getCommand().pathfinder.FAST_PATH;
        }
        path = u.getCommand().pathfinder.findPath(u.getPos(), target, u.getUnit().getDef().getMoveData().getMaxSlope(), costSupplier);
        if (path.size() <= 1) {
            
            command.debug("path finder probably didnt find a path");
            errors = 20;
        }
    }

    @Override
    public boolean execute(AIUnit u) {
        
        command.areaManager.executedTask();
        if (command.areaManager.getExecutedTasks() > 100) {
            issuer.reportSpam();
        }
        //u.getCommand().debug("executing movetask");
        if (u.getCommand().getCurrentFrame() - lastPath > repathTime) {
            lastPath = u.getCommand().getCurrentFrame();
            updatePath(u);
        }
        if (errors > 15) {
            issuer.abortedTask(this);
            return true;
        }
        lastUnit = u;
        while (!path.isEmpty() && ((u.distanceTo(path.getFirst()) < 220 && path.size() > 1) || (u.distanceTo(path.getFirst()) < 50))) {
            path.pollFirst();
        }
        if (path.isEmpty() || (u.getCommand().getCurrentFrame() >= timeout)) {
            issuer.finishedTask(this);
            return true;
        }
        u.moveTo(path.getFirst(), (short) 0, u.getCommand().getCurrentFrame() + 20);
        return false;
    }

    @Override
    public void pathFindingError(AIUnit u) {
        command.debug("pathFindingError");
        errors++;
        target.x += Math.random() * 60 - 30;
        target.z += Math.random() * 60 - 30;
    }

    @Override
    public Object getResult() {
        return null;
    }

}
