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
import zkcbai.Command;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.DevNullHandler;
import zkcbai.unitHandlers.UnitHandler;
import static zkcbai.unitHandlers.units.AIUnit.OPTION_NONE;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public abstract class AITroop {

    public static final short OPTION_NONE = 0;//   0
    public static final short OPTION_DONT_REPEAT = (1 << 3);//   8
    public static final short OPTION_RIGHT_MOUSE_KEY = (1 << 4); //  16
    public static final short OPTION_SHIFT_KEY = (1 << 5); //  32
    public static final short OPTION_CONTROL_KEY = (1 << 6);//  64
    public static final short OPTION_ALT_KEY = (1 << 7); // 128

    protected AIUnitHandler handler;
    protected ZoneManager areaManager = null;
    protected Task task;
    protected Queue<Task> taskqueue = new LinkedList();

    public abstract float getMaxRange();
    
    public abstract float getMaxSlope();
            
    public abstract AIFloat3 getPos();
    
    public abstract void idle();
    
    public abstract UnitDef getDef();

    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        pos.y = 0;
        return pos.length();
    }
    
    public void assignAIUnitHandler(AIUnitHandler handler){
        this.handler = handler;
    }

    public Task getTask() {
        return task;
    }

    public void assignTask(Task t) {
        task = t;
        taskqueue.clear();
        doTask();
    }

    public void queueTask(Task t) {
        taskqueue.add(t);
        doTask();
    }

    private int tasksThisFrame = 0;
    private int thisFrame = 0;

    protected void doTask() {
        
        if (getCommand().getCurrentFrame() != thisFrame) {
            tasksThisFrame = 0;
            thisFrame = getCommand().getCurrentFrame();
        }
        tasksThisFrame++;
        if (tasksThisFrame > 120) {
            throw new RuntimeException("Too many tasks!");
        }
        //getCommand().mark(getPos(), "doing task");
        if (task == null || task.execute(this)) {
            task = null;
            if (!taskqueue.isEmpty()) {
                task = taskqueue.poll();
                doTask();
            } else {

                handler.troopIdle(this);
            }
        }
        
    }

    public AITroop(AIUnitHandler handler) {
        if (handler == null) {
            handler = new DevNullHandler(null, null);
        } else {
            this.areaManager = handler.getCommand().areaManager;
        }
        this.handler = handler;
    }

    public Command getCommand() {
        return handler.getCommand();
    }

    public void moveTo(AIFloat3 trg, int timeout) {
        moveTo(trg, OPTION_NONE, timeout);
    }

    public void patrolTo(AIFloat3 trg, int timeout) {
        patrolTo(trg, OPTION_NONE, timeout);
    }

    public void fight(AIFloat3 trg, int timeout) {
        fight(trg, OPTION_NONE, timeout);
    }

    public void attack(Unit trg, int timeout) {
        attack(trg, OPTION_NONE, timeout);
    }

    public void build(UnitDef building, int facing, AIFloat3 trg, int timeout) {
        build(building, facing, trg, OPTION_NONE, timeout);
    }

    public abstract void moveTo(AIFloat3 trg, short options, int timeout);

    public abstract void patrolTo(AIFloat3 trg, short options, int timeout);

    public abstract void fight(AIFloat3 trg, short options, int timeout);

    public abstract void attack(Unit trg, short options, int timeout);

    public abstract void build(UnitDef building, int facing, AIFloat3 trg, short options, int timeout);
    
    
    public abstract void setTarget(int targetUnitId);
}
