/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers.units.tasks;

import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public abstract class Task {
    protected long taskId = System.currentTimeMillis();//should work..
            
    public abstract boolean execute(AIUnit u);
    public abstract void pathFindingError(AIUnit u);
    
    public long getId(){
        return taskId;
    }
    
    public boolean equals(Task t){
        return taskId == t.getId();
    }
}
