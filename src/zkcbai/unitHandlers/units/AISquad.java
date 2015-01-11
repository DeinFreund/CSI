/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zkcbai.UnitDestroyedListener;
import zkcbai.UpdateListener;
import zkcbai.utility.Point;
import zkcbai.utility.SmallestEnclosingCircle;

/**
 *
 * @author User
 */
public class AISquad extends AITroop implements AIUnitHandler, UpdateListener, UnitDestroyedListener {

    Set<AIUnit> units = new HashSet();
    private int wakeUpFrame = -1;
    private float minRange = Float.MAX_VALUE;
    private float minSlope = Float.MAX_VALUE;
    private Set<AIUnit> idlers = new HashSet();
    private boolean dead = false;

    public AISquad(AIUnitHandler handler) {
        super(handler);
    }

    public void addUnit(AIUnit u) {
        units.add(u);
        u.assignAIUnitHandler(this);
        minRange = Math.min(u.getMaxRange(), minRange);
        minSlope = Math.min(minSlope, u.getUnit().getDef().getMoveData().getMaxSlope());
    }

    public void removeUnit(AIUnit u, AIUnitHandler newHandler) {
        if (!units.contains(u)) {
            return;
        }
        u.assignAIUnitHandler(newHandler);
        units.remove(u);
        minRange = Float.MAX_VALUE;
        minSlope = Float.MAX_VALUE;
        for (AIUnit au : units) {
            minRange = Math.min(au.getMaxRange(), minRange);
            minSlope = Math.min(minSlope, au.getUnit().getDef().getMoveData().getMaxSlope());
        }
        if (units.isEmpty()) {
            dead = true;
            if (getCommand() != null) {
                getCommand().removeUnitDestroyedListener(this);
            }
        }
    }

    @Override
    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        pos.y = 0;
        return pos.length() + getRadius();
    }

    /**
     * Used to determine how far the squad is spread out
     *
     * @return
     */
    public float getRadius() {
        List<Point> positions = new ArrayList();
        for (AIUnit au : units) {
            positions.add(new Point(au.getPos().x, au.getPos().z));
        }
        return (float) SmallestEnclosingCircle.makeCircle(positions).r;
    }

    @Override
    public AIFloat3 getPos() {
        List<Point> positions = new ArrayList();
        for (AIUnit au : units) {
            positions.add(new Point(au.getPos().x, au.getPos().z));
        }
        Point p = SmallestEnclosingCircle.makeCircle(positions).c;
        AIFloat3 pos = new AIFloat3((float) p.x, 0, (float) p.y);
        pos.y = handler.getCommand().getCallback().getMap().getElevationAt(pos.x, pos.z);
        return pos;
    }

    @Override
    public void update(int frame) {
        if (dead) {
            return;
        }
        idle();
    }

    private void clearUpdateListener() {
        if (handler.getCommand() != null && wakeUpFrame > handler.getCommand().getCurrentFrame()) {
            handler.getCommand().removeSingleUpdateListener(this, wakeUpFrame);
            wakeUpFrame = -1;
        }
    }

    /**
     * Will only be called if all units of squad are idle
     */
    @Override
    public void idle() {
        idlers.clear();
        clearUpdateListener();
        doTask();
    }

    public Collection<AIUnit> getUnits() {
        return units;
    }

    @Override
    public void moveTo(AIFloat3 trg, short options, int timeout) {
        //handler.getCommand().mark(trg, "move");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        for (AIUnit u : units) {
            u.moveTo(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void attack(Unit trg, short options, int timeout) {
        //handler.getCommand().mark(trg, "move");
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : units) {
            u.attack(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void patrolTo(AIFloat3 trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : units) {
            u.patrolTo(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void fight(AIFloat3 trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : units) {
            u.fight(trg, options, Integer.MAX_VALUE);
        }
        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void build(UnitDef building, int facing, AIFloat3 trg, short options, int timeout
    ) {
        //handler.getCommand().mark(trg, "build " + building.getHumanName());
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : units) {
            u.build(building, facing, trg, options, Integer.MAX_VALUE);
        }

        if (timeout < wakeUpFrame || wakeUpFrame <= getCommand().getCurrentFrame()) {
            clearUpdateListener();
            if (handler.getCommand().addSingleUpdateListener(this, timeout)) {
                wakeUpFrame = timeout;
            }
        }
    }

    @Override
    public void troopIdle(AITroop u) {
        idlers.add((AIUnit) u);
        if (idlers.size() == units.size()) {
            getCommand().debug("all idle");
            idle();
        }
    }

    @Override
    public float getMaxRange() {
        return minRange;
    }

    @Override
    public float getMaxSlope() {
        return minSlope;
    }

    @Override
    public void unitDestroyed(AIUnit u) {
        removeUnit(u, null);
    }

    @Override
    public void unitDestroyed(Enemy e) {
    }

}
