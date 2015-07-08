/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import zkcbai.Command;
import zkcbai.UnitCreatedListener;
import zkcbai.UnitFinishedListener;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class BuildTask extends Task implements TaskIssuer, UnitFinishedListener, UnitCreatedListener{

    UnitDef building;
    Unit result;
    boolean resultFinished = false;
    boolean aborted = false;
    AIFloat3 pos;
    int facing;
    OOAICallback clbk;
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
        super(issuer);
        //command.mark(pos, building.getHumanName() +  clbk.getMap().isPossibleToBuildAt(building, pos, facing));
        this.building = building;
        this.pos = pos;
        this.facing = facing;
        this.clbk = clbk;
        this.command = command;
        this.lastExecution = command.getCurrentFrame();
        if (building.getSpeed() <= 0 && !command.isPossibleToBuildAt(building, pos, facing)) {
            //throw new RuntimeException("Invalid BuildTask parameters: Unable to build at location");
        }
        assignedUnits = new ArrayList();
        command.addUnitFinishedListener(this);
        command.addUnitCreatedListener(this);
    }
    
    private void cleanup() {

        command.removeUnitFinishedListener(this);
        command.removeUnitCreatedListener(this);
        List<AIUnit> auc = new ArrayList();
        Collections.copy(assignedUnits, auc);
        assignedUnits.clear();
        for (AIUnit au : auc) {
            if (au.getTask().equals(this)) {
                au.idle();
            }
        }
    }
    
    List<String> errorMessages = new ArrayList();
    
    public boolean isDone(){
        return resultFinished;
    }
    
    public boolean isAborted(){
        return aborted;
    }
    
    /**
     * 
     * @param u AITroop to use for task
     * @return true if task has been finished, false otherwise
     */
    @Override
    public boolean execute(AITroop u) {
        lastExecution = command.getCurrentFrame();
        if (errors >5 + 3* assignedUnits.size()){
            aborted = true;
            completed(u);
            command.debug("aborted task execution because of errors");
            for (String s : errorMessages){
                command.debug(s);
            }
            issuer.abortedTask(this);
            cleanup();
            return true;
        }
        if (result != null && resultFinished) {
            command.debug("aborted task execution because of finished");
            completed(u);
            return true;
        }
        
        
        if ((building.getSpeed() <= 0 && !command.isPossibleToBuildAt(building,pos, facing)) && result == null){
            errors ++;
            errorMessages.add("Impossible to build at location");
            u.fight(pos, command.getCurrentFrame()+ 20);
            
            
            return false;
        }
        if (!assignedUnits.contains(u))assignedUnits.add(u);
        
        if (u.distanceTo(pos)> 440){
            AIFloat3 trg = new AIFloat3();
            trg.interpolate( pos,u.getPos(),100f/u.distanceTo(pos));
            
            u.assignTask(new MoveTask(trg,command.getCurrentFrame() + 250,this,command).queue(this));
            return false;
        }
        if (result != null){
            u.repair(result, command.getCurrentFrame() + 100);
        }else{
            u.build(building, facing, pos, (short)0, command.getCurrentFrame() + 100);
        }
        
        return false;
    }

    int errors = 0;
    
    @Override
    public void moveFailed(AITroop u) {
        errors ++;
            errorMessages.add("MoveFailed");
    }

    @Override
    public void abortedTask(Task t) {
        errors ++;
        
        errorMessages.add("Aborted MoveTask");
        /*
        try{
            pathFindingError(((MoveTask)t).getLastExecutingUnit());
        }catch(ClassCastException ex){}*/
    }

    @Override
    public BuildTask clone(){
        BuildTask as = new BuildTask(building, pos, facing, issuer, clbk, command);
        as.result = this.result;
        as.queued = this.queued;
        return as;
    }
    
    public UnitDef getBuilding(){
        return building;
    }
    
    public AIFloat3 getPos(){
        return pos;
    }
    
    public Collection<AITroop> getWorkers(){
        return assignedUnits;
    }
    
    @Override
    public Object getResult() {
        return command.getAIUnit(result);
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public void unitFinished(AIUnit u) {
        if (u.getUnit().getDef().equals(building) && u.distanceTo(pos)<50){
            command.debug("finished " + building.getHumanName());
            completed(u);
            result = u.getUnit();
            issuer.finishedTask(this);
            resultFinished = true;
            cleanup();
        }
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void unitCreated(Unit u, AIUnit builder) {
        if (u.getDef().equals(building) && Command.distance2D(pos, u.getPos())<40){
            result = u;
        }
    }
    
}
