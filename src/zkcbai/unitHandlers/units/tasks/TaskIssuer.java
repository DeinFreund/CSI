/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers.units.tasks;

/**
 *
 * @author User
 */
public interface TaskIssuer {
    
        
    public abstract void abortedTask(Task t);
    
    public abstract void finishedTask(Task t);
    
    public abstract void reportSpam();
}
