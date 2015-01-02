/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers.units.tasks;

import com.springrts.ai.oo.AIFloat3;
import java.util.logging.Level;
import java.util.logging.Logger;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class MoveTask extends Task{

    public static final int SAFE_MOVE = 1;
    public static final int AGGRESSIVE_MOVE = 2;
    
    private AIFloat3 target;
    private int options;
    private int errors = 0;
    private TaskIssuer issuer;
    private AIUnit lastUnit;
    
    public MoveTask(AIFloat3 target, TaskIssuer issuer){
        this(target,0,issuer);
    }
    public MoveTask(AIFloat3 target, int options, TaskIssuer issuer){
        this.target = target;
        this.options = options;
        this.issuer = issuer;
        try { Thread.sleep(1); } catch (InterruptedException ex) {}
    }
    
    private boolean isSet(int option){
        return (options & option) == option;
    }
    
    public AIUnit getLastExecutingUnit(){
        return lastUnit;
    }
    
    @Override
    public boolean execute(AIUnit u) {
        if (errors > 5){
            issuer.abortedTask(this);
            return true;
        }
        lastUnit = u;
        if (u.distanceTo(target) < 40) return true;
        if (isSet(SAFE_MOVE)){
            
            u.moveTo(target, (short)0, -1);
        }else 
        if (isSet(AGGRESSIVE_MOVE)){
            
            u.moveTo(target, (short)0, -1);
        }else{
            u.moveTo(target, (short)0, -1);
        }
        return false;
    }
    
    @Override
    public void pathFindingError(AIUnit u) {
        errors ++;
        target.x += Math.random()*20-10;
        target.z += Math.random()*20-10;
    }

    @Override
    public Object getResult() {
        return null;
    }
    
    

    
}
