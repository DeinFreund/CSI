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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.DevNullHandler;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.RepairTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class AIUnit extends AITroop implements UpdateListener, TaskIssuer {

    private static final float repairPercentage = 0.45f;
    private static final float repairHP = 4000f;

    private final Unit unit;
    private final int unitId;
    private int wakeUpFrame = -1;
    private int lastCommandTime = -1;
    private boolean dead = false;
    private boolean autoRepair = true;
    private boolean needRepairs = false;
    private Task preRepairTask = null;
    private RepairTask repairTask;
    private Task retreatTask = null;
    private Set<RepairListener> repairListeners = new HashSet();

    public AIUnit(Unit u, Command cmd) {
        super(cmd);
        unit = u;
        unitId = u.getUnitId();
    }

    public AIUnit(Unit u, AIUnitHandler handler) {
        super(handler);
        unit = u;
        unitId = unit.getUnitId();

    }

    public void addRepairListener(RepairListener rl) {
        repairListeners.add(rl);
    }

    public void removeRepairListener(RepairListener rl) {
        repairListeners.remove(rl);
    }

    public void setAutoRepair(boolean enabled) {
        this.autoRepair = enabled;
    }

    public boolean getAutoRepairEnabled() {
        return autoRepair;
    }

    public boolean needsRepairs() {
        return needRepairs && getAutoRepairEnabled();
    }

    public void destroyed() {
        handler.getCommand().debug("dead " + unitId);
        clearUpdateListener();
        dead = true;
    }

    public boolean isDead() {
        return dead;
    }

    public boolean isBuilding() {
        if (getDef() == null) return false;
        return getDef().getSpeed() < 0.1f;
    }

    @Override
    public List<AIUnit> getUnits() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        List<AIUnit> res = new ArrayList();
        res.add(this);
        return res;
    }

    @Override
    public void abortedTask(Task t) {
    }

    private void makeRetreatTask() {

        Collection<AIUnit> cons = repairTask.getWorkers();
        if (cons.isEmpty()) {
            cons = getCommand().getBuilderHandler().getBuilders();
        }
        if (!cons.isEmpty()) {

            AIUnit best = null;
            for (AIUnit au : cons) {
                if (best == null || best.distanceTo(getPos()) > au.distanceTo(getPos())) {
                    best = au;
                }
            }
            if (best.distanceTo(getPos()) < 200) {
                retreatTask = new WaitTask(getCommand().getCurrentFrame() + 30, this);
            } else {
                retreatTask = (new MoveTask(best.getPos(), getCommand().getCurrentFrame() + 30, this, getCommand()));
            }
        } else {

            ZoneManager.Area safe = getCommand().areaManager.getArea(getPos()).getNearestArea(new AreaChecker() {

                @Override
                public boolean checkArea(ZoneManager.Area a) {
                    return a.isSafe() && a.getZone() == ZoneManager.Zone.own;
                }
            }, getMovementType());
            retreatTask = (new MoveTask(safe.getPos(), getCommand().getCurrentFrame() + 30, this, getCommand()));
        }
    }

    @Override
    public void finishedTask(Task t) {
        if (t.equals(retreatTask)) {
            makeRetreatTask();
        }
    }

    @Override
    public void reportSpam() {
        throw new AssertionError("You messed up!");
    }

    public enum UnitType {

        raider, assault;
    }

    @Override
    public UnitDef getDef() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        return unit.getDef();
    }

    public UnitType getType() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (unit.getDef().getName().equalsIgnoreCase("armpw")) {
            return UnitType.raider;
        }
        if (unit.getDef().getName().equalsIgnoreCase("armzeus")) {
            return UnitType.assault;
        }
        return null;
        //throw new RuntimeException("Unknown UnitType");
    }

    @Override
    public void update(int frame) {
        if (dead) {
            return;
        }
        idle();
    }

    /**
     * Used to notify the unit that it's being repaired
     *
     * @return whether unit still needs repairs
     */
    public boolean repaired() {
        if (!needRepairs) {
            return true;
        }
        if (getUnit().getHealth() / getDef().getHealth() > 0.99f) {
            needRepairs = false;
            if (preRepairTask != null) {
                assignTask(preRepairTask);
            } else {
                idle();
            }

            for (RepairListener rl : repairListeners) {
                rl.finishedRepairs(this);
            }
            return true;
        }
        return false;
    }

    /**
     * Used to notify the unit that it's being damaged
     *
     * @param attacker Enemy or null
     * @param damage float representing absolute damage
     */
    public void damaged(Enemy attacker, float damage) {
        if (autoRepair && getUnit().getHealth() / getDef().getHealth() < repairPercentage && getUnit().getHealth() < repairHP && !isBuilding()) {
            needRepairs = true;
            preRepairTask = task;
            repairTask = getCommand().getBuilderHandler().requestRepairs(this);
            makeRetreatTask();
            idle();
        }
    }

    public void checkIdle() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (handler instanceof DevNullHandler) {
            return;
        }
        if ((wakeUpFrame < 0 || wakeUpFrame > 1000000) && handler.getCommand().getCurrentFrame() - lastCommandTime > 100
                && unit.getCurrentCommands().isEmpty()) {
            handler.getCommand().mark(getPos(), "reawaken");
            idle();
        }
        String tname = "";
        if (task != null) {
            tname = task.getClass().getName();
        }
        //getCommand().debug("not idle doing " + tname + " because "  + (wakeUpFrame < 0 || wakeUpFrame > 1000000) + 
        //        "&&" + (handler.getCommand().getCurrentFrame() - lastCommandTime > 100) +
        //        "&&" + unit.getCurrentCommands().isEmpty());
    }

    private void clearUpdateListener() {
        if (handler != null && handler.getCommand() != null && wakeUpFrame > handler.getCommand().getCurrentFrame()) {
            //getCommand().debug("wake up removed");
            handler.getCommand().removeSingleUpdateListener(this, wakeUpFrame);
            wakeUpFrame = -1;
        }
    }

    int lastIdle = -10;

    @Override
    public void idle() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (handler.getCommand() == null) {
            return;
        }
        for (AIUnit au : handler.getCommand().getFactoryHandler().getUnits()) {
            if (distanceTo(au.getPos()) < 70 && getDef().getSpeed() > 0) {
                AIFloat3 npos = new AIFloat3(au.getPos());
                npos.x += 350;
                npos = MoveTask.randomize(npos, 150);
                moveTo(npos, getCommand().getCurrentFrame() + 100);
                return;
            }
        }
        lastIdle = getCommand().getCurrentFrame();
        clearUpdateListener();

        doTask();
    }

    @Override
    protected void doTask() {

        if (autoRepair && needRepairs && !isBuilding() && handler.retreatForRepairs(this) && (task == null || !task.equals(retreatTask))) {

            for (RepairListener rl : repairListeners) {
                rl.retreating(this);
            }
            assignTask(retreatTask);
        }
        super.doTask();
    }

    public boolean isRetreating() {
        return task.equals(retreatTask);
    }

    public void moveFailed() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (task != null) {
            task.moveFailed(this);
        } else {
            handler.getCommand().debug("Warning: move failed");
            //unit.wait(OPTION_NONE, Integer.MAX_VALUE);
        }
    }

    @Override
    public float getEfficiencyAgainst(Enemy e) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        return getEfficiencyAgainst(e.getDef());
    }

    @Override
    public float getEfficiencyAgainst(UnitDef ud) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        return handler.getCommand().killCounter.getEfficiency(unit.getDef(), ud);
    }

    @Override
    public AIFloat3 getPos() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        return new AIFloat3(unit.getPos());
    }

    public Unit getUnit() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        return unit;
    }

    /**
     *
     * @param g
     * @deprecated use AISquad instead
     */
    @Deprecated
    public void addToGroup(Group g) {
        unit.addToGroup(g, OPTION_NONE, Integer.MAX_VALUE);
    }

    @Override
    public void moveTo(AIFloat3 trg, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.moveTo(trg, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
                //handler.getCommand().debug("set wakeUpFrame to " + wakeUpFrame);
            }
        }
    }

    @Override
    public void attack(Unit trg, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        //handler.getCommand().mark(trg, "move");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.attack(trg, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
                //handler.getCommand().debug("set wakeUpFrame to " + wakeUpFrame);
            }
        }
    }

    @Override
    public void repair(Unit trg, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.repair(trg, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
                //handler.getCommand().debug("set wakeUpFrame to " + wakeUpFrame);
            }
        }
    }

    @Override
    public void reclaimArea(AIFloat3 pos, float radius, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.reclaimInArea(pos, radius, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
                //handler.getCommand().debug("set wakeUpFrame to " + wakeUpFrame);
            }
        }
    }

    @Override
    public void patrolTo(AIFloat3 trg, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.patrolTo(trg, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void wait(int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        //handler.getCommand().mark(trg, "fight");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    private int lastCall = 0;

    @Override
    public void fight(AIFloat3 trg, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (handler.getCommand().getCurrentFrame() == lastCall) {
        }
        lastCall = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.fight(trg, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void attackGround(AIFloat3 trg, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (handler.getCommand().getCurrentFrame() == lastCall) {
        }
        lastCall = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        List<Float> floats = new ArrayList();
        floats.add(trg.x);
        floats.add(trg.y);
        floats.add(trg.z);
        try {
            unit.executeCustomCommand(20, floats, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void build(UnitDef building, int facing, AIFloat3 trg, short options, int timeout) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        //handler.getCommand().mark(trg, "build " + building.getHumanName());
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.build(building, trg, facing, options, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }

        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void setTarget(int targetUnitId) {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        List<Float> list = new ArrayList();
        list.add((float) targetUnitId);
        unit.executeCustomCommand(34923, list, OPTION_NONE, lastIdle);
    }

    @Override
    public float getMaxRange() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        return unit.getMaxRange();
    }

    @Override
    public float getMaxSlope() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        return unit.getDef().getMoveData().getMaxSlope();
    }

    public float getMakesEnergy() {
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId);
            handler.getCommand().unitDestroyed(unit, null);
        }
        if (getDef() == null) return 0f;
        if (getDef().getName().contains("com")) {
            return 6;
        }
        return getDef().getCustomParams().containsKey("income_energy") ? Float.valueOf(getDef().getCustomParams().get("income_energy")) : 0f;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AIUnit) {
            return equals((AIUnit) o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return unitId;
    }

    public boolean equals(AIUnit u) {
        return u != null && hashCode() == u.hashCode();
    }

}
