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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.UnitDestroyedListener;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.units.tasks.Task;
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
    private int timeCreated;
    
    private static List<AISquad> squads = new ArrayList();

    public AISquad(AIUnitHandler handler) {
        super(handler);
        handler.getCommand().addUnitDestroyedListener(this);
        squads.add(this);
        timeCreated = handler.getCommand().getCurrentFrame();
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
            squads.remove(this);
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
        //if (getRadius() > 40) handler.getCommand().mark(getPos(), "regrouping");
        return pos.length() + Math.max(0,getRadius()-50);
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
    public UnitDef getDef(){
        Map<UnitDef, Integer> cnt = new TreeMap();
        for (AIUnit u : units){
            if (!cnt.containsKey(u.getDef())) cnt.put(u.getDef(), 0);
            cnt.put(u.getDef(), cnt.get(u.getDef()) + 1);
        }
        
        int max = -1;
        UnitDef res = null;
        for (Map.Entry<UnitDef, Integer> e :  cnt.entrySet()){
            if (e.getValue() > max){
                max = e.getValue();
                res = e.getKey();
            }
        }
        if (res == null) throw new RuntimeException("Empty Squad -> no UnitDef");
        return res;
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

    @Override
    protected void doTask() {
        if (handler.getCommand().getCurrentFrame() - timeCreated > 150) {
            Map<Integer, List<AIUnit>> unittypes = new TreeMap();
            int squadcount = 0;
            List<Task> tasks = new ArrayList();
            List<AIUnit> units = new ArrayList();
            AIFloat3 pos = getPos();
            for (AISquad s : squads.toArray(new AISquad[squads.size()])) {
                if (handler.getCommand().getCurrentFrame() - s.timeCreated > 150 && s.distanceTo(pos) < 600 && handler.equals(s.handler)) {
                    squadcount++;
                    if (s.getTask() != null) {
                        tasks.add(s.getTask());
                    }
                    for (AIUnit u : s.getUnits().toArray(new AIUnit[s.getUnits().size()])) {
                        units.add(u);
                        s.removeUnit(u, null);
                        if (!unittypes.containsKey(u.getDef().getUnitDefId())) {
                            unittypes.put(u.getDef().getUnitDefId(), new ArrayList());
                        }
                        unittypes.get(u.getDef().getUnitDefId()).add(u);
                    }
                }
            }
            while (!unittypes.isEmpty()) {
                squadcount--;
                List<AIUnit> big = null;
                if (squadcount > 0) {
                    int bigi = 0;
                    for (Map.Entry<Integer, List<AIUnit>> e : unittypes.entrySet()) {
                        if (big == null || e.getValue().size() > big.size()) {
                            big = e.getValue();
                            bigi = e.getKey();
                        }
                    }
                    unittypes.remove(bigi);
                } else {
                    big = new ArrayList();
                    for (List<AIUnit> l : unittypes.values()) {
                        big.addAll(l);
                    }
                    unittypes.clear();
                }
                AISquad ns = new AISquad(handler);
                for (AIUnit au : big) {
                    ns.addUnit(au);
                }
                if (!tasks.isEmpty()) {
                    Task task = tasks.get(0).clone();
                    for (int i = 1; i < tasks.size(); i++) {
                        task.queue(tasks.get(i).clone());
                    }
                    ns.assignTask(task);
                }
            }
            for ( AIUnit au : units ) {
                if (au.handler == null) throw new RuntimeException("No AIUnitHandler");
            }
        }
        
        if (dead) return;
        
        super.doTask();
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

    @Override
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
    public void repair(Unit trg, short options, int timeout) {
        if (timeout < 0) {
            timeout = Integer.MAX_VALUE;
        }
        areaManager.executedCommand();
        for (AIUnit u : units) {
            u.repair(trg, options, Integer.MAX_VALUE);
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
    public void setTarget(int targetUnitId) {
        
        areaManager.executedCommand();
        for (AIUnit u : units) {
            u.setTarget(targetUnitId);
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
    public void unitDestroyed(AIUnit u, Enemy e) {
        removeUnit(u, null);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public float getEfficiencyAgainst(Enemy e) {
        return getEfficiencyAgainst(e.getDef());
    }

    @Override
    public float getEfficiencyAgainst(UnitDef ud) {
        float tot = 0.1f;
        float div = 0.1f;
        for (AIUnit u : units){
            tot += u.getEfficiencyAgainst(ud)*u.getDef().getCost(handler.getCommand().metal);
            div += u.getDef().getCost(handler.getCommand().metal);
        }
        return tot/div;
    }

}
