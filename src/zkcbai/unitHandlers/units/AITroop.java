/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import zkcbai.Command;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.DevNullHandler;
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

    protected Set<Area> reachableAreas;
    private final AreaChecker reachable = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return reachableAreas.contains(a);
        }
    };

    public AITroop(Command cmd) {
        this(new DevNullHandler(cmd, cmd.getCallback()));
    }

    public AITroop(AIUnitHandler handler) {
        if (handler == null || handler.getCommand() == null) {
            throw new AssertionError("AITroop without handler | handler has no command");
        } else {
            this.areaManager = handler.getCommand().areaManager;
        }
        this.handler = handler;
    }

    public abstract float getMaxRange();

    public abstract float getMaxSlope();

    public Set<Area> getReachableAreas() {
        return reachableAreas;
    }

    public Area getArea() {
        return areaManager.getArea(getPos()).getNearestArea(reachable);
    }

    public abstract AIFloat3 getPos();

    public abstract void idle();

    public abstract UnitDef getDef();

    public abstract Collection<AIUnit> getUnits();

    public abstract float getEfficiencyAgainst(Enemy e);

    public abstract float getEfficiencyAgainst(UnitDef ud);

    public MovementType getMovementType() {
        return MovementType.getMovementType(getDef());
    }

    public float getMetalCost() {
        float res = 0;
        for (AIUnit u : getUnits().toArray(new AIUnit[getUnits().size()])) {
            if (u.getDef() != null) {
                res += u.getDef().getCost(handler.getCommand().metal);
            } else {
                handler.getCommand().debug("Requested MetalCost but UnitDef is null");
                handler.getCommand().unitDestroyed(u.getUnit(), null);
            }
        }
        return res;
    }

    public float distanceTo3D(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        return pos.length();
    }

    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        pos.y = 0;
        return pos.length();
    }

    private Enemy nearestEnemy = null;
    private int lastNearestEnemy = -100;

    /**
     * Distance is measured by Geometric Distance - Enemy Range
     *
     * @return nearest enemy within 1200 elmos
     */
    public Enemy getNearestEnemy() {
        Command command = handler.getCommand();
        if ((command.getCurrentFrame() - lastNearestEnemy > 20 && nearestEnemy == null)
                || handler.getCommand().getCurrentFrame() - lastNearestEnemy > 70 || (nearestEnemy != null && !nearestEnemy.isAlive())) {
            Set<Enemy> enemies = command.getEnemyUnitsIn(getPos(), 700);
            if (enemies.isEmpty()) {
                enemies = command.getEnemyUnitsIn(getPos(), 1500);
            }
            nearestEnemy = null;
            for (Enemy e : enemies) {
                if (e.getDef().isAbleToFly() && !getDef().isAbleToFly()) {
                    continue;
                }
                if (nearestEnemy == null || nearestEnemy.distanceTo(getPos()) - nearestEnemy.getMaxRange() > e.distanceTo(getPos()) - e.getMaxRange()) {
                    nearestEnemy = e;
                }
            }
            lastNearestEnemy = command.getCurrentFrame();
        }
        return nearestEnemy;
    }

    public void assignAIUnitHandler(AIUnitHandler handler) {
        if (handler == null || handler.getCommand() == null) {
            throw new AssertionError("AITroop without handler | handler has no command");
        }
        if (!getUnits().isEmpty()) {
            handler.getCommand().debug(getUnits().iterator().next().getUnit().getUnitId() + " (" + getDef().getHumanName() + ") is now controlled by a " + handler.getClass().getName());
        }
        this.handler = handler;
    }

    public Task getTask() {
        return task;
    }

    public void assignTask(Task t) {
        assignTask(t, true);
    }

    private void assignTask(Task t, boolean execute) {
        task = t;
        taskqueue.clear();
        if (execute) {
            doTask();
        }
    }

    public AIUnit getNearestBuilding() {
        AIUnit best = null;
        for (AIUnit au : handler.getCommand().areaManager.getArea(getPos()).getNearbyBuildings()) {
            if (best == null || best.distanceTo(getPos()) > au.distanceTo(getPos())) {
                best = au;
            }
        }
        return best;
    }

    /**
     * Tasks are easily overwritten, use other methods to queue tasks securely!
     *
     * @param t
     */
    public void queueTask(Task t) {
        queueTask(t, true);
    }

    /**
     * Tasks are easily overwritten, use other methods to queue tasks securely!
     *
     * @param t
     * @param execute
     */
    public void queueTask(Task t, boolean execute) {
        taskqueue.add(t);
        if (execute) {
            doTask();
        }
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
            throw new RuntimeException("Too many tasks for " + getDef().getHumanName());
        }
        //getCommand().mark(getPos(), "doing task");
        if (task == null || task.execute(this)) {
            task = null;
            if (!taskqueue.isEmpty()) {
                task = taskqueue.poll();
                long t = System.currentTimeMillis();
                doTask();
                t = System.currentTimeMillis() - t;
                if (t > 1) {
                    getCommand().debug("Executing " + task.getClass().getName() + " took " + t + " ms.");
                }
            } else {
                long t = System.currentTimeMillis();
                handler.troopIdle(this);
                t = System.currentTimeMillis() - t;
                if (t > 1) {
                    getCommand().debug("Requesting new task for " + getDef().getHumanName() + " by " + handler.getClass().getName() + " took " + t + " ms.");
                }
            }
        }

    }

    public Command getCommand() {
        Command ret = handler.getCommand();
        if (ret == null) {
            throw new AssertionError(handler.getClass().getName() + " has command==null");
        }
        return ret;
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

    public void attack(Enemy trg, int timeout) {
        attack(trg, OPTION_NONE, timeout);
    }

    public void fireDGun(AIFloat3 target, int timeout) {
        fireDGun(target, OPTION_NONE, timeout);
    }

    public void dropPayload(int timeout) {
        dropPayload(OPTION_NONE, timeout);
    }

    public void repair(Unit trg, int timeout) {
        repair(trg, OPTION_NONE, timeout);
    }

    /**
     *
     * @return average health of units
     */
    public float getHealth() {
        float sum = 0;
        for (AIUnit au : getUnits()) {
            sum += au.getHealth();
        }
        return sum / getUnits().size();
    }

    public void loadUnit(Unit trg, int timeout) {
        loadUnit(trg, OPTION_NONE, timeout);
    }

    public void reclaimArea(AIFloat3 pos, float radius, int timeout) {
        reclaimArea(pos, radius, OPTION_NONE, timeout);
    }

    public void build(UnitDef building, int facing, AIFloat3 trg, int timeout) {
        build(building, facing, trg, OPTION_NONE, timeout);
    }

    public abstract void moveTo(AIFloat3 trg, short options, int timeout);

    public abstract void patrolTo(AIFloat3 trg, short options, int timeout);

    public abstract void fight(AIFloat3 trg, short options, int timeout);

    public abstract void attackGround(AIFloat3 trg, short options, int timeout);

    public abstract void attack(Enemy trg, short options, int timeout);

    public abstract void repair(Unit trg, short options, int timeout);

    public abstract void reclaimArea(AIFloat3 pos, float radius, short options, int timeout);

    public abstract void build(UnitDef building, int facing, AIFloat3 trg, short options, int timeout);

    public abstract void loadUnit(Unit unit, short options, int timeout);

    public abstract void fireDGun(AIFloat3 target, short options, int timeout);

    public abstract void dropPayload(short options, int timeout);

    public abstract void wait(int timeout);

    public abstract void setTarget(int targetUnitId);
}
