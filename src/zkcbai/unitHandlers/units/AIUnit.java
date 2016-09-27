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
import com.springrts.ai.oo.clb.WeaponMount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.Pathfinder;
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

    private static final float REPAIR_PERCENTAGE = 0.55f;
    private static final float REPAIR_HP = 4000f;

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

        if (u.getDef().getSpeed() > 0) {
            reachableAreas = handler.getCommand().areaManager.getArea(u.getPos()).getConnectedAreas(Pathfinder.MovementType.getMovementType(u.getDef()));
        } else {
            reachableAreas = new HashSet();
            reachableAreas.add(handler.getCommand().areaManager.getArea(u.getPos()));
        }
    }

    public AIUnit(Unit u, AIUnitHandler handler) {
        super(handler);
        unit = u;
        unitId = unit.getUnitId();
        handler.getCommand().debug(unitId + " (" + getDef().getHumanName() + ") is now controlled by a " + handler.getClass().getName());

        if (u.getDef().getSpeed() > 0) {
            reachableAreas = handler.getCommand().areaManager.getArea(u.getPos()).getConnectedAreas(Pathfinder.MovementType.getMovementType(u.getDef()));
        } else {
            reachableAreas = new HashSet();
            reachableAreas.add(handler.getCommand().areaManager.getArea(u.getPos()));
        }
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
        if (getDef() == null) {
            return false;
        }
        return getDef().getSpeed() < 0.1f;
    }

    @Override
    public List<AIUnit> getUnits() {
        checkDead();
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
            cons = getCommand().getNanoHandler().getUnits();
        }
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
            if (best.distanceTo(getPos()) < 300) {
                retreatTask = new WaitTask(getCommand().getCurrentFrame() + 30, this);
            } else {
                retreatTask = (new MoveTask(best.getPos(), getCommand().getCurrentFrame() + 30, this, getCommand()));
            }
        } else {
            retreatTask = (new MoveTask(getArea().getNearestArea(getCommand().areaManager.SAFE, getMovementType()).getPos(), getCommand().getCurrentFrame() + 30, this, getCommand()));
        }
    }

    @Override
    public void finishedTask(Task t) {
        if (t.equals(retreatTask) && !dead) {
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
        checkDead();
        return unit.getDef();
    }

    public UnitType getType() {
        checkDead();
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

    @Override
    public float getHealth() {
        return getUnit().getHealth();
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
            getCommand().mark(getPos(), "fully repaired");
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
        if (autoRepair && getUnit().getHealth() / getDef().getHealth() < REPAIR_PERCENTAGE && getUnit().getHealth() < REPAIR_HP && !isBuilding() && !needRepairs) {
            getCommand().mark(getPos(), "need repairs");
            needRepairs = true;
            preRepairTask = task;
            repairTask = getCommand().getBuilderHandler().requestRepairs(this);
            makeRetreatTask();
            idle();
        }
    }

    public float getDPS() {
        checkDead();
        return Command.getDPS(getDef());
    }

    public void checkDead() {
        if (unit.getPos().lengthSquared() < 0.1) {
            getCommand().debug("aiunit " + unitId + "(" + (unit.getDef() != null ? unit.getDef().getHumanName() : "null") + ") might be dead");
        }
        if (dead) {
            handler.getCommand().debug("polled dead aiunit " + unitId + "");
            handler.getCommand().debugStackTrace();
            handler.getCommand().addSingleUpdateListener(new UpdateListener() {
                @Override
                public void update(int frame) {
                    handler.getCommand().unitDestroyed(unit, null);
                }
            }, handler.getCommand().getCurrentFrame() + 1);
        }
    }

    public void checkIdle() {
        checkDead();
        if (handler instanceof DevNullHandler || handler.getCommand() == null) {
            return;
        }
        if ((wakeUpFrame < 0 || wakeUpFrame > 1000000) && handler.getCommand().getCurrentFrame() - lastCommandTime > 30
                && unit.getCurrentCommands().isEmpty()) {
            handler.getCommand().debug("reawaken " + getUnit().getUnitId() + "(" + getDef().getHumanName() + ") controlled by " + handler.getClass().getName());
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
        checkDead();
        if (handler.getCommand() == null) {
            return;
        }
        for (AIUnit au : handler.getCommand().getFactoryHandler().getUnits()) {
            if (distanceTo(au.getPos()) < 70 && getDef().getSpeed() > 0 && !au.getDef().getName().contains("gunship") && !au.getDef().getName().contains("plane")) {
                AIFloat3 npos = new AIFloat3(au.getPos());
                npos.x += 400;
                npos = MoveTask.randomize(npos, 300);
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
        checkDead();
        
        if (autoRepair && needRepairs && !isBuilding() && handler.retreatForRepairs(this) && (task == null || !task.equals(retreatTask))) {

            for (RepairListener rl : repairListeners) {
                rl.retreating(this);
            }
            assignTask(retreatTask);
            getCommand().debug(getDef().getHumanName() + " retreating");
        }
        super.doTask();
    }

    public boolean isRetreating() {
        return task.equals(retreatTask);
    }

    public void moveFailed() {
        checkDead();
        if (task != null) {
            task.moveFailed(this);
        } else {
            handler.getCommand().debug("Warning: move failed");
            //unit.wait(OPTION_NONE, Integer.MAX_VALUE);
        }
    }

    @Override
    public float getEfficiencyAgainst(Enemy e) {
        checkDead();
        return getEfficiencyAgainst(e.getDef());

    }

    @Override
    public float getEfficiencyAgainst(UnitDef ud) {
        checkDead();
        return handler.getCommand().killCounter.getEfficiency(unit.getDef(), ud);
    }

    @Override
    public AIFloat3 getPos() {
        checkDead();
        return new AIFloat3(unit.getPos());
    }

    public Unit getUnit() {
        checkDead();
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
        checkDead();
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
    public void attack(Enemy trg, short options, int timeout) {
        checkDead();
        //handler.getCommand().mark(trg, "move");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            if (trg.getUnit() != null && trg.isVisible()) {
                unit.attack(trg.getUnit(), options, Integer.MAX_VALUE);
            } else {
                unit.moveTo(trg.getPos(), options, Integer.MAX_VALUE);
                timeout = getCommand().getCurrentFrame() + 15;
            }
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
        checkDead();
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
    public void loadUnit(Unit trg, short options, int timeout) {
        checkDead();
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            List<Unit> units = new ArrayList<>();
            units.add(trg);
            unit.loadUnits(units, options, Integer.MAX_VALUE);
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
        checkDead();
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
        checkDead();
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
    public void fireDGun(AIFloat3 trg, short options, int timeout) {
        checkDead();
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.dGunPosition(trg, options, Integer.MAX_VALUE);
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
        checkDead();
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
        checkDead();
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (handler.getCommand().getCurrentFrame() == lastCall) {
        }
        lastCall = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            if (getMaxRange() > 400 && getNearestEnemy() != null && getNearestEnemy().distanceTo(getPos()) < getMaxRange() * 0.9) {
                unit.moveTo(getArea().getNearestArea(getCommand().areaManager.SAFE).getPos(), options, Integer.MAX_VALUE);
            } else {
                unit.fight(trg, options, Integer.MAX_VALUE);
            }
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

    public static enum RetreatState {
        RetreatOff, Retreat30, Retreat65, Retreat99
    }

    public void setLandWhenIdle(boolean land) {
        checkDead();
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (handler.getCommand().getCurrentFrame() == lastCall) {
        }
        lastCall = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        try {
            unit.setIdleMode(land ? 1 : 0, OPTION_NONE, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
    }

    public void setRetreat(RetreatState state) {
        checkDead();
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (handler.getCommand().getCurrentFrame() == lastCall) {
        }
        lastCall = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        List<Float> floats = new ArrayList();
        floats.add((float) state.ordinal());
        try {
            unit.executeCustomCommand(34223, floats, OPTION_NONE, Integer.MAX_VALUE);
            unit.executeCustomCommand(34223, floats, (state == RetreatState.RetreatOff) ? OPTION_RIGHT_MOUSE_KEY : OPTION_NONE, Integer.MAX_VALUE);
            //unit.setAutoRepairLevel(state.ordinal(), OPTION_NONE, Integer.MAX_VALUE);
        } catch (Exception ex) {
            handler.getCommand().debug("AIUnit exception: ", ex);
            handler.getCommand().unitDestroyed(unit, null);
        }
    }

    @Override
    public void attackGround(AIFloat3 trg, short options, int timeout) {
        checkDead();
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
    public void dropPayload(short options, int timeout) {
        checkDead();
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (handler.getCommand().getCurrentFrame() == lastCall) {
        }
        lastCall = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        List<Float> floats = new ArrayList();
        try {
            unit.executeCustomCommand(35000, floats, options, Integer.MAX_VALUE);
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
        checkDead();
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
        checkDead();
        List<Float> list = new ArrayList();
        list.add((float) targetUnitId);
        unit.executeCustomCommand(34923, list, OPTION_NONE, lastIdle);
    }

    @Override
    public float getMaxRange() {
        checkDead();
        return unit.getMaxRange();
    }

    @Override
    public float getMaxSlope() {
        checkDead();
        if (isBuilding()) {
            throw new AssertionError("Tried to get movedata for " + getDef().getHumanName());
        }
        if (getDef().isAbleToFly()) {
            return 1;
        }
        return unit.getDef().getMoveData().getMaxSlope();
    }

    public float getMakesEnergy() {
        checkDead();
        if (getDef() == null) {
            return 0f;
        }
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
