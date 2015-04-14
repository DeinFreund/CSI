/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import zkcbai.Command;
import zkcbai.UnitFinishedListener;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class BuildTask extends Task implements TaskIssuer, UnitFinishedListener{

    UnitDef building;
    AIUnit result;
    AIFloat3 pos;
    int facing;
    OOAICallback clbk;
    TaskIssuer issuer;
    List<AITroop> assignedUnits;
    Command command;
    
    
    public BuildTask(UnitDef building, AIFloat3 approxPos, TaskIssuer issuer, OOAICallback clbk, Command command) {//simplified constructor
        this(building, approxPos, issuer, clbk, command, 3);
    }
    
    public BuildTask(UnitDef building, AIFloat3 approxPos, TaskIssuer issuer, OOAICallback clbk, Command command, int minDist) {//simplified constructor
        this(building, clbk.getMap().findClosestBuildSite(building, approxPos, 1000, minDist, (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0),
                (approxPos.z > clbk.getMap().getHeight() * 4) ? 2 : 0, issuer, clbk, command);
    }

    public BuildTask(UnitDef building, AIFloat3 pos, int facing, TaskIssuer issuer, OOAICallback clbk, Command command){
        //command.mark(pos, building.getHumanName() +  clbk.getMap().isPossibleToBuildAt(building, pos, facing));
        this.building = building;
        this.pos = pos;
        this.facing = facing;
        this.issuer = issuer;
        this.clbk = clbk;
        this.command = command;
        assignedUnits = new ArrayList();
        command.addUnitFinishedListener(this);
    }
    
    @Override
    public boolean execute(AITroop u) {
        if (errors > 3){
            issuer.abortedTask(this);
            return true;
        }
        if (result != null) return true;
        
        if (building.getSpeed() <= 0 && !clbk.getMap().isPossibleToBuildAt(building,pos, facing)){
            
            issuer.abortedTask(this);
            command.removeUnitFinishedListener(this);
            List<AIUnit> auc = new ArrayList();
            Collections.copy(assignedUnits, auc);
            assignedUnits.clear();
            for (AIUnit au : auc){
                if (au.getTask().equals(this)) au.idle();
            }
            return true;
        }
        if (!assignedUnits.contains(u))assignedUnits.add(u);
        
        if (u.distanceTo(pos)> 440){
            AIFloat3 trg = new AIFloat3();
            trg.interpolate( pos,u.getPos(),100f/u.distanceTo(pos));
            
            u.assignTask(new MoveTask(trg,this,command));
            u.queueTask(this);
            return false;
        }
        
        u.build(building, facing, pos, (short)0, Integer.MAX_VALUE);
        
        return false;
    }

    int errors = 0;
    
    @Override
    public void moveFailed(AITroop u) {
        errors ++;
    }

    @Override
    public void abortedTask(Task t) {
        errors ++;
        /*
        try{
            pathFindingError(((MoveTask)t).getLastExecutingUnit());
        }catch(ClassCastException ex){}*/
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public void unitFinished(AIUnit u) {
        //command.mark(u.getPos(), u.getUnit().getDef().getHumanName() + " finished");
        if (u.getUnit().getDef().equals(building) && u.distanceTo(pos)<50){
        //    command.mark(pos, "task finished");
            issuer.finishedTask(this);
            result = u;
            command.removeUnitFinishedListener(this);
        }
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }
    
}
