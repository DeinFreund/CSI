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
    public final int taskID = taskIDCounter++;
    int lastExecution = -1;
    
    private static int taskIDCounter = 0;
    
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
    
    public int getTaskId(){
        return taskID;
    }
    
    public TaskIssuer getIssuer(){
        return issuer;
    }
    
    public abstract Task clone();
    
    protected void completed(AITroop t){
        if (t == null) return;
        for (Task ta : queued){
            t.queueTask(ta, false);
        }
    }
    
    public List<Task> getQueue(){
        return queued;
    }

    public String getInfo() {
        return this.info;
    }
    
    public TaskType getTaskType(){
        return TaskType.Various;
    }
    
    public abstract void cancel();
    
    /**
     * Checks whether there has been any work(execution) done on the task since frame
     * @param frame
     * @return 
     */
    public boolean isBeingWorkedOn(int frame){
        if (lastExecution < 0) throw new UnsupportedOperationException("IsBeingWorkedOnNotImplemented");
        return lastExecution > frame;
    }

    public abstract Object getResult();
}
