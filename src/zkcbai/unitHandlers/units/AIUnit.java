/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Group;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.LinkedList;
import java.util.Queue;
import zkcbai.UpdateListener;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.DevNullHandler;
import zkcbai.unitHandlers.UnitHandler;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class AIUnit implements UpdateListener {

    private Unit unit;
    private Task task;
    private Queue<Task> taskqueue = new LinkedList();
    private UnitHandler handler;
    private int wakeUpFrame = -1;
    private boolean dead = false;
    private ZoneManager areaManager = null;

    public AIUnit(Unit u, UnitHandler handler) {
        unit = u;
        if (handler == null) {
            handler = new DevNullHandler(null, null);
        } else {
            this.areaManager = handler.getCommand().areaManager;
        }
        this.handler = handler;
    }

    public void assignTask(Task t) {
        task = t;
        doTask();
    }

    public void queueTask(Task t) {
        taskqueue.add(t);
        doTask();
    }

    private void doTask() {
        if (task == null || task.execute(this)) {
            task = null;
            if (!taskqueue.isEmpty()) {
                task = taskqueue.poll();
                doTask();
            } else {
                
                //handler.getCommand().mark(unit.getPos(), "unitidle");
                handler.unitIdle(this);
            }
        }
    }

    public void destroyed() {
        //handler.getCommand().mark(getPos(), "dead");
        clearUpdateListener();
        dead = true;
    }

    @Override
    public void update(int frame) {
        if (dead) {
            return;
        }
        //handler.getCommand().mark(getPos(), "stop");
        unit.stop(OPTION_NONE, frame);// timeout
    }

    private void clearUpdateListener() {
        if (wakeUpFrame > handler.getCommand().getCurrentFrame()) {
            handler.getCommand().removeSingleUpdateListener(this, wakeUpFrame);
            wakeUpFrame = -1;
        }
    }

    public void idle() {
        clearUpdateListener();
        doTask();
    }

    public void pathFindingError() {
        if (task != null) {
            task.pathFindingError(this);
        } else {
            handler.getCommand().mark(getPos(), "pathFindingError");
            unit.wait(OPTION_NONE,Integer.MAX_VALUE);
        }
    }

    public AIFloat3 getPos() {
        return new AIFloat3(unit.getPos());
    }

    public Unit getUnit() {
        return unit;
    }

    public Task getTask() {
        return task;
    }

    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        return pos.length();
    }

    public void addToGroup(Group g) {
        unit.addToGroup(g, OPTION_NONE, Integer.MAX_VALUE);
    }

    public static final short OPTION_NONE = 0;//   0
    public static final short OPTION_DONT_REPEAT = (1 << 3);//   8
    public static final short OPTION_RIGHT_MOUSE_KEY = (1 << 4); //  16
    public static final short OPTION_SHIFT_KEY = (1 << 5); //  32
    public static final short OPTION_CONTROL_KEY = (1 << 6);//  64
    public static final short OPTION_ALT_KEY = (1 << 7); // 128

    public void moveTo(AIFloat3 trg, short options, int timeout) {
        //handler.getCommand().mark(trg, "move");
        clearUpdateListener();
        areaManager.executedCommand();
        unit.moveTo(trg, options, Integer.MAX_VALUE);
        if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
            wakeUpFrame = timeout;
            handler.getCommand().debug("set wakeUpFrame to " + wakeUpFrame);
        }
    }

    public void patrolTo(AIFloat3 trg, short options, int timeout) {
        clearUpdateListener();
        areaManager.executedCommand();
        unit.patrolTo(trg, options, Integer.MAX_VALUE);
        if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
            wakeUpFrame = timeout;
        }
    }

    private int lastCall = 0;
    
    public void fight(AIFloat3 trg, short options, int timeout) {
        //handler.getCommand().mark(trg, "fight");
        if (handler.getCommand().getCurrentFrame() == lastCall){
            throw new RuntimeException("Double Call to Fight");
        }
        lastCall = handler.getCommand().getCurrentFrame();
        clearUpdateListener();
        areaManager.executedCommand();
        unit.fight(trg, options, Integer.MAX_VALUE);
        if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
            wakeUpFrame = timeout;
        }
    }

    public void build(UnitDef building, int facing, AIFloat3 trg, short options, int timeout) {
        //handler.getCommand().mark(trg, "build " + building.getHumanName());
        clearUpdateListener();
        areaManager.executedCommand();
        unit.build(building, trg, facing, options, Integer.MAX_VALUE);
        if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
            wakeUpFrame = timeout;
        }
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

    public void build(UnitDef building, int facing, AIFloat3 trg, int timeout) {
        build(building, facing, trg, OPTION_NONE, timeout);
    }

    public int getHashCode() {
        return unit.hashCode();
    }

}
