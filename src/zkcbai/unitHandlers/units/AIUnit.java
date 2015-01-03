/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.LinkedList;
import java.util.Queue;
import zkcbai.unitHandlers.DevNullHandler;
import zkcbai.unitHandlers.UnitHandler;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class AIUnit {
    private Unit unit;
    private Task task;
    private Queue<Task> taskqueue = new LinkedList();
    private UnitHandler handler;
    
    public AIUnit(Unit u, UnitHandler handler){
        unit = u;
        if (handler == null) handler = new DevNullHandler(null,null);
        this.handler = handler;
    }
    
    public void assignTask(Task t){
        task = t;
        doTask();
    }
    public void queueTask(Task t){
        taskqueue.add(t);
        doTask();
    }
    
    private void doTask(){
        if (task == null || task.execute(this)){
            task = null;
            if (!taskqueue.isEmpty()){
                task = taskqueue.poll();
                doTask();
            }else{
                handler.unitIdle(this);
            }
        }
    }
    
    public void destroyed(){
        
    }
    
    public void idle(){
        doTask();
    }
    
    public void pathFindingError(){
        task.pathFindingError(this);
    }
    
    public AIFloat3 getPos(){
        return unit.getPos();
    }
    
    public Unit getUnit(){
        return unit;
    }
    
    public Task getTask(){
       return task; 
    }
    
    public float distanceTo(AIFloat3 trg){
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        return pos.length();
    }

    public static final short OPTION_NONE = 0;//   0
    public static final short OPTION_DONT_REPEAT = (1 << 3);//   8
    public static final short OPTION_RIGHT_MOUSE_KEY = (1 << 4); //  16
    public static final short OPTION_SHIFT_KEY = (1 << 5); //  32
    public static final short OPTION_CONTROL_KEY = (1 << 6);//  64
    public static final short OPTION_ALT_KEY = (1 << 7); // 128
    
    public void moveTo(AIFloat3 trg, short options, int timeout){
        unit.moveTo(trg, options, timeout);
    }
    public void patrolTo(AIFloat3 trg, short options, int timeout){
        unit.patrolTo(trg, options, timeout);
    }
    public void fight(AIFloat3 trg, short options, int timeout){
        unit.fight(trg, options, timeout);
    }
    public void build(UnitDef building,int facing, AIFloat3 trg, short options, int timeout){
        unit.build(building,trg,facing, options, timeout);
    }
    public int getHashCode(){
        return unit.hashCode();
    }
}
