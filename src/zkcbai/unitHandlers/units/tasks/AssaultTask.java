/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import java.util.ArrayList;
import java.util.List;
import javax.xml.ws.handler.MessageContext;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class AssaultTask extends Task implements TaskIssuer{

    AIFloat3 target;
    Command command;
    List<AITroop> aitroops;
    AIFloat3 lastpos;
    int lastTime = 0;
    
    public AssaultTask(AIFloat3 target, Command command, TaskIssuer issuer){
        super(issuer);
        this.target = target;
        if (target == null) throw new RuntimeException("Target of AssaultTask can't be null");
        this.command = command;
        aitroops = new ArrayList();
    }
    
    @Override
    public AssaultTask clone(){
        AssaultTask as = new AssaultTask(target, command, issuer);
        as.queued = this.queued;
        if (as.target == null) throw new RuntimeException("Target is null");
        return as;
    }
    
    @Override
    public boolean execute(AITroop u) {
        if (!aitroops.contains(u)) aitroops.add(u);
        if (command.getCurrentFrame() - lastTime > 300){
            if (u.getArea().getNearbyEnemies().isEmpty() && lastpos != null && u.distanceTo(lastpos) < 250){
                command.debug("Assault finished because no enemy in sight");
                completed(u);
                issuer.finishedTask(this);
                return true;
            }
            lastTime = command.getCurrentFrame();
            lastpos = u.getPos();
        }
        
        float maxscore = 0;
        Enemy best = null;
        float danger = 0;
        for (Enemy e : u.getArea().getNearbyEnemies()){
            if (!e.isAlive() || e.isTimedOut()) continue;
            danger += e.getMetalCost() / Math.max(0.3,u.getEfficiencyAgainst(e)) * e.getRelativeHealth();
            float score = Math.max(2f*e.getDPS(),e.getMetalCost())/e.getHealth() //value
                    * Math.min(u.getEfficiencyAgainst(e),3) * u.getMetalCost() / e.getMetalCost() / e.getHealth(); //ease to kill
            if (score > maxscore){
                maxscore = score;
                best = e;
            }
        }
        if (best != null /*&& danger < 2*u.getMetalCost()*/){
            u.assignTask(new AttackTask(best, command.getCurrentFrame() + 30, this, command).queue(this));
            return false;
        }
        best = null;
        maxscore = 0;
        for (Enemy e :command.areaManager.getArea(target).getNearbyEnemies()){
            if (!e.isAlive() || e.isTimedOut()) continue;
            float score = Math.max(2f*e.getDPS(),e.getMetalCost())/e.getHealth() //value
                    * Math.min(u.getEfficiencyAgainst(e),3) * u.getMetalCost() / e.getMetalCost() / e.getHealth(); //ease to kill
            if (score > maxscore){
                maxscore = score;
                best = e;
            }
        }
        if (best == null && u.distanceTo(target)<80) {
            //command.mark(u.getPos(), "finished Assault");
            completed(u);
            issuer.finishedTask(this);
            return true;
        }
        if (best == null) {
            //command.mark(u.getPos(), "assaultmove");
            u.assignTask(new MoveTask(target, command.getCurrentFrame() + 20, this, u.getDef(), command).queue(this));
        } else {
            //command.mark(u.getPos(), "assaultattack");
            u.assignTask(new AttackTask(best, command.getCurrentFrame() + 30, this, command).queue(this));
        }
        return false;
    }

    @Override
    public void moveFailed(AITroop u) {
    }

    @Override
    public Object getResult() {
        return null;
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }
    
    public final List<AITroop> getAITroops(){
        return aitroops;
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed Tasks!");
    }
    
    
    @Override
    public void cancel(){
        
    }
    
}
