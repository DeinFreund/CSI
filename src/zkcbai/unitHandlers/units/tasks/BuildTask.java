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
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.UnitFinishedListener;

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
    List<AIUnit> assignedUnits;
    
    
    public BuildTask(UnitDef building, AIFloat3 approxPos, TaskIssuer issuer, OOAICallback clbk, Command command) {//simplified constructor

        this(building, clbk.getMap().findClosestBuildSite(building, approxPos, 1000, 3, (approxPos.z * 2 > clbk.getMap().getHeight()) ? 2 : 0),
                (approxPos.z * 2 > clbk.getMap().getHeight()) ? 2 : 0, issuer, clbk, command);
    }

    public BuildTask(UnitDef building, AIFloat3 pos, int facing, TaskIssuer issuer, OOAICallback clbk, Command command){
        this.building = building;
        this.pos = pos;
        this.facing = facing;
        this.issuer = issuer;
        this.clbk = clbk;
        assignedUnits = new ArrayList();
        command.addUnitFinishedListener(this);
        try { Thread.sleep(1); } catch (InterruptedException ex) {} //magic
    }
    
    @Override
    public boolean execute(AIUnit u) {
        if (result != null) return true;
        if (!clbk.getMap().isPossibleToBuildAt(building, pos, facing)){
            issuer.abortedTask(this);
            List<AIUnit> auc = new ArrayList();
            Collections.copy(assignedUnits, auc);
            assignedUnits.clear();
            for (AIUnit au : auc){
                if (au.getTask().equals(this)) au.idle();
            }
            return true;
        }
        if (!assignedUnits.contains(u))assignedUnits.add(u);
        if (u.distanceTo(pos)> 100){
            u.assignTask(new MoveTask(pos,this));
            u.queueTask(this);
            return false;
        }
        u.build(building, facing, pos, (short)0, -1);
        return false;
    }

    @Override
    public void pathFindingError(AIUnit u) {
        
        u.assignTask(null);
    }

    @Override
    public void abortedTask(Task t) {
        try{
            pathFindingError(((MoveTask)t).getLastExecutingUnit());
        }catch(ClassCastException ex){}
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
        if (u.getUnit().getDef().equals(building) && u.getPos().equals(pos)){
            issuer.finishedTask(this);
            result = u;
        }
    }
    
}
