/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units.tasks;

import java.util.ArrayList;
import java.util.List;
import zkcbai.unitHandlers.units.AITroop;

/**
 *
 * @author User
 */
public abstract class Task {

    protected TaskIssuer issuer;
    protected List<Task> queued;
    
    public Task(TaskIssuer issuer){
        this.issuer = issuer;
        queued = new ArrayList();
    }
    
    public abstract boolean execute(AITroop u);

    public abstract void moveFailed(AITroop u);

    private String info = "";

    public Task setInfo(String info) {
        this.info = info;
        return this;
    }
    
    public Task queue(Task t) {
        queued.add(t);
        return this;
    }
    
    public abstract Task clone();
    
    protected void completed(AITroop t){
        
        for (Task ta : queued){
            t.queueTask(ta, false);
        }
    }

    public String getInfo() {
        return this.info;
    }

    public abstract Object getResult();
}
