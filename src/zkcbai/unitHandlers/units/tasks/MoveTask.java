/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Collections;
import java.util.Deque;
import java.util.Random;
import zkcbai.Command;
import zkcbai.helpers.CostSupplier;
import zkcbai.helpers.CounterAvoidance;
import zkcbai.helpers.PathfindingCompleteListener;
import zkcbai.unitHandlers.units.AITroop;

/**
 *
 * @author User
 */
public class MoveTask extends Task implements PathfindingCompleteListener {

    private AIFloat3 target;
    private int errors = 0;
    private AITroop lastUnit;
    private int timeout;
    private Deque<AIFloat3> path;
    private CostSupplier costSupplier;
    private int lastPath = -1000;
    private int repathTime = 90;
    private Command command;
    private static Random rnd = new Random();
    private boolean requestingPath = false;

    public MoveTask(AIFloat3 target, TaskIssuer issuer, Command cmd) {
        this(target, Integer.MAX_VALUE, issuer, cmd);
    }

    public MoveTask(AIFloat3 target, int timeout, TaskIssuer issuer, Command cmd) {
        this(target, timeout, issuer, cmd.pathfinder.FAST_PATH, cmd);
    }

    /**
     * Generates CounterAvoidance using UnitDef
     *
     * @param target
     * @param timeout
     * @param issuer
     * @param ud
     * @param cmd
     */
    public MoveTask(AIFloat3 target, int timeout, TaskIssuer issuer, UnitDef ud, Command cmd) {
        this(target, timeout, issuer, new CounterAvoidance(ud, cmd, (ud.isAbleToCloak()) ? (150 * ud.getSpeed() / 80) : 0), cmd);
    }

    public MoveTask(AIFloat3 target, int timeout, TaskIssuer issuer, CostSupplier costSupplier, Command cmd) {
        super(issuer);

        this.target = target;
        this.timeout = timeout;
        this.costSupplier = costSupplier;
        this.command = cmd;
        cmd.areaManager.executedTask();
        if (cmd.areaManager.getExecutedTasks() > 100) {
            issuer.reportSpam();
        }
    }

    public AITroop getLastExecutingUnit() {
        return lastUnit;
    }

    private void updatePath(AITroop u) {
        if (costSupplier == null) {
            costSupplier = u.getCommand().pathfinder.FAST_PATH;
        }
        requestingPath = true;
        u.getCommand().pathfinder.requestPath(u.getPos(), target, u.getMaxSlope(), costSupplier, this);

    }

    float randomizeFirst = 60;

    @Override
    public boolean execute(AITroop u) {

        lastUnit = u;
        command.areaManager.executedTask();
        if (command.areaManager.getExecutedTasks() > 100) {
            issuer.reportSpam();
        }
        //u.getCommand().debug("executing movetask");
        if (u.getCommand().getCurrentFrame() - lastPath > repathTime && !requestingPath) {
            updatePath(u);
        }
        if (path == null && requestingPath) {
            u.moveTo(target, command.getCurrentFrame() + 21);
            return false;
        }
        if (errors > 15) {
            command.debug("MoveTask aborted because of too many errors");
            completed(u);
            issuer.abortedTask(this);
            return true;
        }
        while (!path.isEmpty() && ((u.distanceTo(path.getFirst()) < 220 && path.size() > 1) || (u.distanceTo(path.getFirst()) < 50))) {
            path.pollFirst();
            //command.debug("removing first checkpoint");
        }
        if (path.isEmpty() && u.distanceTo(target) > 50) {
            u.moveTo(target, u.getCommand().getCurrentFrame() + 20);
            return false;
        }
        if (path.isEmpty() || (u.getCommand().getCurrentFrame() >= timeout)) {
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        AIFloat3 first = (path.pollFirst());

        u.moveTo(randomize(first, randomizeFirst), (short) 0, u.getCommand().getCurrentFrame() + 20);
        if (path.size() > 0 && distance(first, path.getFirst()) > 100) {
            u.moveTo(randomize(path.getFirst(), 100), AITroop.OPTION_SHIFT_KEY, u.getCommand().getCurrentFrame() + 20);
        }
        path.addFirst(first);
        return false;
    }

    private AIFloat3 randomize(AIFloat3 f, float amt) {
        return new AIFloat3(f.x + rnd.nextFloat() * amt - amt / 2, f.y + rnd.nextFloat() * amt - amt / 2, f.z + rnd.nextFloat() * amt - amt / 2);
    }

    private float distance(AIFloat3 a, AIFloat3 b) {
        AIFloat3 res = new AIFloat3(a);
        res.sub(b);
        return res.length();
    }

    @Override
    public void moveFailed(AITroop u) {
        command.debug("move Failed");
        errors++;
        target.x += Math.random() * 60 - 30;
        target.z += Math.random() * 60 - 30;
        randomizeFirst *= 1.5;
    }

    @Override
    public MoveTask clone() {
        MoveTask as = new MoveTask(target, timeout, issuer, costSupplier, command);
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
    public void foundPath(Deque<AIFloat3> path) {
        requestingPath = false;
        lastPath = command.getCurrentFrame();
        this.path = path;
        //command.debug("path has " + path.size() + " steps");
        
        if (path.size() <= 1) {
            lastPath = command.getCurrentFrame() - repathTime + 30;
        }
/*
            target.x = Math.max(0, Math.min(command.getCallback().getMap().getWidth() * 8 - 1, target.x + (float) Math.random() * 400 - 200));
            target.z = Math.max(0, Math.min(command.getCallback().getMap().getHeight() * 8 - 1, target.z + (float) Math.random() * 400 - 200));

            updatePath(lastUnit);

        }*/
    }

}
