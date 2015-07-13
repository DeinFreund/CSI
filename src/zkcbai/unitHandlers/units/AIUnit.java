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
import java.util.List;
import zkcbai.UpdateListener;

/**
 *
 * @author User
 */
public class AIUnit extends AITroop implements UpdateListener {

    private Unit unit;
    private int unitId;
    private int wakeUpFrame = -1;
    private int lastCommandTime = -1;
    private boolean dead = false;

    public AIUnit(Unit u, AIUnitHandler handler) {
        super(handler);
        unit = u;
        unitId = unit.getUnitId();

    }

    public void destroyed() {
        //handler.getCommand().mark(getPos(), "dead");
        clearUpdateListener();
        dead = true;
    }

    @Override
    public List<AIUnit> getUnits() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        List<AIUnit> res = new ArrayList();
        res.add(this);
        return res;
    }

    public enum UnitType {

        raider, assault;
    }
    
    @Override
    public UnitDef getDef(){
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        return unit.getDef();
    }

    public UnitType getType() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
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

    public void checkIdle() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        if (handler.getCommand() == null) return;
        if ((wakeUpFrame < 0 || wakeUpFrame > 1000000) && handler.getCommand().getCurrentFrame() - lastCommandTime > 100
                && unit.getCurrentCommands().isEmpty()) {
            handler.getCommand().mark(getPos(), "reawaken");
            idle();
        }
        String tname = "";
        if (task != null) tname = task.getClass().getName();
        getCommand().debug("not idle doing " + tname + " because "  + (wakeUpFrame < 0 || wakeUpFrame > 1000000) + 
                "&&" + (handler.getCommand().getCurrentFrame() - lastCommandTime > 100) +
                "&&" + unit.getCurrentCommands().isEmpty());
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
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        if (handler.getCommand() == null) {
            return;
        }
        lastIdle = getCommand().getCurrentFrame();
        clearUpdateListener();
        doTask();
    }

    public void moveFailed() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        if (task != null) {
            task.moveFailed(this);
        } else {
            handler.getCommand().debug("Warning: move failed");
            //unit.wait(OPTION_NONE, Integer.MAX_VALUE);
        }
    }
    
    public float getEfficiencyAgainst(Enemy e){
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        return getEfficiencyAgainst(e.getDef());
    }
    
    public float getEfficiencyAgainst(UnitDef ud){
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        return handler.getCommand().killCounter.getEfficiency(unit.getDef(),ud);
    }

    @Override
    public AIFloat3 getPos() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        return new AIFloat3(unit.getPos());
    }

    public Unit getUnit() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
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
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        unit.moveTo(trg, options, Integer.MAX_VALUE);
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
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        //handler.getCommand().mark(trg, "move");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        unit.attack(trg, options, Integer.MAX_VALUE);
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
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        unit.repair(trg, options, Integer.MAX_VALUE);
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
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        unit.patrolTo(trg, options, Integer.MAX_VALUE);
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
            throw new RuntimeException("polled dead aiunit " + unitId);
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
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        //handler.getCommand().mark(trg, "fight");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        if (handler.getCommand().getCurrentFrame() == lastCall) {
            //throw new RuntimeException("Double Call to Fight");
            //getCommand().debug("Warning: Double Call to Fight");
        }
        lastCall = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        unit.fight(trg, options, Integer.MAX_VALUE);
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void build(UnitDef building, int facing, AIFloat3 trg, short options, int timeout) {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        //handler.getCommand().mark(trg, "build " + building.getHumanName());
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        lastCommandTime = handler.getCommand().getCurrentFrame();
        areaManager.executedCommand();
        unit.build(building, trg, facing, options, Integer.MAX_VALUE);

        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }
    
    @Override
    public void setTarget(int targetUnitId){
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        List<Float> list = new ArrayList();
        list.add((float)targetUnitId);
        unit.executeCustomCommand( 34923, list , OPTION_NONE, lastIdle);
    }

    @Override
    public float getMaxRange() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        return unit.getMaxRange();
    }

    @Override
    public float getMaxSlope() {
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        return unit.getDef().getMoveData().getMaxSlope();
    }
    
    public float getMakesEnergy(){
        if (dead) throw new RuntimeException("polled dead aiunit " + unitId);
        if (getDef().getName().contains("com")) return 6;
        return getDef().getCustomParams().containsKey("income_energy") ? Float.valueOf(getDef().getCustomParams().get("income_energy")) : 0f;
    }
    
    
    @Override
    public boolean equals(Object o){
        if (o instanceof AIUnit){
            return equals((AIUnit)o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return unitId;
    }
    
    public boolean equals(AIUnit u){
        return u != null && hashCode() == u.hashCode();
    }

}
