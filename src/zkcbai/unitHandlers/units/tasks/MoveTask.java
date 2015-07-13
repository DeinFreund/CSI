/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Deque;
import java.util.Random;
import zkcbai.Command;
import zkcbai.helpers.CostSupplier;
import zkcbai.helpers.CounterAvoidance;
import zkcbai.unitHandlers.units.AITroop;

/**
 *
 * @author User
 */
public class MoveTask extends Task {

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

    public MoveTask(AIFloat3 target, TaskIssuer issuer, Command cmd) {
        this(target, Integer.MAX_VALUE, issuer, cmd);
    }

    public MoveTask(AIFloat3 target, int timeout, TaskIssuer issuer, Command cmd) {
        this(target, timeout, issuer, cmd.pathfinder.FAST_PATH, cmd);
    }

    /**
     * Generates CounterAvoidance using UnitDef
     * @param target
     * @param timeout
     * @param issuer
     * @param ud
     * @param cmd
     */
    public MoveTask(AIFloat3 target, int timeout, TaskIssuer issuer, UnitDef ud ,Command cmd) {
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
        path = u.getCommand().pathfinder.findPath(u.getPos(), target, u.getMaxSlope(), costSupplier);
        if (path.size() <= 1) {
            
            target.x = Math.max(0,Math.min( command.getCallback().getMap().getWidth()*8-1, target.x + (float)Math.random() * 150 - 75));
            target.z = Math.max(0,Math.min( command.getCallback().getMap().getHeight()*8-1, target.z +(float)Math.random() * 150 - 75));
            
            path = u.getCommand().pathfinder.findPath(u.getPos(), target, u.getMaxSlope(), costSupplier);
            
            if (path.size() <= 1) {
                command.debug("pathfinder didnt find path for movetask");
                for (AIFloat3 pos : path){
                    //command.mark(pos, "path");
                }
                //errors = 20;
            }
        }
    }

    float randomizeFirst = 60;
    
    @Override
    public boolean execute(AITroop u) {
        
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
            command.debug("MoveTask aborted because of too many errors");
            completed(u);
            issuer.abortedTask(this);
            return true;
        }
        lastUnit = u;
        while (!path.isEmpty() && ((u.distanceTo(path.getFirst()) < 220 && path.size() > 1) || (u.distanceTo(path.getFirst()) < 50))) {
            path.pollFirst();
        }
        if (path.isEmpty() && u.distanceTo(target) > 50){
            u.moveTo(target, u.getCommand().getCurrentFrame() + 20);
            return false;
        }
        if (path.isEmpty() || (u.getCommand().getCurrentFrame() >= timeout)) {
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        AIFloat3 first = (path.pollFirst());
        
        u.moveTo(randomize(first,randomizeFirst), (short) 0, u.getCommand().getCurrentFrame() + 20);
        if (path.size() > 0 && distance(first, path.getFirst()) > 100){
            u.moveTo(randomize(path.getFirst(),100), AITroop.OPTION_SHIFT_KEY, u.getCommand().getCurrentFrame() + 20);
        }
        path.addFirst(first);
        return false;
    }
    
    private AIFloat3 randomize(AIFloat3 f, float amt){
        return new AIFloat3(f.x+rnd.nextFloat()*amt-amt/2,f.y+rnd.nextFloat()*amt-amt/2,f.z+rnd.nextFloat()*amt-amt/2);
    }
    
    private float distance(AIFloat3 a, AIFloat3 b){
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
    public MoveTask clone(){
        MoveTask as = new MoveTask(target, timeout, issuer, costSupplier, command);
        as.queued = this.queued;
        return as;
    }

    @Override
    public Object getResult() {
        return null;
    }
    
    
    @Override
    public void cancel(){
        
    }

}
